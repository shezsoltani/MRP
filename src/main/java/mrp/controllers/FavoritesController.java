package mrp.controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mrp.repos.FavoritesRepository;
import mrp.services.TokenService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller für Favoriten-Operationen
 * 
 * Wird von MediaController für /api/media/{id}/favorite geroutet
 * Trennung der Verantwortlichkeiten: MediaController routet, FavoritesController verarbeitet
 * 
 * Verwendet Repository Pattern für Datenbankzugriffe (Dependency Inversion Principle)
 */
public class FavoritesController {
    private final FavoritesRepository favoritesRepo;  // Interface, nicht konkrete Implementierung
    private final TokenService tokenService;

    public FavoritesController(HttpServer server, FavoritesRepository favoritesRepo, TokenService tokenService) {
        this.favoritesRepo = favoritesRepo;
        this.tokenService = tokenService;
    }

    // Wird von MediaController aufgerufen
    // Trennung der Verantwortlichkeiten: MediaController routet, FavoritesController verarbeitet
    public void handleFavorite(HttpExchange exchange) throws IOException {
        try {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Path-Parsing: /api/media/{mediaId}/favorite -> parts[3] ist mediaId
            int mediaId = Integer.parseInt(parts[3]);
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handleMarkFavorite(exchange, mediaId);
            } else if ("DELETE".equals(method)) {
                handleUnmarkFavorite(exchange, mediaId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleMarkFavorite(HttpExchange exchange, int mediaId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        boolean success = favoritesRepo.markFavorite(userId.get(), mediaId);
        // 204 wenn neu markiert, 200 wenn bereits vorhanden
        exchange.sendResponseHeaders(success ? 204 : 200, -1);
    }

    private void handleUnmarkFavorite(HttpExchange exchange, int mediaId) throws IOException, SQLException {
        Optional<Integer> userId = requireAuth(exchange);
        if (userId.isEmpty()) return;

        if (!favoritesRepo.unmarkFavorite(userId.get(), mediaId)) {
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

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        var error = ("{\"error\":\"" + message + "\"}").getBytes();
        exchange.sendResponseHeaders(statusCode, error.length);
        try (var os = exchange.getResponseBody()) {
            os.write(error);
        }
    }
}
