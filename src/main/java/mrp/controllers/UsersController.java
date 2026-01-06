package mrp.controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import mrp.repos.UserRepository;
import mrp.repos.RatingRepository;
import mrp.repos.FavoritesRepository;
import mrp.repos.MediaRepository;
import mrp.services.AuthService;

import java.io.OutputStream;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Controller für User-Operationen
 * 
 * Nutzt AuthService für Authentifizierung
 */
public class UsersController {
    private final AuthService auth;
    private final UserRepository users;  // Interface, nicht konkrete Implementierung
    private final RatingRepository ratingRepo;
    private final FavoritesRepository favoritesRepo;
    private final MediaRepository mediaRepo;
    private final ObjectMapper json = new ObjectMapper();

    public UsersController(HttpServer server, AuthService auth, UserRepository users, 
                          RatingRepository ratingRepo, FavoritesRepository favoritesRepo, 
                          MediaRepository mediaRepo) {
        this.auth = auth; 
        this.users = users;
        this.ratingRepo = ratingRepo;
        this.favoritesRepo = favoritesRepo;
        this.mediaRepo = mediaRepo;
        server.createContext("/api/users/register", this::register);
        server.createContext("/api/users/login", this::login);
        server.createContext("/api/users/profile", this::profile);
        server.createContext("/api/users/", this::handleUserPath);
    }

    // DTOs (Data Transfer Objects) – einfache Transport-Objekte für JSON-Serialisierung
    public record RegisterReq(String username, String password) {}
    public record LoginReq(String username, String password) {}
    public record ProfileRes(int userId, String username, String email, String favoriteGenre) {}
    public record ProfileUpdateReq(String email, String favoriteGenre) {}

    private void register(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                write(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            var req = json.readValue(ex.getRequestBody(), RegisterReq.class);
            
            // Prüfe ob User bereits existiert (409 Conflict vermeiden)
            try {
                if (users.findByUsername(req.username()).isPresent()) {
                    write(ex, 409, "{\"error\":\"username already exists\"}");
                    return;
                }
            } catch (SQLException e) {
                // SQL-Fehler bei Prüfung → weiter mit Erstellung
            }
            
            // Passwort wird gehasht bevor es gespeichert wird
            try {
                int userId = users.create(req.username(), mrp.services.HashService.hash(req.password()));
                var response = String.format("{\"id\":%d,\"username\":\"%s\",\"status\":\"created\"}", userId, req.username());
                write(ex, 201, response);
            } catch (SQLException e) {
                // SQL-Fehler (z.B. Duplikat) → 409 Conflict
                if (e.getMessage() != null && (e.getMessage().contains("duplicate") || e.getMessage().contains("unique"))) {
                    write(ex, 409, "{\"error\":\"username already exists\"}");
                } else {
                    System.err.println("Error in register (SQL): " + e.getMessage());
                    write(ex, 500, err(e));
                }
            }
        } catch (IllegalArgumentException e) {
            write(ex, 400, err(e));
        } catch (Exception e) {
            System.err.println("Error in register: " + e.getMessage());
            write(ex, 500, err(e));
        }
    }

    private void login(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                write(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            var req = json.readValue(ex.getRequestBody(), LoginReq.class);
            // AuthService prüft Credentials und gibt Token zurück
            var token = auth.login(req.username(), req.password());
            write(ex, 200, "{\"token\":\"" + token + "\"}");
        } catch (IllegalArgumentException e) {
            write(ex, 401, err(e));
        } catch (Exception e) {
            System.err.println("Error in login: " + e.getMessage());
            write(ex, 500, err(e));
        }
    }

    private void profile(HttpExchange ex) {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                write(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            // Authentifizierung: Extrahiert userId aus Authorization-Header
            var authz = ex.getRequestHeaders().getFirst("Authorization");
            int userId = auth.requireUserId(authz);
            var u = users.findById(userId).orElseThrow();
            sendJson(ex, 200, new ProfileRes(userId, u.username(), u.email(), u.favoriteGenre()));
        } catch (SecurityException se) {
            int code = se.getMessage().contains("unauth") ? 401 : 403;
            write(ex, code, err(se));
        } catch (Exception e) {
            System.err.println("Error in profile: " + e.getMessage());
            write(ex, 500, err(e));
        }
    }

