package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import mrp.repos.MediaRepository;
import mrp.services.TokenService;
import mrp.models.MediaEntry;
import mrp.models.LeaderboardEntry;
import mrp.models.RecommendationEntry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controller für Media-Operationen
 * 
 * Routet Sub-Endpoints an RatingController, CommentController und FavoritesController
 * Trennung der Verantwortlichkeiten: MediaController routet, spezialisierte Controller verarbeiten
 * 
 * Verwendet Repository Pattern für Datenbankzugriffe (Dependency Inversion Principle)
 */
public class MediaController {
    private final MediaRepository repo;  // Interface, nicht konkrete Implementierung
    private final TokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();
    private RatingController ratingController;
    private CommentController commentController;
    private FavoritesController favoritesController;

    public MediaController(HttpServer server, MediaRepository repo, TokenService tokenService) {
        this.repo = repo;
        this.tokenService = tokenService;
        server.createContext("/api/media", this::handle);
        server.createContext("/api/media/", this::handleWithId);
        server.createContext("/api/leaderboard", this::handleLeaderboard);
        server.createContext("/api/recommendations", this::handleRecommendations);
    }

    // Dependency Injection: Controller werden nachträglich gesetzt (circular dependency vermeiden)
    public void setRatingController(RatingController ratingController) {
        this.ratingController = ratingController;
    }

    public void setCommentController(CommentController commentController) {
        this.commentController = commentController;
    }

