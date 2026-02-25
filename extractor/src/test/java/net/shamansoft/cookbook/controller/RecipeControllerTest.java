package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.RecipeListResponse;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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

    // ---- createRecipe -------------------------------------------------------

    @Test
    @DisplayName("Should delegate createRecipe to RecipeService with correct parameters")
    void shouldDelegateToRecipeService() throws IOException, AuthenticationException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        String compression = "gzip";
        RecipeResponse expectedResponse = RecipeResponse.builder()
                .title("Recipe Title").url("http://example.com").isRecipe(true)
                .driveFileId("file-123").driveFileUrl("https://drive.google.com/file/file-123").build();
        when(recipeService.createRecipe(USER_ID, request.url(), request.html(), compression, request.title()))
                .thenReturn(expectedResponse);
        RecipeResponse response = controller.createRecipe(request, compression, USER_ID, USER_EMAIL);
        assertThat(response).isEqualTo(expectedResponse);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), compression, request.title());
    }

    @Test
    @DisplayName("Should rethrow StorageNotConnectedException")
    void shouldBubbleUpStorageNotConnectedException() throws IOException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new StorageNotConnectedException("No storage configured"));
        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configured");
    }

    @Test
    @DisplayName("Should propagate IOException from RecipeService")
    void shouldPropagateIOException() throws IOException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        when(recipeService.createRecipe(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IOException("HTML extraction failed"));
        assertThatThrownBy(() -> controller.createRecipe(request, null, USER_ID, USER_EMAIL))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTML extraction failed");
    }

    @Test
    @DisplayName("Should handle null compression parameter")
    void shouldHandleNullCompression() throws IOException, AuthenticationException {
        Request request = new Request("html-payload", "Recipe Title", "http://example.com");
        RecipeResponse expectedResponse = RecipeResponse.builder()
                .title("Recipe Title").url("http://example.com").isRecipe(true).build();
        when(recipeService.createRecipe(eq(USER_ID), eq(request.url()), eq(request.html()),
                eq(null), eq(request.title()))).thenReturn(expectedResponse);
        RecipeResponse response = controller.createRecipe(request, null, USER_ID, USER_EMAIL);
        assertThat(response).isEqualTo(expectedResponse);
        verify(recipeService).createRecipe(USER_ID, request.url(), request.html(), null, request.title());
    }

    // ---- listRecipes --------------------------------------------------------

    @Test
    @DisplayName("listRecipes: returns paginated list with cache headers")
    void listRecipesShouldReturnPaginatedList() {
        RecipeDto recipe = RecipeDto.builder().id("file-1").title("Pasta").build();
        RecipeService.RecipeListResult result =
                new RecipeService.RecipeListResult(List.of(recipe), "next-token-abc");
        when(recipeService.listRecipes(USER_ID, 20, null)).thenReturn(result);

        ResponseEntity<RecipeListResponse> response = controller.listRecipes(USER_ID, 20, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRecipes()).hasSize(1);
        assertThat(response.getBody().getRecipes().get(0).getTitle()).isEqualTo("Pasta");
        assertThat(response.getBody().getNextPageToken()).isEqualTo("next-token-abc");
        assertThat(response.getBody().getCount()).isEqualTo(1);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=300");
        verify(recipeService).listRecipes(USER_ID, 20, null);
    }

    @Test
    @DisplayName("listRecipes: forwards pageToken to service, returns empty list")
    void listRecipesShouldForwardPageToken() {
        RecipeService.RecipeListResult result =
                new RecipeService.RecipeListResult(List.of(), null);
        when(recipeService.listRecipes(USER_ID, 10, "token-xyz")).thenReturn(result);

        ResponseEntity<RecipeListResponse> response = controller.listRecipes(USER_ID, 10, "token-xyz");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRecipes()).isEmpty();
        assertThat(response.getBody().getNextPageToken()).isNull();
        assertThat(response.getBody().getCount()).isEqualTo(0);
        verify(recipeService).listRecipes(USER_ID, 10, "token-xyz");
    }

    @Test
    @DisplayName("listRecipes: propagates StorageNotConnectedException")
    void listRecipesShouldPropagateStorageNotConnected() {
        when(recipeService.listRecipes(USER_ID, 20, null))
                .thenThrow(new StorageNotConnectedException("No storage configured"));
        assertThatThrownBy(() -> controller.listRecipes(USER_ID, 20, null))
                .isInstanceOf(StorageNotConnectedException.class);
    }

    // ---- getRecipe ----------------------------------------------------------

    @Test
    @DisplayName("getRecipe: returns recipe DTO with 1-hour cache header")
    void getRecipeShouldReturnRecipeWithCacheHeaders() {
        RecipeDto expectedDto = RecipeDto.builder().id("file-abc").title("Chocolate Cake").build();
        when(recipeService.getRecipe(USER_ID, "file-abc")).thenReturn(expectedDto);

        ResponseEntity<RecipeDto> response = controller.getRecipe(USER_ID, "file-abc");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("file-abc");
        assertThat(response.getBody().getTitle()).isEqualTo("Chocolate Cake");
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=3600");
        verify(recipeService).getRecipe(USER_ID, "file-abc");
    }

    @Test
    @DisplayName("getRecipe: propagates RecipeNotFoundException")
    void getRecipeShouldPropagateRecipeNotFound() {
        when(recipeService.getRecipe(USER_ID, "missing-id"))
                .thenThrow(new RecipeNotFoundException("Recipe not found: missing-id"));
        assertThatThrownBy(() -> controller.getRecipe(USER_ID, "missing-id"))
                .isInstanceOf(RecipeNotFoundException.class)
                .hasMessageContaining("missing-id");
    }

    @Test
    @DisplayName("getRecipe: propagates StorageNotConnectedException")
    void getRecipeShouldPropagateStorageNotConnected() {
        when(recipeService.getRecipe(USER_ID, "file-xyz"))
                .thenThrow(new StorageNotConnectedException("No storage configured"));
        assertThatThrownBy(() -> controller.getRecipe(USER_ID, "file-xyz"))
                .isInstanceOf(StorageNotConnectedException.class);
    }
}
