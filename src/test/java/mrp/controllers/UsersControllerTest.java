package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import mrp.repos.*;
import mrp.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für UsersController
 * 
 * Verwendet Mockito zum Mocken der Dependencies (HttpServer, AuthService, Repositories)
 */
@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    @Mock private HttpServer mockServer;
    @Mock private AuthService mockAuthService;
    @Mock private UserRepository mockUserRepo;
    @Mock private RatingRepository mockRatingRepo;
    @Mock private FavoritesRepository mockFavoritesRepo;
    @Mock private MediaRepository mockMediaRepo;

    @Test
    @DisplayName("UsersController sollte korrekt initialisiert werden")
    void testUsersController_ShouldInitializeCorrectly() {
        // ACT
        UsersController controller = new UsersController(mockServer, mockAuthService, mockUserRepo,
                                                         mockRatingRepo, mockFavoritesRepo, mockMediaRepo);

        // ASSERT
        assertNotNull(controller, "Controller sollte erstellt werden");
        verify(mockServer, times(4)).createContext(anyString(), any());
    }
}
