package mrp.repos;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Interface für Rating-Operationen (Repository Pattern)
 * 
 * Abstrahiert Datenbankzugriffe und unterstützt das Dependency Inversion Principle (DIP)
 * 1 Rating pro User/Media (1-5 Sterne), idempotente Operationen
 */
public interface RatingRepository {

    // Idempotente Operation: Erstellt oder aktualisiert ein Rating (1-5 Sterne)
    int setRating(int userId, int mediaId, int rating) throws SQLException;

    // Idempotente Operation: Erstellt oder aktualisiert ein Rating mit Kommentar
    int setRating(int userId, int mediaId, int rating, String comment) throws SQLException;

    Optional<Integer> getRating(int userId, int mediaId) throws SQLException;

    // Berechnet den Durchschnitt aller Ratings für ein Media (SQL AVG)
    double getAverageRating(int mediaId) throws SQLException;

    int getRatingCount(int mediaId) throws SQLException;

    boolean deleteRating(int userId, int mediaId) throws SQLException;

    Optional<RatingRow> getRatingById(int ratingId) throws SQLException;

    boolean updateRatingById(int ratingId, int stars, String comment) throws SQLException;

    // Toggle-Logik: Like/Unlike eines Ratings (idempotent)
    boolean likeRating(int ratingId, int userId) throws SQLException;

    // Moderation: Bestätigt ein Rating (setzt confirmed = true)
    boolean confirmRating(int ratingId) throws SQLException;

    java.util.List<RatingRow> getRatingsByUserId(int userId) throws SQLException;

    // Rating-Daten Record (Java 14+ Feature)
    record RatingRow(int id, int userId, int mediaId, int rating, String comment, int likes, boolean confirmed) {}
}