    private void handleUserPath(HttpExchange ex) throws IOException {
        try {
            String[] parts = ex.getRequestURI().getPath().split("/");
            if (parts.length < 4) {
                write(ex, 400, "{\"error\":\"Invalid path\"}");
                return;
            }

            // Path-Parsing: /api/users/{userId}/... -> parts[3] ist userId
            int urlUserId = Integer.parseInt(parts[3]);

            // Prüfe ob URL-User-ID existiert (für 404 bei nicht existierendem User)
            if (users.findById(urlUserId).isEmpty()) {
                write(ex, 404, "{\"error\":\"User not found\"}");
                return;
            }

            // Authentifizierung: userId aus Token extrahieren (Sicherheit)
            var authz = ex.getRequestHeaders().getFirst("Authorization");
            int userId = auth.requireUserId(authz);

            String subPath = parts.length > 4 ? parts[4] : "";
            String method = ex.getRequestMethod();
            
            if ("profile".equals(subPath)) {
                if ("GET".equals(method)) {
                    handleGetProfile(ex, userId);
                } else if ("PUT".equals(method)) {
                    handleUpdateProfile(ex, userId);
                } else {
                    write(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else if ("ratings".equals(subPath)) {
                if ("GET".equals(method)) {
                    handleGetRatings(ex, userId);
                } else {
                    write(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else if ("favorites".equals(subPath)) {
                if ("GET".equals(method)) {
                    handleGetFavorites(ex, userId);
                } else {
                    write(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else if ("recommendations".equals(subPath)) {
                if ("GET".equals(method)) {
                    handleGetRecommendations(ex, userId);
                } else {
                    write(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else {
                write(ex, 404, "{\"error\":\"Not found\"}");
            }
        } catch (NumberFormatException e) {
            write(ex, 400, "{\"error\":\"Invalid user ID\"}");
        } catch (SecurityException se) {
            int code = se.getMessage().contains("unauth") ? 401 : 403;
            write(ex, code, err(se));
        } catch (Exception e) {
            System.err.println("Error in handleUserPath: " + e.getMessage());
            write(ex, 500, err(e));
        }
    }

    private void handleGetProfile(HttpExchange ex, int userId) throws IOException, SQLException {
        var u = users.findById(userId);
        if (u.isEmpty()) {
            write(ex, 404, "{\"error\":\"User not found\"}");
            return;
        }
        sendJson(ex, 200, new ProfileRes(userId, u.get().username(), u.get().email(), u.get().favoriteGenre()));
    }

    private void handleUpdateProfile(HttpExchange ex, int userId) throws IOException, SQLException {
        var req = json.readValue(ex.getRequestBody(), ProfileUpdateReq.class);
        
        // Email-Validierung: Prüft grundlegendes Format (@ vorhanden)
        if (req.email() != null && !req.email().isBlank() && !validateEmail(req.email())) {
            write(ex, 400, "{\"error\":\"invalid email format\"}");
            return;
        }
        
        if (!users.updateProfile(userId, req.email(), req.favoriteGenre())) {
            write(ex, 404, "{\"error\":\"User not found\"}");
            return;
        }
        
        var u = users.findById(userId).orElseThrow();
        sendJson(ex, 200, new ProfileRes(userId, u.username(), u.email(), u.favoriteGenre()));
    }

    private void handleGetRatings(HttpExchange ex, int userId) throws IOException, SQLException {
        if (!checkUserExists(userId, ex)) return;
        
        var ratings = ratingRepo.getRatingsByUserId(userId);
        sendJson(ex, 200, ratings);
    }

    private void handleGetFavorites(HttpExchange ex, int userId) throws IOException, SQLException {
        if (!checkUserExists(userId, ex)) return;
        
        // Hole Favorite-IDs und lade zugehörige Media-Entries
        var favoriteIds = favoritesRepo.getFavorites(userId);
        var favorites = favoriteIds.stream()
            .map(id -> {
                try {
                    return mediaRepo.findById(id);
                } catch (SQLException e) {
                    return java.util.Optional.<mrp.models.MediaEntry>empty();
                }
            })
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();
        sendJson(ex, 200, favorites);
    }

    private void handleGetRecommendations(HttpExchange ex, int userId) throws IOException, SQLException {
        if (!checkUserExists(userId, ex)) return;
        
        // Query-Parameter: type kann "genre" oder "content" sein
        String type = parseTypeParam(ex.getRequestURI().getQuery(), "content");
        if (type == null) {
            write(ex, 400, "{\"error\":\"Invalid type. Must be 'genre' or 'content'\"}");
            return;
        }
        
        var recommendations = mediaRepo.getRecommendations(userId, 10, type);
        sendJson(ex, 200, recommendations);
    }

    private boolean checkUserExists(int userId, HttpExchange ex) throws IOException, SQLException {
        if (users.findById(userId).isEmpty()) {
            write(ex, 404, "{\"error\":\"User not found\"}");
            return false;
        }
        return true;
    }

    // Einfache Email-Validierung: Prüft ob @ vorhanden und nicht am Anfang/Ende
    private boolean validateEmail(String email) {
        int atIndex = email.indexOf("@");
        return atIndex > 0 && atIndex < email.length() - 1;
    }

    private String parseTypeParam(String query, String defaultValue) {
        if (query == null || query.isBlank()) return defaultValue;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && "type".equals(keyValue[0].trim())) {
                String type = keyValue[1].trim();
                return ("genre".equals(type) || "content".equals(type)) ? type : null;
            }
        }
        return defaultValue;
    }

    private void sendJson(HttpExchange ex, int code, Object data) {
        try {
            byte[] body = json.writeValueAsBytes(data);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } catch (Exception e) {
            System.err.println("Error in sendJson: " + e.getMessage());
        }
    }

    private static String err(Exception e) {
        return "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
    }

    private static void write(HttpExchange ex, int code, String body) {
        try {
            var bytes = body == null ? new byte[0] : body.getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignore) {}
    }
}
