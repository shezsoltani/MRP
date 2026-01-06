package mrp.controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mrp.repos.CommentRepository;
import mrp.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller für Kommentar-Operationen
 * 
 * Implementiert Moderation: Neue Kommentare sind standardmäßig nicht approved
 * und werden erst nach Freigabe (POST /api/comments/{id}/approve) öffentlich sichtbar
 * 
 * Verwendet Repository Pattern für Datenbankzugriffe (Dependency Inversion Principle)
 */
public class CommentController {
    private final CommentRepository commentRepo;  // Interface, nicht konkrete Implementierung
    private final TokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CommentController(HttpServer server, CommentRepository commentRepo, TokenService tokenService) {
        this.commentRepo = commentRepo;
        this.tokenService = tokenService;
        // Direktes Routing für /api/comments/{id} Endpoints
        server.createContext("/api/comments/", this::handleComment);
    }

    // Wird von MediaController aufgerufen für /api/media/{id}/comments
    // Trennung der Verantwortlichkeiten: MediaController routet, CommentController verarbeitet
    public void handleMediaComments(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!path.endsWith("/comments")) return;

            String[] parts = path.split("/");
            if (parts.length < 5) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Path-Parsing: /api/media/{mediaId}/comments -> parts[3] ist mediaId
            int mediaId = Integer.parseInt(parts[3]);
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                handleCreateComment(exchange, mediaId);
            } else if ("GET".equals(method)) {
                handleGetComments(exchange, mediaId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleComment(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            int commentId = Integer.parseInt(parts[3]);

            // Sub-Endpoint: /api/comments/{id}/approve für Moderation
            if (parts.length >= 5 && "approve".equals(parts[4])) {
                if ("POST".equals(exchange.getRequestMethod())) {
                    handleApproveComment(exchange, commentId);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
                return;
            }

            String method = exchange.getRequestMethod();
            if ("PUT".equals(method)) {
                handleUpdateComment(exchange, commentId);
            } else if ("DELETE".equals(method)) {
                handleDeleteComment(exchange, commentId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleCreateComment(HttpExchange exchange, int mediaId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        String text = parseTextFromBody(exchange);
        if (text == null) {
            sendError(exchange, 400, "Comment text cannot be empty");
            return;
        }

        // Erstellt Kommentar mit approved=false (Moderation erforderlich)
        var comment = commentRepo.create(mediaId, userId.get(), text);
        sendJson(exchange, 201, comment);
    }

    private void handleGetComments(HttpExchange exchange, int mediaId) throws IOException, SQLException {
        // Repository filtert automatisch nur approved Kommentare (approved=true)
        // Nicht-approved Kommentare sind nur für Moderatoren sichtbar
        var comments = commentRepo.findByMediaId(mediaId);
        sendJson(exchange, 200, comments);
    }

    private void handleUpdateComment(HttpExchange exchange, int commentId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        // Ownership-Check: Nur der Ersteller kann seinen Kommentar bearbeiten
        if (!commentRepo.isOwner(commentId, userId.get())) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        String text = parseTextFromBody(exchange);
        if (text == null) {
            sendError(exchange, 400, "Comment text cannot be empty");
            return;
        }

        if (!commentRepo.update(commentId, text)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        var comment = commentRepo.findById(commentId).orElseThrow();
        sendJson(exchange, 200, comment);
    }

    private void handleDeleteComment(HttpExchange exchange, int commentId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        // Ownership-Check: Nur der Ersteller kann seinen Kommentar löschen
        if (!commentRepo.isOwner(commentId, userId.get())) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!commentRepo.delete(commentId)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        exchange.sendResponseHeaders(204, -1);
    }

    private void handleApproveComment(HttpExchange exchange, int commentId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        // Moderation: Kommentar freigeben (approved = true)
        // Nach Freigabe wird der Kommentar öffentlich sichtbar (findByMediaId)
        if (!commentRepo.approve(commentId)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        var comment = commentRepo.findById(commentId).orElseThrow();
        sendJson(exchange, 200, comment);
    }

    // Authentifizierung: Prüft Authorization-Header und extrahiert userId aus Token
    private Optional<Integer> requireAuth(HttpExchange exchange) throws IOException {
        var token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token == null) {
            exchange.sendResponseHeaders(401, -1);
            return Optional.empty();
        }
        var userId = tokenService.verify(token);
        if (userId.isEmpty()) {
            exchange.sendResponseHeaders(401, -1);
            return Optional.empty();
        }
        return userId;
    }

    private String parseTextFromBody(HttpExchange exchange) throws IOException {
        var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var node = mapper.readTree(json);
        String text = node.get("text").asText();
        return (text == null || text.isBlank()) ? null : text;
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        var response = mapper.writeValueAsBytes(data);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (var os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        var error = ("{\"error\":\"" + message + "\"}").getBytes();
        exchange.sendResponseHeaders(statusCode, error.length);
        try (var os = exchange.getResponseBody()) {
            os.write(error);
        }
    }
}
