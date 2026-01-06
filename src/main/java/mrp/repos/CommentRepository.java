package mrp.repos;

import mrp.models.Comment;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Interface für Comment-Operationen (Repository Pattern)
 * 
 * Neue Kommentare sind standardmäßig nicht approved und werden erst nach Moderation sichtbar
 */
public interface CommentRepository {

    /**
     * Erstellt einen neuen Kommentar (approved = false)
     */
    Comment create(int mediaId, int userId, String text) throws SQLException;

    /**
     * Findet alle bestätigten Kommentare für ein Media
     * Nur approved = true Kommentare werden zurückgegeben (öffentliche Sichtbarkeit)
     */
    List<Comment> findByMediaId(int mediaId) throws SQLException;

    /**
     * Findet alle Kommentare für ein Media (inkl. nicht-bestätigte)
     * Für Moderation/Admin-Zwecke
     */
    List<Comment> findAllByMediaId(int mediaId) throws SQLException;

    /**
     * Findet einen Kommentar nach ID
     */
    Optional<Comment> findById(int id) throws SQLException;

    /**
     * Bestätigt einen Kommentar (approved = true)
     * Nach Bestätigung wird der Kommentar öffentlich sichtbar
     */
    boolean approve(int id) throws SQLException;

    /**
     * Aktualisiert den Text eines Kommentars
     */
    boolean update(int id, String text) throws SQLException;

    /**
     * Löscht einen Kommentar
     */
    boolean delete(int id) throws SQLException;

    /**
     * Prüft ob ein Kommentar einem User gehört (Ownership-Check für Sicherheit)
     */
    boolean isOwner(int id, int userId) throws SQLException;
}

