package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.naming.AuthenticationException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CookbookController.
 * Tests controller's responsibility: HTTP request handling, delegation to service, and exception wrapping.
 * Business logic is tested in RecipeServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class CookbookControllerTest {

    private static final String USER_ID = "user-123";
    private static final String USER_EMAIL = "user@example.com";

    @Mock
    private StorageService storageService;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private CookbookController controller;

    @Test
    @DisplayName("Should delegate createRecipe to RecipeService with correct parameters")
    void shouldDelegateToRecipeService() throws IOException, AuthenticationException {
        // Given
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        String compression = "gzip";

        RecipeResponse expectedResponse = RecipeResponse.builder()
                .title("Recipe Title")
                .url("http://example.com")
                .isRecipe(true)
                .driveFileId("file-123")
                .driveFileUrl("https://drive.google.com/file/file-123")
                .build();

        when(recipeService.createRecipe(USER_ID, request.url(), request.html(), compression, request.title()))
                .thenReturn(expectedResponse);

        // When
        RecipeResponse response = controller.createRecipe(request, compression, USER_ID, USER_EMAIL);

        // Then
        assertThat(response).isEqualTo(expectedResponse);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), compression, request.title());
    }

    @Test
    @DisplayName("Should rethrow StorageNotConnectedException in IOException")
    void shouldBubbleUpStorageNotConnectedException() throws IOException {
        // Given
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");

        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new StorageNotConnectedException("No storage configured"));

        // When/Then
        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configured");
    }

    @Test
    @DisplayName("Should propagate IOException from RecipeService")
    void shouldPropagateIOException() throws IOException {
        // Given
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");

        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IOException("HTML extraction failed"));

        // When/Then
        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTML extraction failed");
    }

    @Test
    @DisplayName("Should handle null compression parameter")
    void shouldHandleNullCompression() throws IOException, AuthenticationException {
        // Given
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");

        RecipeResponse expectedResponse = RecipeResponse.builder()
                .title("Recipe Title")
                .url("http://example.com")
                .isRecipe(true)
                .build();

        when(recipeService.createRecipe(eq(USER_ID), eq(request.url()), eq(request.html()), eq(null), eq(request.title())))
                .thenReturn(expectedResponse);

        // When
        RecipeResponse response = controller.createRecipe(request, null, USER_ID, USER_EMAIL);

        // Then
        assertThat(response).isEqualTo(expectedResponse);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), null, request.title());
    }

    @Test
    @DisplayName("Health check endpoint should return OK")
    void healthCheckShouldReturnOK() {
        // When
        String response = controller.gcpHealth();

        // Then
        assertThat(response).isEqualTo("OK");
    }

    @Test
    @DisplayName("Hello endpoint should format greeting with name")
    void helloShouldFormatGreeting() {
        // When
        String response = controller.index("John");

        // Then
        assertThat(response).isEqualTo("Hello, Cookbook user John!");
    }
}
