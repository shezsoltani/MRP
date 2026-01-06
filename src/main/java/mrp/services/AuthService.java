package mrp.services;

import mrp.repos.UserRepository;

/**
 * Service für Authentifizierung
 */
public class AuthService {
    private final UserRepository users;
    private final TokenService tokens;

    public AuthService(UserRepository users, TokenService tokens) { 
        this.users = users; 
        this.tokens = tokens; 
    }

    // Registrierung: Validiert Input, prüft auf Duplikate und speichert Passwort als Hash
    public void register(String username, String password) throws Exception {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username required");
        if (password == null || password.length() < 4) throw new IllegalArgumentException("password too short");
        if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("username exists");
        users.create(username, mrp.services.HashService.hash(password));
    }

    // Login: Validiert Credentials (Passwort wird gehasht und mit gespeichertem Hash verglichen)
    // Gibt bei Erfolg ein Token zurück für nachfolgende authentifizierte Anfragen
    public String login(String username, String password) throws Exception {
        var row = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!mrp.services.HashService.hash(password).equals(row.passwordHash())) throw new IllegalArgumentException("invalid credentials");
        return tokens.issue(row.id(), row.username());
    }

    // Extrahiert User-ID aus Authorization-Header (Bearer Token)
    // Wirft SecurityException bei fehlendem oder ungültigem Token
    public int requireUserId(String authorizationHeader) throws Exception {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) throw new SecurityException("unauthorized");
        var token = authorizationHeader.substring(7);
        return tokens.verify(token).orElseThrow(() -> new SecurityException("forbidden"));
    }
}
