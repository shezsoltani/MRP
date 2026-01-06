package mrp.models;

/**
 * Model für Kommentare zu Media-Entries
 * 
 * Implementiert Moderation: Kommentare sind standardmäßig nicht approved
 * und werden erst nach Freigabe öffentlich sichtbar
 */
public class Comment {
    private int id;
    private int mediaId;
    private int userId;
    private String text;
    private boolean approved;  // Moderation: false = nicht freigegeben, true = öffentlich sichtbar
    private String createdAt;
    private String updatedAt;

    public Comment(int id, int mediaId, int userId, String text, boolean approved, String createdAt, String updatedAt) {
        this.id = id;
        this.mediaId = mediaId;
        this.userId = userId;
        this.text = text;
        this.approved = approved;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public int getMediaId() { return mediaId; }
    public int getUserId() { return userId; }
    public String getText() { return text; }
    public boolean isApproved() { return approved; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}

