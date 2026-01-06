package mrp.repos;

import mrp.models.MediaEntry;
import mrp.models.LeaderboardEntry;
import mrp.models.RecommendationEntry;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository Interface für Media-Operationen
 */
public interface MediaRepository {
    MediaEntry create(String title, int rating, int userId) throws SQLException;

    List<MediaEntry> findAll() throws SQLException;

    Optional<MediaEntry> findById(int id) throws SQLException;

    boolean update(int id, String title, int rating) throws SQLException;

    boolean delete(int id) throws SQLException;

    // Prüft, ob ein MediaEntry einem bestimmten User gehört (wichtig für Sicherheitsprüfungen)
    boolean isOwner(int id, int userId) throws SQLException;

    // Dynamische Suche mit optionalen Filtern (title, rating, userId, genre, type, ageRestriction)
    // Alle Parameter sind optional - null-Werte werden ignoriert
    List<MediaEntry> search(String title, Integer rating, Integer userId, String genre, String type, Integer ageRestriction) throws SQLException;

    // Leaderboard: Top-rated Media, sortiert nach durchschnittlichem Rating
    List<LeaderboardEntry> getLeaderboard(int limit) throws SQLException;

    // Empfehlungen: Zeigt Media, die der User noch nicht bewertet hat, sortiert nach Average Rating
    // type: "genre" (basierend auf favorite_genre) oder "content" (basierend auf Content-Ähnlichkeit)
    List<RecommendationEntry> getRecommendations(int userId, int limit, String type) throws SQLException;
}