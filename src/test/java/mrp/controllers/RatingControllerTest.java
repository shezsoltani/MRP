package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import mrp.repos.RatingRepository;
import mrp.services.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für RatingController
 * 
 * Verwendet Mockito zum Mocken der Dependencies (HttpServer, RatingRepository, TokenService)
 */
@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    @Mock private HttpServer mockServer;
    @Mock private RatingRepository mockRatingRepo;
    @Mock private TokenService mockTokenService;

    @Test
    @DisplayName("RatingController sollte korrekt initialisiert werden")
    void testRatingController_ShouldInitializeCorrectly() {
        // ACT
        RatingController controller = new RatingController(mockServer, mockRatingRepo, mockTokenService);

        // ASSERT
        assertNotNull(controller, "Controller sollte erstellt werden");
        verify(mockServer, times(1)).createContext(anyString(), any());
    }
}
