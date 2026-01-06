package mrp.controllers;

import com.sun.net.httpserver.HttpServer;
import mrp.repos.CommentRepository;
import mrp.services.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für CommentController
 * 
 * Verwendet Mockito zum Mocken der Dependencies (HttpServer, CommentRepository, TokenService)
 */
@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock private HttpServer mockServer;
    @Mock private CommentRepository mockCommentRepo;
    @Mock private TokenService mockTokenService;

    @Test
    @DisplayName("CommentController sollte korrekt initialisiert werden")
    void testCommentController_ShouldInitializeCorrectly() {
        // ACT
        CommentController controller = new CommentController(mockServer, mockCommentRepo, mockTokenService);

        // ASSERT
        assertNotNull(controller, "Controller sollte erstellt werden");
        verify(mockServer, times(1)).createContext(anyString(), any());
    }
}
