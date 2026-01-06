package mrp.repos;

import mrp.models.Comment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-Implementierung des CommentRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 */
public class JdbcCommentRepository implements CommentRepository {

    @Override
    public Comment create(int mediaId, int userId, String text) throws SQLException {
        // Neue Kommentare sind standardmäßig nicht approved (Moderation erforderlich)
        String sql = """
            INSERT INTO comments(media_id, user_id, text, approved, created_at, updated_at)
            VALUES (?, ?, ?, false, now(), now())
            RETURNING id, created_at, updated_at
            """;
        
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, mediaId);
            ps.setInt(2, userId);
            ps.setString(3, text);
            try (var rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt("id");
                String createdAt = rs.getTimestamp("created_at").toInstant().toString();
                String updatedAt = rs.getTimestamp("updated_at").toInstant().toString();
                return new Comment(id, mediaId, userId, text, false, createdAt, updatedAt);
            }
        }
    }

    @Override
    public List<Comment> findByMediaId(int mediaId) throws SQLException {
        // Filtert nur approved Kommentare (öffentliche Sichtbarkeit)
        String sql = """
            SELECT c.id, c.media_id, c.user_id, c.text, c.approved, 
                   c.created_at, c.updated_at
            FROM comments c
            WHERE c.media_id = ? AND c.approved = true
            ORDER BY c.created_at DESC
            """;
        return executeCommentQuery(sql, mediaId);
    }

    @Override
    public List<Comment> findAllByMediaId(int mediaId) throws SQLException {
        // Gibt alle Kommentare zurück (inkl. nicht-approved) - für Moderation
        String sql = """
            SELECT c.id, c.media_id, c.user_id, c.text, c.approved, 
                   c.created_at, c.updated_at
            FROM comments c
            WHERE c.media_id = ?
            ORDER BY c.created_at DESC
            """;
        return executeCommentQuery(sql, mediaId);
    }

    @Override
    public Optional<Comment> findById(int id) throws SQLException {
        String sql = "SELECT * FROM comments WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapComment(rs));
            }
        }
    }

    @Override
    public boolean approve(int id) throws SQLException {
        // Moderation: Kommentar freigeben (approved = true)
        return executeUpdate("UPDATE comments SET approved = true, updated_at = now() WHERE id = ?", id);
    }

    @Override
    public boolean update(int id, String text) throws SQLException {
        String sql = "UPDATE comments SET text = ?, updated_at = now() WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, text);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(int id) throws SQLException {
        return executeUpdate("DELETE FROM comments WHERE id = ?", id);
    }

    @Override
    public boolean isOwner(int id, int userId) throws SQLException {
        String sql = "SELECT user_id FROM comments WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("user_id") == userId;
            }
        }
    }

    private List<Comment> executeCommentQuery(String sql, int mediaId) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, mediaId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<Comment>();
                while (rs.next()) {
                    list.add(mapComment(rs));
                }
                return list;
            }
        }
    }

    // Mapping: Konvertiert ResultSet-Zeile zu Comment-Objekt
    private Comment mapComment(ResultSet rs) throws SQLException {
        return new Comment(
                rs.getInt("id"),
                rs.getInt("media_id"),
                rs.getInt("user_id"),
                rs.getString("text"),
                rs.getBoolean("approved"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString()
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
