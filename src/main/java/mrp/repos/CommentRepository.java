package mrp.repos;

import mrp.models.Comment;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Interface für Comment-Operationen
 * 
 * Neue Kommentare sind standardmäßig nicht approved
 */
public interface CommentRepository {

    Comment create(int mediaId, int userId, String text) throws SQLException;

    // Findet nur approved Kommentare
    List<Comment> findByMediaId(int mediaId) throws SQLException;

    // Findet alle Kommentare (inkl. nicht-approved)
    List<Comment> findAllByMediaId(int mediaId) throws SQLException;

    Optional<Comment> findById(int id) throws SQLException;

    boolean approve(int id) throws SQLException;

    boolean update(int id, String text) throws SQLException;

    boolean delete(int id) throws SQLException;

    boolean isOwner(int id, int userId) throws SQLException;
}

