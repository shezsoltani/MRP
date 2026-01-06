package mrp.repos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Datenbank-Verbindungsklasse
 * 
 * Verwendet JDBC für PostgreSQL-Verbindung (Docker Container auf localhost:5432)
 * Connection sollte mit try-with-resources verwendet werden für automatisches Schließen
 */
public class Db {
    private static final String URL = "jdbc:postgresql://localhost:5432/mrp";
    private static final String USER = "mrp";
    private static final String PASSWORD = "mrp";

    /**
     * Erstellt eine neue Datenbank-Verbindung
     * 
     * Verwendung: try (Connection conn = Db.get()) { ... }
     */
    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