    public void setFavoritesController(FavoritesController favoritesController) {
        this.favoritesController = favoritesController;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if ("POST".equals(exchange.getRequestMethod())) {
                handleCreate(exchange);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                handleList(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleWithId(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Path-Parsing: /api/media/{id}/... -> parts[3] ist id
            int id = Integer.parseInt(parts[3]);

            // Routing: Weiterleitung an spezialisierte Controller für Sub-Endpoints
            if (parts.length >= 5) {
                String subPath = parts[4];
                if ("average-rating".equals(subPath) && ratingController != null) {
                    ratingController.handleAverageRating(exchange);
                    return;
                } else if ("comments".equals(subPath) && commentController != null) {
                    commentController.handleMediaComments(exchange);
                    return;
                } else if ("rate".equals(subPath) && ratingController != null) {
                    ratingController.handleRateMedia(exchange);
                    return;
                } else if ("favorite".equals(subPath) && favoritesController != null) {
                    favoritesController.handleFavorite(exchange);
                    return;
                }
            }
            
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                handleGetById(exchange, id);
            } else if ("PUT".equals(method)) {
                handleUpdate(exchange, id);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange, id);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var node = mapper.readTree(json);
        
        String title = validateTitle(node, exchange);
        if (title == null) return;
        
        // Rating ist optional, Standardwert 0
        int rating = node.has("rating") && !node.get("rating").isNull() ? node.get("rating").asInt() : 0;

        var media = repo.create(title, rating, userId.get());
        sendJson(exchange, 201, media);
    }

    private void handleList(HttpExchange exchange) throws IOException, SQLException {
        var query = exchange.getRequestURI().getQuery();
        
        if (query == null || query.isBlank()) {
            sendJson(exchange, 200, repo.findAll());
            return;
        }
        
        var params = parseSearchParams(exchange, query);
        if (params == null) return;

        // Unterstützt Filter: title, rating, userId, genre, type, ageRestriction
        var list = repo.search(params.title, params.rating, params.userId, 
                              params.genre, params.type, params.ageRestriction);
        sendJson(exchange, 200, list);
    }

    private void handleGetById(HttpExchange exchange, int id) throws IOException, SQLException {
        var optional = repo.findById(id);
        if (optional.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        sendJson(exchange, 200, optional.get());
    }

    private void handleUpdate(HttpExchange exchange, int id) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        // Ownership-Check: Nur der Ersteller kann sein Media-Entry bearbeiten
        if (!repo.isOwner(id, userId.get())) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        var existing = repo.findById(id);
        if (existing.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var node = mapper.readTree(json);
        
        String title = validateTitle(node, exchange);
        if (title == null) return;
        
        // Validierung: releaseYear muss zwischen 1900 und aktuellem Jahr+1 sein
        // Erlaubt auch zukünftige Jahre (+1) für angekündigte Medien
        if (node.has("releaseYear") && !node.get("releaseYear").isNull()) {
            int releaseYear = node.get("releaseYear").asInt();
            int currentYear = java.time.Year.now().getValue();
            if (releaseYear < 1900 || releaseYear > currentYear + 1) {
                sendError(exchange, 400, String.format("releaseYear must be between 1900 and %d", currentYear + 1));
                return;
            }
        }
        
        int rating = node.has("rating") && !node.get("rating").isNull() 
            ? node.get("rating").asInt() 
            : existing.get().getRating();

        if (!repo.update(id, title, rating)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        var updatedMedia = repo.findById(id).orElseThrow();
        sendJson(exchange, 200, updatedMedia);
    }

    private void handleDelete(HttpExchange exchange, int id) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        // Ownership-Check: Nur der Ersteller kann sein Media-Entry löschen
        if (!repo.isOwner(id, userId.get())) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!repo.delete(id)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        exchange.sendResponseHeaders(204, -1);
    }

    private void handleLeaderboard(HttpExchange exchange) throws IOException {
        try {
            Optional<Integer> userId = requireAuth(exchange);
            if (userId.isEmpty()) return;

            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            int limit = parseLimitParam(exchange.getRequestURI().getQuery(), 10);
            List<LeaderboardEntry> leaderboard = repo.getLeaderboard(limit);
            sendJson(exchange, 200, leaderboard);
        } catch (SQLException e) {
            sendError(exchange, 500, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleRecommendations(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Optional<Integer> userId = requireAuth(exchange);
            if (userId.isEmpty()) return;

            var query = exchange.getRequestURI().getQuery();
            int limit = parseLimitParam(query, 10);
            String type = parseTypeParam(query, "content");

            List<RecommendationEntry> recommendations = repo.getRecommendations(userId.get(), limit, type);
            sendJson(exchange, 200, recommendations);
        } catch (SQLException e) {
            sendError(exchange, 500, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
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

    private String validateTitle(com.fasterxml.jackson.databind.JsonNode node, HttpExchange exchange) throws IOException {
        if (!node.has("title") || node.get("title").isNull()) {
            sendError(exchange, 400, "title is required");
            return null;
        }
        String title = node.get("title").asText();
        if (title.isBlank()) {
            sendError(exchange, 400, "title cannot be empty");
            return null;
        }
        return title;
    }

    private SearchParams parseSearchParams(HttpExchange exchange, String query) throws IOException {
        SearchParams params = new SearchParams();
        String[] pairs = query.split("&");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length != 2) continue;
            
            String key = keyValue[0].trim();
            String value = decodeUrl(keyValue[1].trim());
            
            switch (key) {
                case "title":
                    params.title = value;
                    break;
                case "rating":
                    try {
                        params.rating = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        sendError(exchange, 400, "Invalid rating: must be a number");
                        return null;
                    }
                    break;
                case "userId":
                    try {
                        params.userId = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        // Ignorieren
                    }
                    break;
                case "genre":
                    params.genre = value;
                    break;
                case "type":
                    params.type = value;
                    break;
                case "ageRestriction":
                    try {
                        params.ageRestriction = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        // Ignorieren
                    }
                    break;
                case "sortBy":
                    if (!"title".equals(value) && !"rating".equals(value) && !"score".equals(value) && 
                        !"id".equals(value) && !"userId".equals(value)) {
                        sendError(exchange, 400, "Invalid sortBy: must be one of: title, rating, score, id, userId");
                        return null;
                    }
                    break;
            }
        }
        return params;
    }

    private int parseLimitParam(String query, int defaultValue) {
        if (query == null || query.isBlank()) return defaultValue;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && "limit".equals(keyValue[0].trim())) {
                try {
                    int limit = Integer.parseInt(keyValue[1].trim());
                    // Limit zwischen 1 und 100 begrenzen (Performance-Schutz)
                    return Math.max(1, Math.min(100, limit));
                } catch (NumberFormatException e) {
                    // Ignorieren
                }
            }
        }
        return defaultValue;
    }

    private String parseTypeParam(String query, String defaultValue) {
        if (query == null || query.isBlank()) return defaultValue;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && "type".equals(keyValue[0].trim())) {
                String type = keyValue[1].trim();
                return ("genre".equals(type) || "content".equals(type)) ? type : defaultValue;
            }
        }
        return defaultValue;
    }

    private String decodeUrl(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
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

    private static class SearchParams {
        String title;
        Integer rating;
        Integer userId;
        String genre;
        String type;
        Integer ageRestriction;
    }
}
