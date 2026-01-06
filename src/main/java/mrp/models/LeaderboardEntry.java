package mrp.models;

/**
 * Model für Leaderboard-Einträge
 * 
 * Enthält MediaEntry-Informationen + Average Rating für Leaderboard-Anzeige
 */
public class LeaderboardEntry {
    private int id;
    private String title;
    private int rating;  // Original Rating (von MediaEntry)
    private int userId;
    private double averageRating;  // Durchschnitt aller User-Ratings (1-5)
    private int ratingCount;  // Anzahl der Ratings

    public LeaderboardEntry(int id, String title, int rating, int userId, double averageRating, int ratingCount) {
        this.id = id;
        this.title = title;
        this.rating = rating;
        this.userId = userId;
        this.averageRating = averageRating;
        this.ratingCount = ratingCount;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getRating() { return rating; }
    public int getUserId() { return userId; }
    public double getAverageRating() { return averageRating; }
    public int getRatingCount() { return ratingCount; }
}

