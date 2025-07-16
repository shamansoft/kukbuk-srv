package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CookbookControllerStoreServiceTest {

    @Autowired
    private CookbookController controller;

    @MockitoBean
    private RecipeStoreService storeService;

    @MockitoBean
    private Transformer transformer;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private RawContentService rawContentService;

    @MockitoBean
    private Compressor compressor;

    @MockitoBean
    private DriveService googleDriveService;

    @MockitoBean
    private ContentHashService contentHashService;

    @BeforeEach
    void setUp() throws Exception {
        // Set up default mock behavior for store service
        when(storeService.findStoredRecipeByHash(anyString()))
                .thenReturn(Optional.empty());
        
        // Set up content hash service mock
        when(contentHashService.generateContentHash(anyString()))
                .thenReturn("mock-hash");
        
        // Set up transformer mock for store miss scenarios
        when(transformer.transform(anyString()))
                .thenReturn(new Transformer.Response(true, "test recipe yaml"));
        
        // Set up token service mock - handle checked exception
        when(tokenService.getAuthToken(any()))
                .thenReturn("mock-auth-token");
        
        // Set up compressor mock
        when(compressor.decompress(anyString()))
                .thenReturn("<html><body>Recipe content</body></html>");
        
        // Set up raw content service mock  
        when(rawContentService.fetch(anyString()))
                .thenReturn("<html><body>Recipe content</body></html>");
        
        // Set up Google Drive service mocks
        when(googleDriveService.getOrCreateFolder(anyString()))
                .thenReturn("mock-folder-id");
        when(googleDriveService.generateFileName(anyString()))
                .thenReturn("mock-filename.yaml");
        when(googleDriveService.uploadRecipeYaml(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new DriveService.UploadResult("mock-file-id", "https://drive.google.com/file/mock-file-id"));
    }

    @Test
    @DisplayName("Should record store miss when recipe not cached")
    void shouldRecordStoreMissWhenRecipeNotCached() throws Exception {
        // Given
        Request request = new Request(
                "<html><body>Recipe content</body></html>",
                "Test Recipe",
                "https://example.com/recipe"
        );

        // When
        RecipeResponse response = controller.createRecipe(
                request, 
                "none", 
                false, 
                Map.of()
        );

        // Then
        assertThat(response).isNotNull();
        verify(storeService).findStoredRecipeByHash("mock-hash");
    }

    @Test
    @DisplayName("Should record store hit when recipe is cached")
    void shouldRecordStoreHitWhenRecipeIsCached() throws Exception {
        // Given
        Request request = new Request(
                null, // No HTML to force URL fetch
                "Test Recipe",
                "https://example.com/recipe"
        );

        net.shamansoft.cookbook.model.Recipe cachedRecipe = 
                net.shamansoft.cookbook.model.Recipe.builder()
                        .contentHash("test-hash")
                        .sourceUrl("https://example.com/recipe")
                        .recipeYaml("recipe: test cached recipe")
                        .isValid(true)
                        .build();

        // Override the default mock for this specific hash to return cached recipe
        when(storeService.findStoredRecipeByHash("mock-hash"))
                .thenReturn(Optional.of(cachedRecipe));

        // When
        RecipeResponse response = controller.createRecipe(
                request, 
                "none", 
                false, 
                Map.of()
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        verify(storeService).findStoredRecipeByHash("mock-hash");
        // Store methods are void now, so we verify they weren't called
        verify(storeService, never()).storeValidRecipe(anyString(), anyString(), anyString());
        verify(storeService, never()).storeInvalidRecipe(anyString(), anyString());
    }

}