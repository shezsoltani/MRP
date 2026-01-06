package mrp.models;

public class MediaEntry {
    private int id;
    private String title;
    private int rating;
    private int userId;

    public MediaEntry(int id, String title, int rating, int userId) {
        this.id = id;
        this.title = title;
        this.rating = rating;
        this.userId = userId;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getRating() { return rating; }
    public int getUserId() { return userId; }
}
