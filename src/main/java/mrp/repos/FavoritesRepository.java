package mrp.repos;

import java.sql.SQLException;
import java.util.List;

/**
 * Interface für Favorites-Operationen
 */
public interface FavoritesRepository {

    /**
     * Markiert ein Media als Favorite
     * Idempotent: Mehrfaches Markieren hat denselben Effekt
     */
    boolean markFavorite(int userId, int mediaId) throws SQLException;

    /**
     * Entfernt ein Media aus den Favorites
     */
    boolean unmarkFavorite(int userId, int mediaId) throws SQLException;

    /**
     * Findet alle Favorites eines Users
     * Gibt Liste der Media-IDs zurück
     */
    List<Integer> getFavorites(int userId) throws SQLException;

    /**
     * Prüft ob ein Media als Favorite markiert ist
     */
    boolean isFavorite(int userId, int mediaId) throws SQLException;
}
