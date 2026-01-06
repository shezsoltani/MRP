package mrp.repos;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC-Implementierung des FavoritesRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 */
public class JdbcFavoritesRepository implements FavoritesRepository {

    @Override
    public boolean markFavorite(int userId, int mediaId) throws SQLException {
        // ON CONFLICT DO NOTHING macht die Operation idempotent (mehrfaches Markieren = kein Fehler)
        String sql = """
            INSERT INTO favorites(user_id, media_id, created_at)
            VALUES (?, ?, now())
            ON CONFLICT (user_id, media_id) DO NOTHING
            """;
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public boolean unmarkFavorite(int userId, int mediaId) throws SQLException {
        String sql = "DELETE FROM favorites WHERE user_id = ? AND media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public List<Integer> getFavorites(int userId) throws SQLException {
        String sql = "SELECT media_id FROM favorites WHERE user_id = ? ORDER BY created_at DESC";
        List<Integer> favorites = new ArrayList<>();
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    favorites.add(rs.getInt("media_id"));
                }
            }
        }
        return favorites;
    }

    @Override
    public boolean isFavorite(int userId, int mediaId) throws SQLException {
        String sql = "SELECT id FROM favorites WHERE user_id = ? AND media_id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, mediaId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
