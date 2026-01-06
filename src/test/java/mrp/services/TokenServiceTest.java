package mrp.services;

import mrp.repos.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.sql.SQLException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für TokenService
 * 
 * Verwendet Mockito zum Mocken der Dependencies (TokenRepository)
 * Testt Token-Erstellung (UUID-basiert) und Token-Verifizierung
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository mockRepo;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(mockRepo);
    }

    @Test
    @DisplayName("Token erstellen sollte korrektes Format haben und Repo aufrufen")
    void testIssue_ShouldCreateTokenWithCorrectFormat() throws Exception {
        // ARRANGE
        int userId = 1;
        String username = "testuser";

        // ACT
        String token = tokenService.issue(userId, username);

        // ASSERT
        assertNotNull(token, "Token sollte nicht null sein");
        assertTrue(token.startsWith(username + "-"), "Token sollte mit Username beginnen");
        assertTrue(token.endsWith("-mrpToken"), "Token sollte mit '-mrpToken' enden");
        verify(mockRepo).create(anyString(), eq(userId));
    }

    @Test
    @DisplayName("Jedes Token sollte einzigartig sein")
    void testIssue_ShouldReturnDifferentTokensForSameUser() throws Exception {
        // ARRANGE
        int userId = 1;
        String username = "test";

        // ACT
        String token1 = tokenService.issue(userId, username);
        String token2 = tokenService.issue(userId, username);

        // ASSERT
        assertNotEquals(token1, token2, "Jedes Token sollte einzigartig sein");
        verify(mockRepo, times(2)).create(anyString(), eq(userId));
    }

    @Test
    @DisplayName("Token verifizieren sollte userId zurückgeben wenn Token gültig")
    void testVerify_ShouldReturnUserIdWhenTokenIsValid() throws SQLException {
        // ARRANGE
        String token = "testuser-abc123-mrpToken";
        int expectedUserId = 42;
        when(mockRepo.findUserIdByToken(token)).thenReturn(Optional.of(expectedUserId));

        // ACT
        Optional<Integer> result = tokenService.verify(token);

        // ASSERT
        assertTrue(result.isPresent(), "Token sollte gültig sein");
        assertEquals(expectedUserId, result.get(), "Korrekte userId sollte zurückgegeben werden");
        verify(mockRepo).findUserIdByToken(token);
    }

    @Test
    @DisplayName("Token verifizieren sollte empty zurückgeben wenn Token ungültig")
    void testVerify_ShouldReturnEmptyWhenTokenIsInvalid() throws SQLException {
        // ARRANGE
        String token = "invalid-token";
        when(mockRepo.findUserIdByToken(token)).thenReturn(Optional.empty());

        // ACT
        Optional<Integer> result = tokenService.verify(token);

        // ASSERT
        assertTrue(result.isEmpty(), "Ungültiges Token sollte empty zurückgeben");
        verify(mockRepo).findUserIdByToken(token);
    }

    @Test
    @DisplayName("Token verifizieren sollte Bearer-Präfix entfernen")
    void testVerify_ShouldRemoveBearerPrefix() throws SQLException {
        // ARRANGE
        String tokenWithBearer = "Bearer test-token-123";
        String tokenWithoutBearer = "test-token-123";
        int expectedUserId = 42;
        when(mockRepo.findUserIdByToken(tokenWithoutBearer)).thenReturn(Optional.of(expectedUserId));

        // ACT
        Optional<Integer> result = tokenService.verify(tokenWithBearer);

        // ASSERT
        assertTrue(result.isPresent(), "Token mit Bearer-Präfix sollte verarbeitet werden");
        assertEquals(expectedUserId, result.get(), "Korrekte userId sollte zurückgegeben werden");
        verify(mockRepo).findUserIdByToken(tokenWithoutBearer);
    }
}
