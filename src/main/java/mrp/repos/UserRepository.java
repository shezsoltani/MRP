package mrp.repos;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Repository Interface für User-Operationen
 * 
 * Abstrahiert Datenbankzugriffe
 * Passwörter werden als Hash gespeichert, nie als Klartext
 */
public interface UserRepository {
    // Erstellt einen neuen User und gibt die generierte User-ID zurück
    int create(String username, String passwordHash) throws SQLException;

    // Findet Benutzer anhand des Benutzernamens (für Login verwendet)
    Optional<UserRow> findByUsername(String username) throws SQLException;

    // Findet Benutzer anhand der User-ID (für Profilabfragen verwendet)
    Optional<UserRow> findById(int id) throws SQLException;

    // Aktualisiert E-Mail und Lieblingsgenre eines Benutzers
    boolean updateProfile(int userId, String email, String favoriteGenre) throws SQLException;

    // User-Daten Record (Java 14+ Feature)
    record UserRow(int id, String username, String passwordHash, String email, String favoriteGenre) {}
}