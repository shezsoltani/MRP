package mrp.repos;

import java.sql.*;
import java.util.Optional;

/**
 * JDBC-Implementierung des TokenRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 */
public class JdbcTokenRepository implements TokenRepository {
    // Erstellt ein neues Token mit 7 Tagen Ablaufzeit (PostgreSQL interval-Feature)
    public void create(String token, int userId) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "INSERT INTO tokens(token,user_id,issued_at,expires_at) VALUES (?, ?, now(), now() + interval '7 days')"
             )) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // Validiert Token und gibt User-ID zurück, wenn Token gültig und nicht abgelaufen ist
    public Optional<Integer> findUserIdByToken(String token) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "SELECT user_id FROM tokens WHERE token=? AND (expires_at IS NULL OR expires_at > now())"
             )) {
            ps.setString(1, token);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }
}
