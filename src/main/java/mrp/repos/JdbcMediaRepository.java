package mrp.repos;

import mrp.models.MediaEntry;
import mrp.models.LeaderboardEntry;
import mrp.models.RecommendationEntry;
import java.sql.*;
import java.util.*;

/**
 * JDBC-Implementierung des MediaRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 */
public class JdbcMediaRepository implements MediaRepository {

    public MediaEntry create(String title, int rating, int userId) throws SQLException {
        // RETURNING id gibt die generierte ID direkt zurück (PostgreSQL-Feature)
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "INSERT INTO media_entries(title, rating, user_id) VALUES(?, ?, ?) RETURNING id")) {
            ps.setString(1, title);
            ps.setInt(2, rating);
            ps.setInt(3, userId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return new MediaEntry(rs.getInt("id"), title, rating, userId);
            }
        }
    }

    public List<MediaEntry> findAll() throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement("SELECT * FROM media_entries");
             var rs = ps.executeQuery()) {
            var list = new ArrayList<MediaEntry>();
            while (rs.next()) {
                list.add(mapMediaEntry(rs));
            }
            return list;
        }
    }

    public Optional<MediaEntry> findById(int id) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement("SELECT * FROM media_entries WHERE id = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapMediaEntry(rs));
            }
        }
    }

    public boolean update(int id, String title, int rating) throws SQLException {
        String sql = "UPDATE media_entries SET title = ?, rating = ? WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, rating);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM media_entries WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // Prüft, ob ein Benutzer der Eigentümer eines Media-Eintrags ist (wichtig für Sicherheitsprüfungen)
    public boolean isOwner(int id, int userId) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement("SELECT user_id FROM media_entries WHERE id = ?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("user_id") == userId;
            }
        }
    }

    // Dynamische Suche mit optionalen Filtern. Baut SQL-Query basierend auf vorhandenen Parametern
    // LEFT JOIN mit media-Tabelle nur wenn genre, type oder ageRestriction gefiltert werden
    public List<MediaEntry> search(String title, Integer rating, Integer userId, String genre, String type, Integer ageRestriction) throws SQLException {
        boolean needsJoin = (genre != null && !genre.isBlank()) || 
                           (type != null && !type.isBlank()) || 
                           (ageRestriction != null);
        
        var conditions = new ArrayList<String>();
        var params = new ArrayList<Object>();
        
        if (title != null && !title.isBlank()) {
            conditions.add((needsJoin ? "me." : "") + "title ILIKE ?");
            params.add("%" + title + "%");
        }
        
        if (rating != null) {
            conditions.add((needsJoin ? "me." : "") + "rating = ?");
            params.add(rating);
        }
        
        if (userId != null) {
            conditions.add((needsJoin ? "me." : "") + "user_id = ?");
            params.add(userId);
        }
        
        if (needsJoin && genre != null && !genre.isBlank()) {
            conditions.add("? = ANY(m.genres)");
            params.add(genre);
        }
        
        if (needsJoin && type != null && !type.isBlank()) {
            conditions.add("m.media_type = ?");
            params.add(type.toLowerCase());
        }
        
        if (needsJoin && ageRestriction != null) {
            conditions.add("m.age_restriction = ?");
            params.add(ageRestriction);
        }
        
        String sql = needsJoin
            ? """
                SELECT DISTINCT me.id, me.title, me.rating, me.user_id
                FROM media_entries me
                LEFT JOIN media m ON LOWER(me.title) = LOWER(m.title) 
                    AND m.creator_user_id = me.user_id
                """
            : "SELECT * FROM media_entries me";
        
        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY me.id";
        
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                var param = params.get(i);
                if (param instanceof String) {
                    ps.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    ps.setInt(i + 1, (Integer) param);
                }
            }
            
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<MediaEntry>();
                while (rs.next()) {
                    list.add(mapMediaEntry(rs));
                }
                return list;
            }
        }
    }

    // Leaderboard: Sortiert Media nach durchschnittlichem Rating und Anzahl der Bewertungen
    public List<LeaderboardEntry> getLeaderboard(int limit) throws SQLException {
        String sql = """
            SELECT 
                me.id,
                me.title,
                me.rating,
                me.user_id,
                COALESCE(AVG(r.rating), 0.0) as average_rating,
                COUNT(r.id) as rating_count
            FROM media_entries me
            LEFT JOIN ratings r ON me.id = r.media_id
            GROUP BY me.id, me.title, me.rating, me.user_id
            ORDER BY average_rating DESC, rating_count DESC
            LIMIT ?
            """;
        
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<LeaderboardEntry>();
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getInt("rating"),
                            rs.getInt("user_id"),
                            rs.getDouble("average_rating"),
                            rs.getInt("rating_count")
                    ));
                }
                return list;
            }
        }
    }

    // Empfehlungen: Zeigt Media, die der Benutzer noch nicht bewertet hat
    // NOT EXISTS filtert bereits bewertete Media heraus
    public List<RecommendationEntry> getRecommendations(int userId, int limit, String type) throws SQLException {
        if (type == null || type.isBlank()) {
            type = "content";
        }
        type = type.toLowerCase();
        
        String sql = """
            SELECT 
                me.id,
                me.title,
                me.rating,
                me.user_id,
                COALESCE(AVG(r_all.rating), 0.0) as average_rating,
                COUNT(r_all.id) as rating_count
            FROM media_entries me
            LEFT JOIN ratings r_all ON me.id = r_all.media_id
            WHERE NOT EXISTS (
                SELECT 1 FROM ratings r_user 
                WHERE r_user.media_id = me.id AND r_user.user_id = ?
            )
            GROUP BY me.id, me.title, me.rating, me.user_id
            ORDER BY average_rating DESC, rating_count DESC
            LIMIT ?
            """;
        
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<RecommendationEntry>();
                while (rs.next()) {
                    list.add(new RecommendationEntry(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getInt("rating"),
                            rs.getInt("user_id"),
                            rs.getDouble("average_rating"),
                            rs.getInt("rating_count")
                    ));
                }
                return list;
            }
        }
    }

    // Helper-Methode: Mappt ResultSet-Zeile zu MediaEntry-Objekt (DRY-Prinzip)
    private MediaEntry mapMediaEntry(ResultSet rs) throws SQLException {
        return new MediaEntry(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getInt("rating"),
                rs.getInt("user_id")
        );
    }
}
