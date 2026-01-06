package mrp;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import mrp.controllers.UsersController;
import mrp.repos.Db;
import mrp.repos.UserRepository;
import mrp.repos.JdbcUserRepository;
import mrp.repos.TokenRepository;
import mrp.repos.JdbcTokenRepository;
import mrp.repos.MediaRepository;
import mrp.repos.JdbcMediaRepository;
import mrp.repos.RatingRepository;
import mrp.repos.JdbcRatingRepository;
import mrp.repos.CommentRepository;
import mrp.repos.JdbcCommentRepository;
import mrp.repos.FavoritesRepository;
import mrp.repos.JdbcFavoritesRepository;
import mrp.services.AuthService;
import mrp.services.TokenService;
import mrp.controllers.MediaController;
import mrp.controllers.RatingController;
import mrp.controllers.CommentController;
import mrp.controllers.FavoritesController;

/**
 * Hauptklasse der Anwendung
 * 
 * Setzt den HTTP-Server auf und konfiguriert alle Controller, Repositories und Services
 */
public class App {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Health-Check Endpoint
        server.createContext("/health", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // Datenbank-Health-Check Endpoint
        server.createContext("/db/ping", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try (var conn = Db.get();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT now() AS ts")) {

                rs.next();
                String ts = rs.getTimestamp("ts").toInstant().toString();
                byte[] body = ("{\"status\":\"ok\",\"db_time\":\"" + ts + "\"}").getBytes();
                exchange.sendResponseHeaders(200, body.length);
                try (var os = exchange.getResponseBody()) { os.write(body); }

            } catch (Exception e) {
                String msg = (e.getMessage() == null ? "error" : e.getMessage()).replace("\"","\\\"");
                byte[] body = ("{\"status\":\"error\",\"message\":\"" + msg + "\"}").getBytes();
                exchange.sendResponseHeaders(500, body.length);
                try (var os = exchange.getResponseBody()) { os.write(body); }
            }
        });

        // Repositories initialisieren
        UserRepository userRepo = new JdbcUserRepository();
        TokenRepository tokenRepo = new JdbcTokenRepository();
        MediaRepository mediaRepo = new JdbcMediaRepository();
        RatingRepository ratingRepo = new JdbcRatingRepository();
        CommentRepository commentRepo = new JdbcCommentRepository();
        FavoritesRepository favoritesRepo = new JdbcFavoritesRepository();
        
        // Services initialisieren
        var tokenService = new TokenService(tokenRepo);
        var authService = new AuthService(userRepo, tokenService);
        
        // Controller initialisieren
        var ratingController = new RatingController(server, ratingRepo, tokenService);
        var commentController = new CommentController(server, commentRepo, tokenService);
        var favoritesController = new FavoritesController(server, favoritesRepo, tokenService);
        var mediaController = new MediaController(server, mediaRepo, tokenService);
        new UsersController(server, authService, userRepo, ratingRepo, favoritesRepo, mediaRepo);
        
        // MediaController braucht andere Controller für Sub-Endpoints
        mediaController.setRatingController(ratingController);
        mediaController.setCommentController(commentController);
        mediaController.setFavoritesController(favoritesController);

        server.start();
        System.out.println("✅ Server läuft auf http://localhost:" + port);
    }
}
