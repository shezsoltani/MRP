package mrp.repos;

import java.sql.*;
import java.util.Optional;

/**
 * JDBC-Implementierung des UserRepository Interfaces
 * 
 * Verwendet PreparedStatements für SQL-Injection-Schutz
 * Passwörter werden als Hash gespeichert, nie als Klartext
 */
public class JdbcUserRepository implements UserRepository {
    
    // RETURNING id gibt die generierte User-ID direkt zurück (PostgreSQL-Feature)
    public int create(String username, String passwordHash) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "INSERT INTO users(username,password_hash) VALUES(?,?) RETURNING id")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Findet Benutzer anhand des Benutzernamens (für Login verwendet)
    public Optional<UserRepository.UserRow> findByUsername(String username) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "SELECT id, username, password_hash, email, favorite_genre FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUserRow(rs)) : Optional.empty();
            }
        }
    }

    // Findet Benutzer anhand der User-ID (für Profilabfragen verwendet)
    public Optional<UserRepository.UserRow> findById(int id) throws SQLException {
        try (var c = Db.get();
             var ps = c.prepareStatement(
                     "SELECT id, username, password_hash, email, favorite_genre FROM users WHERE id=?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUserRow(rs)) : Optional.empty();
            }
        }
    }

    // Aktualisiert E-Mail und Lieblingsgenre eines Benutzers
    @Override
    public boolean updateProfile(int userId, String email, String favoriteGenre) throws SQLException {
        String sql = "UPDATE users SET email = ?, favorite_genre = ? WHERE id = ?";
        try (var c = Db.get();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, favoriteGenre);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // Helper-Methode: Mappt ResultSet-Zeile zu UserRow-Objekt
    private UserRepository.UserRow mapUserRow(ResultSet rs) throws SQLException {
        return new UserRepository.UserRow(
                rs.getInt(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5)
        );
    }
}
