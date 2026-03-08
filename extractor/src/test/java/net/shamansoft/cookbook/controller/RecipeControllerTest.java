package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.dto.Compression;
import net.shamansoft.cookbook.dto.CustomRecipeRequest;
import net.shamansoft.cookbook.dto.RecipeItemResult;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeControllerTest {

    private static final String USER_ID = "user-123";
    private static final String USER_EMAIL = "user@example.com";

    @Mock
    private StorageService storageService;
    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private RecipeController controller;

    // --- createRecipe (HTML extraction) ---

    @Test
    @DisplayName("Should delegate createRecipe to RecipeService with BASE64_GZIP compression")
    void shouldDelegateToRecipeServiceWithBase64Compression() throws IOException, AuthenticationException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        RecipeResponse expected = RecipeResponse.builder()
                .title("Recipe Title").url("http://example.com").isRecipe(true)
                .driveFileId("file-123").driveFileUrl("https://drive.google.com/file/file-123")
                .build();

        when(recipeService.createRecipe(USER_ID, request.url(), request.html(), Compression.BASE64_GZIP, request.title()))
                .thenReturn(expected);

        RecipeResponse response = controller.createRecipe(request, Compression.BASE64_GZIP, USER_ID, USER_EMAIL);

        assertThat(response).isEqualTo(expected);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), Compression.BASE64_GZIP, request.title());
    }

    @Test
    @DisplayName("Should pass null compression to RecipeService (treated as NONE, no decompression)")
    void shouldHandleNullCompression() throws IOException, AuthenticationException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        RecipeResponse expected = RecipeResponse.builder().title("Recipe Title").isRecipe(true).build();

        when(recipeService.createRecipe(eq(USER_ID), eq(request.url()), eq(request.html()), eq(null), eq(request.title())))
                .thenReturn(expected);

        RecipeResponse response = controller.createRecipe(request, null, USER_ID, USER_EMAIL);

        assertThat(response).isEqualTo(expected);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), null, request.title());
    }

    @Test
    @DisplayName("Should bubble up StorageNotConnectedException from createRecipe")
    void shouldBubbleUpStorageNotConnectedException() throws IOException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new StorageNotConnectedException("No storage configured"));

        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configured");
    }

    @Test
    @DisplayName("Should propagate IOException from RecipeService createRecipe")
    void shouldPropagateIOException() throws IOException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IOException("HTML extraction failed"));

        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTML extraction failed");
    }

    // --- createCustomRecipe (description-based) ---

    @Test
    @DisplayName("Should delegate createCustomRecipe with compression to RecipeService")
    void shouldDelegateCustomRecipeWithCompression() throws IOException {
        CustomRecipeRequest request = new CustomRecipeRequest(
                "Mix flour and eggs. Fry until golden.", "Crepes", "https://example.com");
        RecipeResponse expected = RecipeResponse.builder()
                .title("Crepes").url("https://example.com").isRecipe(true).driveFileId("file-123")
                .recipes(List.of(new RecipeItemResult("Crepes", "file-123", "https://drive.google.com/file/file-123")))
                .build();

        when(recipeService.createRecipeFromDescription(
                USER_ID, request.description(), request.title(), request.url(), Compression.BASE64_GZIP))
                .thenReturn(expected);

        RecipeResponse response = controller.createCustomRecipe(request, Compression.BASE64_GZIP, USER_ID, USER_EMAIL);

        assertThat(response).isEqualTo(expected);
        verify(recipeService).createRecipeFromDescription(
                USER_ID, request.description(), request.title(), request.url(), Compression.BASE64_GZIP);
    }

    @Test
    @DisplayName("Should pass null compression and null URL/title for custom recipe")
    void shouldPassNullCompressionAndNullUrlTitle() throws IOException {
        CustomRecipeRequest request = new CustomRecipeRequest("Some recipe description", null, null);
        RecipeResponse expected = RecipeResponse.builder().title("AI Title").isRecipe(true).build();

        when(recipeService.createRecipeFromDescription(USER_ID, request.description(), null, null, null))
                .thenReturn(expected);

        RecipeResponse response = controller.createCustomRecipe(request, null, USER_ID, USER_EMAIL);

        assertThat(response).isEqualTo(expected);
        verify(recipeService).createRecipeFromDescription(USER_ID, request.description(), null, null, null);
    }

    @Test
    @DisplayName("Should pass NONE compression through to RecipeService for custom recipe")
    void shouldPassNoneCompressionForCustomRecipe() throws IOException {
        CustomRecipeRequest request = new CustomRecipeRequest("raw description", "Title", null);
        RecipeResponse expected = RecipeResponse.builder().isRecipe(true).build();

        when(recipeService.createRecipeFromDescription(
                USER_ID, request.description(), request.title(), null, Compression.NONE))
                .thenReturn(expected);

        RecipeResponse response = controller.createCustomRecipe(request, Compression.NONE, USER_ID, USER_EMAIL);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should propagate StorageNotConnectedException from custom recipe endpoint")
    void shouldPropagateStorageExceptionFromCustomRecipe() throws IOException {
        CustomRecipeRequest request = new CustomRecipeRequest("Some recipe", null, null);
        when(recipeService.createRecipeFromDescription(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new StorageNotConnectedException("Storage not connected"));

        assertThatThrownBy(() -> controller.createCustomRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("Storage not connected");
    }
}
