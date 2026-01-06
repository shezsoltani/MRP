package mrp.repos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-Implementierung des RatingRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 */
public class JdbcRatingRepository implements RatingRepository {

    @Override
    public int setRating(int userId, int mediaId, int rating) throws SQLException {
        return setRating(userId, mediaId, rating, null);
    }

    // Idempotente Operation: Erstellt oder aktualisiert ein Rating
    // ON CONFLICT macht die Operation sicher bei mehrfachen Aufrufen
    // COALESCE behält bestehenden Kommentar, wenn neuer null ist
    // RETURNING id gibt die Rating-ID zurück (PostgreSQL-Feature)
    @Override
    public int setRating(int userId, int mediaId, int rating, String comment) throws SQLException {
        String sql = """
            INSERT INTO ratings(user_id, media_id, rating, comment, created_at, updated_at)
            VALUES (?, ?, ?, ?, now(), now())
            ON CONFLICT (user_id, media_id)
            DO UPDATE SET rating = ?, comment = COALESCE(EXCLUDED.comment, ratings.comment), updated_at = now()
            RETURNING id
            """;
        
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            ps.setInt(3, rating);
            ps.setString(4, comment);
            ps.setInt(5, rating);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Failed to get rating ID");
                return rs.getInt("id");
            }
        }
    }

    @Override
    public Optional<Integer> getRating(int userId, int mediaId) throws SQLException {
        String sql = "SELECT rating FROM ratings WHERE user_id = ? AND media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt("rating")) : Optional.empty();
            }
        }
    }

    @Override
    public double getAverageRating(int mediaId) throws SQLException {
        String sql = "SELECT COALESCE(AVG(rating), 0.0) as avg_rating FROM ratings WHERE media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, mediaId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("avg_rating") : 0.0;
            }
        }
    }

    @Override
    public int getRatingCount(int mediaId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM ratings WHERE media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, mediaId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }

    @Override
    public boolean deleteRating(int userId, int mediaId) throws SQLException {
        String sql = "DELETE FROM ratings WHERE user_id = ? AND media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<RatingRepository.RatingRow> getRatingById(int ratingId) throws SQLException {
        String sql = "SELECT id, user_id, media_id, rating, comment, likes, confirmed FROM ratings WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, ratingId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRatingRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean updateRatingById(int ratingId, int stars, String comment) throws SQLException {
        String sql = "UPDATE ratings SET rating = ?, comment = ?, updated_at = now() WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, stars);
            ps.setString(2, comment);
            ps.setInt(3, ratingId);
            return ps.executeUpdate() > 0;
        }
    }

    // Toggle-Logik: Wenn bereits geliked, wird Unlike ausgeführt (idempotent)
    // Aktualisiert sowohl rating_likes-Tabelle als auch likes-Counter in ratings
    @Override
    public boolean likeRating(int ratingId, int userId) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement("SELECT id FROM rating_likes WHERE rating_id = ? AND user_id = ?")) {
            ps.setInt(1, ratingId);
            ps.setInt(2, userId);
            try (var rs = ps.executeQuery()) {
                boolean alreadyLiked = rs.next();
                
                if (alreadyLiked) {
                    // Unlike: Entfernt Like und dekrementiert Counter
                    try (var ps2 = c.prepareStatement("DELETE FROM rating_likes WHERE rating_id = ? AND user_id = ?")) {
                        ps2.setInt(1, ratingId);
                        ps2.setInt(2, userId);
                        ps2.executeUpdate();
                    }
                    try (var ps3 = c.prepareStatement("UPDATE ratings SET likes = GREATEST(0, likes - 1) WHERE id = ?")) {
                        ps3.setInt(1, ratingId);
                        ps3.executeUpdate();
                    }
                } else {
                    // Like: Fügt Like hinzu und inkrementiert Counter
                    try (var ps2 = c.prepareStatement("INSERT INTO rating_likes(rating_id, user_id, created_at) VALUES (?, ?, now())")) {
                        ps2.setInt(1, ratingId);
                        ps2.setInt(2, userId);
                        ps2.executeUpdate();
                    }
                    try (var ps3 = c.prepareStatement("UPDATE ratings SET likes = likes + 1 WHERE id = ?")) {
                        ps3.setInt(1, ratingId);
                        ps3.executeUpdate();
                    }
                }
                return true;
            }
        }
    }

    // Moderation: Setzt confirmed = true, damit das Rating öffentlich sichtbar wird
    @Override
    public boolean confirmRating(int ratingId) throws SQLException {
        return executeUpdate("UPDATE ratings SET confirmed = true WHERE id = ?", ratingId);
    }

    @Override
    public List<RatingRepository.RatingRow> getRatingsByUserId(int userId) throws SQLException {
        String sql = "SELECT id, user_id, media_id, rating, comment, likes, confirmed FROM ratings WHERE user_id = ? ORDER BY created_at DESC";
        List<RatingRepository.RatingRow> ratings = new ArrayList<>();
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    ratings.add(mapRatingRow(rs));
                }
            }
        }
        return ratings;
    }

    // Helper-Methode: Mappt ResultSet-Zeile zu RatingRow-Objekt (DRY-Prinzip)
    private RatingRepository.RatingRow mapRatingRow(ResultSet rs) throws SQLException {
        return new RatingRepository.RatingRow(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getInt("media_id"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getInt("likes"),
                rs.getBoolean("confirmed")
        );
    }

    private boolean executeUpdate(String sql, int id) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
