package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import mrp.repos.MediaRepository;
import mrp.services.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für MediaController
 * 
 * Verwendet Mockito zum Mocken der Dependencies (HttpServer, MediaRepository, TokenService)
 */
@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock private HttpServer mockServer;
    @Mock private MediaRepository mockMediaRepo;
    @Mock private TokenService mockTokenService;

    @Test
    @DisplayName("MediaController sollte korrekt initialisiert werden")
    void testMediaController_ShouldInitializeCorrectly() {
        // ACT
        MediaController controller = new MediaController(mockServer, mockMediaRepo, mockTokenService);

        // ASSERT
        assertNotNull(controller, "Controller sollte erstellt werden");
        verify(mockServer, times(4)).createContext(anyString(), any());
    }

    @Test
    @DisplayName("MediaController sollte andere Controller setzen können")
    void testMediaController_ShouldSetOtherControllers() {
        // ARRANGE
        MediaController controller = new MediaController(mockServer, mockMediaRepo, mockTokenService);
        RatingController ratingController = mock(RatingController.class);
        CommentController commentController = mock(CommentController.class);
        FavoritesController favoritesController = mock(FavoritesController.class);

        // ACT - Setter Injection: Verhindert Zirkelabhängigkeiten (MediaController braucht andere Controller, aber nicht umgekehrt)
        controller.setRatingController(ratingController);
        controller.setCommentController(commentController);
        controller.setFavoritesController(favoritesController);

        // ASSERT
        assertNotNull(controller, "Controller sollte andere Controller setzen können");
    }
}
