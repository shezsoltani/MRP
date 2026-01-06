package mrp.controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mrp.repos.RatingRepository;
import mrp.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller für Rating-Operationen
 * 
 * Wird von MediaController für /api/media/{id}/rate und /api/media/{id}/average-rating geroutet
 * 
 * Verwendet Repository Pattern für Datenbankzugriffe (Dependency Inversion Principle)
 */
public class RatingController {
    private final RatingRepository ratingRepo;  // Interface, nicht konkrete Implementierung
    private final TokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();

    public RatingController(HttpServer server, RatingRepository ratingRepo, TokenService tokenService) {
        this.ratingRepo = ratingRepo;
        this.tokenService = tokenService;
        server.createContext("/api/ratings/", this::handleRating);
    }

    // Wird von MediaController aufgerufen für /api/media/{id}/rate
    public void handleRateMedia(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Path-Parsing: /api/media/{mediaId}/rate -> parts[3] ist mediaId
            int mediaId = Integer.parseInt(parts[3]);
            if ("POST".equals(exchange.getRequestMethod())) {
                handleSetRating(exchange, mediaId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleRating(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            int ratingId = Integer.parseInt(parts[3]);

            // Sub-Endpoints: /api/ratings/{id}/like und /api/ratings/{id}/confirm
            if (parts.length >= 5) {
                String subPath = parts[4];
                if ("like".equals(subPath) && "POST".equals(exchange.getRequestMethod())) {
                    handleLikeRating(exchange, ratingId);
                    return;
                } else if ("confirm".equals(subPath) && "POST".equals(exchange.getRequestMethod())) {
                    handleConfirmRating(exchange, ratingId);
                    return;
                }
            }

            String method = exchange.getRequestMethod();
            if ("PUT".equals(method)) {
                handleUpdateRatingById(exchange, ratingId);
            } else if ("GET".equals(method)) {
                handleGetRatingById(exchange, ratingId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // Wird von MediaController aufgerufen für /api/media/{id}/average-rating
    public void handleAverageRating(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Path-Parsing: /api/media/{mediaId}/average-rating -> parts[3] ist mediaId
            int mediaId = Integer.parseInt(parts[3]);
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            double average = ratingRepo.getAverageRating(mediaId);
            int count = ratingRepo.getRatingCount(mediaId);
            
            var response = String.format("{\"mediaId\":%d,\"averageRating\":%.2f,\"ratingCount\":%d}", 
                mediaId, average, count).getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleSetRating(HttpExchange exchange, int mediaId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var node = mapper.readTree(json);
        // Unterstützt sowohl "stars" als auch "rating" als Feldname
        int stars = node.has("stars") ? node.get("stars").asInt() : 
                   node.has("rating") ? node.get("rating").asInt() : 0;
        String comment = node.has("comment") ? node.get("comment").asText() : null;

        if (!validateRating(stars, exchange)) return;

        // Repository verwendet ON CONFLICT UPDATE (idempotent: 1 Rating pro User/Media)
        int ratingId = ratingRepo.setRating(userId.get(), mediaId, stars, comment);
        var response = String.format("{\"id\":%d,\"mediaId\":%d,\"rating\":%d,\"userId\":%d}", 
            ratingId, mediaId, stars, userId.get()).getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void handleUpdateRatingById(HttpExchange exchange, int ratingId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        var ratingOpt = ratingRepo.getRatingById(ratingId);
        if (ratingOpt.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        // Ownership-Check: Nur der Ersteller kann sein Rating bearbeiten
        if (ratingOpt.get().userId() != userId.get()) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var node = mapper.readTree(json);
        int stars = node.has("stars") ? node.get("stars").asInt() : 
                   node.has("rating") ? node.get("rating").asInt() : 0;
        String comment = node.has("comment") ? node.get("comment").asText() : "";

        if (!validateRating(stars, exchange)) return;

        if (!ratingRepo.updateRatingById(ratingId, stars, comment)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        var updated = ratingRepo.getRatingById(ratingId).orElseThrow();
        sendJson(exchange, 200, updated);
    }

    private void handleGetRatingById(HttpExchange exchange, int ratingId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        var ratingOpt = ratingRepo.getRatingById(ratingId);
        if (ratingOpt.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        sendJson(exchange, 200, ratingOpt.get());
    }

    private void handleLikeRating(HttpExchange exchange, int ratingId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        if (ratingRepo.getRatingById(ratingId).isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        if (!ratingRepo.likeRating(ratingId, userId.get())) {
            exchange.sendResponseHeaders(500, -1);
            return;
        }

        exchange.sendResponseHeaders(204, -1);
    }

    private void handleConfirmRating(HttpExchange exchange, int ratingId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        var ratingOpt = ratingRepo.getRatingById(ratingId);
        if (ratingOpt.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        // Ownership-Check: Nur der Ersteller kann sein Rating bestätigen
        if (ratingOpt.get().userId() != userId.get()) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        // Bestätigt das Rating (confirmed = true)
        if (!ratingRepo.confirmRating(ratingId)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        exchange.sendResponseHeaders(204, -1);
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

    // Validierung: Rating muss zwischen 1 und 5 Sternen sein
    private boolean validateRating(int stars, HttpExchange exchange) throws IOException {
        if (stars < 1 || stars > 5) {
            sendError(exchange, 400, "Rating must be between 1 and 5");
            return false;
        }
        return true;
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
