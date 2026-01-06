package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import mrp.repos.FavoritesRepository;
import mrp.services.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für FavoritesController
 * 
 * Verwendet Mockito zum Mocken der Dependencies (HttpServer, FavoritesRepository, TokenService)
 */
@ExtendWith(MockitoExtension.class)
class FavoritesControllerTest {

    @Mock private HttpServer mockServer;
    @Mock private FavoritesRepository mockFavoritesRepo;
    @Mock private TokenService mockTokenService;

    @Test
    @DisplayName("FavoritesController sollte korrekt initialisiert werden")
    void testFavoritesController_ShouldInitializeCorrectly() {
        // ACT
        FavoritesController controller = new FavoritesController(mockServer, mockFavoritesRepo, mockTokenService);

        // ASSERT
        assertNotNull(controller, "Controller sollte erstellt werden");
        // FavoritesController erstellt keinen eigenen Context - wird vom MediaController geroutet
    }
}
