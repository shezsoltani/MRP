package mrp.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für HashService
 * 
 * Testt Passwort-Hashing (SHA-256) 
 */
class HashServiceTest {

    @Test
    @DisplayName("Hash sollte konsistent sein - gleicher Input ergibt gleichen Hash")
    void testHash_ShouldReturnSameHashForSameInput() {
        // ARRANGE
        String password = "test123";
        
        // ACT
        String hash1 = HashService.hash(password);
        String hash2 = HashService.hash(password);
        
        // ASSERT
        assertEquals(hash1, hash2, "Gleiches Passwort sollte gleichen Hash ergeben");
    }

    @Test
    @DisplayName("Hash sollte unterschiedlich sein - verschiedene Inputs ergeben verschiedene Hashes")
    void testHash_ShouldReturnDifferentHashForDifferentInput() {
        // ARRANGE
        String password1 = "test123";
        String password2 = "test456";
        
        // ACT
        String hash1 = HashService.hash(password1);
        String hash2 = HashService.hash(password2);
        
        // ASSERT
        assertNotEquals(hash1, hash2, "Verschiedene Passwörter sollten verschiedene Hashes ergeben");
    }

    @Test
    @DisplayName("Hash sollte nicht null sein und korrekte Länge haben")
    void testHash_ShouldReturnNonNullHashWithCorrectLength() {
        // ARRANGE
        String password = "test123";
        
        // ACT
        String hash = HashService.hash(password);
        
        // ASSERT
        assertNotNull(hash, "Hash sollte nicht null sein");
        assertEquals(64, hash.length(), "SHA-256 Hash sollte 64 Zeichen lang sein (Hex-String)");
    }
}
