package mrp.services;

import mrp.repos.UserRepository;
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
 * Unit-Tests für AuthService
 * 
 * Verwendet Mockito zum Mocken der Dependencies (UserRepository, TokenService)
 * Testt Business-Logik für Registrierung, Login und Token-Validierung
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository mockUserRepo;

    @Mock
    private TokenService mockTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(mockUserRepo, mockTokenService);
    }

    // Helper-Methode: Erstellt UserRow für Tests (email und favoriteGenre sind null, da nicht relevant)
    private UserRepository.UserRow createUserRow(int id, String username, String passwordHash) {
        return new UserRepository.UserRow(id, username, passwordHash, null, null);
    }

    @Test
    @DisplayName("Registrierung sollte User mit gehashtem Passwort erstellen")
    void testRegister_ShouldCreateUserWithHashedPassword() throws Exception {
        // ARRANGE
        String username = "newuser";
        String password = "password123";
        when(mockUserRepo.findByUsername(username)).thenReturn(Optional.empty());
        when(mockUserRepo.create(eq(username), anyString())).thenReturn(1);

        // ACT
        authService.register(username, password);

        // VERIFY
        verify(mockUserRepo).findByUsername(username);
        verify(mockUserRepo).create(eq(username), argThat(hash -> 
            hash != null && !hash.equals(password) && hash.length() == 64
        ));
    }

    @Test
    @DisplayName("Login sollte Token zurückgeben wenn Credentials gültig")
    void testLogin_ShouldReturnTokenWhenCredentialsAreValid() throws Exception {
        // ARRANGE
        String username = "testuser";
        String password = "password123";
        String passwordHash = HashService.hash(password);
        int userId = 42;
        String expectedToken = "testuser-token-123";
        
        when(mockUserRepo.findByUsername(username)).thenReturn(
            Optional.of(createUserRow(userId, username, passwordHash))
        );
        when(mockTokenService.issue(userId, username)).thenReturn(expectedToken);

        // ACT
        String token = authService.login(username, password);

        // ASSERT
        assertNotNull(token, "Token sollte nicht null sein");
        assertEquals(expectedToken, token, "Korrektes Token sollte zurückgegeben werden");
        verify(mockTokenService).issue(userId, username);
    }

    @Test
    @DisplayName("Registrierung sollte Exception werfen wenn Username existiert")
    void testRegister_ShouldThrowExceptionWhenUsernameExists() throws SQLException {
        // ARRANGE
        String username = "existinguser";
        String password = "password123";
        when(mockUserRepo.findByUsername(username)).thenReturn(
            Optional.of(createUserRow(1, username, "somehash"))
        );

        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.register(username, password),
            "Existierender Username sollte Exception werfen"
        );
        assertEquals("username exists", exception.getMessage());
        verify(mockUserRepo, never()).create(anyString(), anyString());
    }

    @Test
    @DisplayName("Login sollte Exception werfen wenn Credentials ungültig")
    void testLogin_ShouldThrowExceptionWhenCredentialsInvalid() throws SQLException, Exception {
        // ARRANGE
        String username = "nonexistent";
        String password = "password123";
        when(mockUserRepo.findByUsername(username)).thenReturn(Optional.empty());

        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.login(username, password),
            "Nicht existierender User sollte Exception werfen"
        );
        assertEquals("invalid credentials", exception.getMessage());
        verify(mockTokenService, never()).issue(anyInt(), anyString());
    }

    @Test
    @DisplayName("requireUserId sollte User-ID zurückgeben wenn Token gültig")
    void testRequireUserId_ShouldReturnUserIdWhenTokenValid() throws Exception {
        // ARRANGE
        String validToken = "Bearer test-token-123";
        int expectedUserId = 42;
        when(mockTokenService.verify("test-token-123")).thenReturn(Optional.of(expectedUserId));

        // ACT
        int userId = authService.requireUserId(validToken);

        // ASSERT
        assertEquals(expectedUserId, userId, "Korrekte User-ID sollte zurückgegeben werden");
        verify(mockTokenService).verify("test-token-123");
    }

    @Test
    @DisplayName("requireUserId sollte SecurityException werfen wenn Token ungültig")
    void testRequireUserId_ShouldThrowSecurityExceptionWhenTokenInvalid() throws Exception {
        // ARRANGE
        String invalidToken = "Bearer invalid-token";
        when(mockTokenService.verify("invalid-token")).thenReturn(Optional.empty());

        // ACT & ASSERT
        SecurityException exception = assertThrows(
            SecurityException.class,
            () -> authService.requireUserId(invalidToken),
            "Ungültiges Token sollte SecurityException werfen"
        );
        assertEquals("forbidden", exception.getMessage());
    }

    @Test
    @DisplayName("requireUserId sollte SecurityException werfen wenn kein Bearer-Präfix")
    void testRequireUserId_ShouldThrowSecurityExceptionWhenNoBearerPrefix() throws Exception {
        // ARRANGE
        String invalidToken = "invalid-token"; // Kein "Bearer " Präfix

        // ACT & ASSERT
        SecurityException exception = assertThrows(
            SecurityException.class,
            () -> authService.requireUserId(invalidToken),
            "Token ohne Bearer-Präfix sollte SecurityException werfen"
        );
        assertEquals("unauthorized", exception.getMessage());
    }

    @Test
    @DisplayName("requireUserId sollte SecurityException werfen wenn Authorization null")
    void testRequireUserId_ShouldThrowSecurityExceptionWhenNull() throws Exception {
        // ACT & ASSERT
        SecurityException exception = assertThrows(
            SecurityException.class,
            () -> authService.requireUserId(null),
            "Null Authorization sollte SecurityException werfen"
        );
        assertEquals("unauthorized", exception.getMessage());
    }

    @Test
    @DisplayName("Registrierung sollte Exception werfen wenn Passwort zu kurz")
    void testRegister_ShouldThrowExceptionWhenPasswordTooShort() throws SQLException {
        // ARRANGE
        String username = "testuser";
        String shortPassword = "123"; // < 4 Zeichen

        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.register(username, shortPassword),
            "Passwort zu kurz sollte Exception werfen"
        );
        assertEquals("password too short", exception.getMessage());
        verify(mockUserRepo, never()).create(anyString(), anyString());
    }

    @Test
    @DisplayName("Registrierung sollte Exception werfen wenn Username leer")
    void testRegister_ShouldThrowExceptionWhenUsernameBlank() throws SQLException {
        // ARRANGE
        String blankUsername = "   ";
        String password = "password123";

        // ACT & ASSERT
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authService.register(blankUsername, password),
            "Leerer Username sollte Exception werfen"
        );
        assertEquals("username required", exception.getMessage());
        verify(mockUserRepo, never()).create(anyString(), anyString());
    }
}
