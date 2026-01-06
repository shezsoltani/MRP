package mrp.repos;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Repository Interface für Token-Operationen
 * 
 * Abstrahiert Datenbankzugriffe und unterstützt das Dependency Inversion Principle (DIP)
 * Token haben eine Ablaufzeit (7 Tage) und werden für die Authentifizierung verwendet
 */
public interface TokenRepository {
    // Erstellt einen neuen Token mit 7 Tagen Ablaufzeit
    void create(String token, int userId) throws SQLException;

    // Validiert Token und gibt User-ID zurück, wenn Token gültig und nicht abgelaufen
    Optional<Integer> findUserIdByToken(String token) throws SQLException;
}