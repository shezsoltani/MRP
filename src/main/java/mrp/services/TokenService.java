package mrp.services;

import mrp.repos.TokenRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Service für Token-Verwaltung
 */
public class TokenService {
    private final TokenRepository repo;

    public TokenService(TokenRepository repo) {
        this.repo = repo;
    }

    // Erstellt ein neues Token (UUID-basiert) und speichert es in der Datenbank mit 7 Tagen Ablaufzeit
    public String issue(int userId, String username) throws Exception {
        String token = username + "-" + UUID.randomUUID() + "-mrpToken";
        repo.create(token, userId);
        return token;
    }

    // Validiert Token und gibt User-ID zurück. Bearer-Präfix wird optional entfernt
    // Wirft keine Exceptions - gibt Optional.empty() bei ungültigem oder abgelaufenem Token zurück
    public Optional<Integer> verify(String token) {

        String raw = token;
        if (raw != null && raw.startsWith("Bearer ")) raw = raw.substring(7);

        try {
            return repo.findUserIdByToken(raw);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
