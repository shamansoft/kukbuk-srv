package net.shamansoft.cookbook;

import net.shamansoft.cookbook.config.TestFirebaseConfig;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.model.StoredRecipe;
import net.shamansoft.cookbook.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestFirebaseConfig.class)
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
    private HtmlExtractor htmlExtractor;

    @MockitoBean
    private DriveService googleDriveService;

    @MockitoBean
    private ContentHashService contentHashService;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        // Set up storage service mock - default to having storage configured
        StorageInfo mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .connectedAt(Instant.now())
                .defaultFolderId("mock-folder-id")
                .build();

        when(storageService.getStorageInfo(anyString()))
                .thenReturn(mockStorageInfo);
        when(storageService.isStorageConnected(anyString()))
                .thenReturn(true);

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
        
        // Set up HTML extractor mock
        when(htmlExtractor.extractHtml(any(Request.class), any(String.class)))
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

        StoredRecipe cachedRecipe =
                StoredRecipe.builder()
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

    @Test
    @DisplayName("Should throw StorageNotConnectedException when no storage configured")
    void shouldThrowStorageNotConnectedExceptionWhenNoStorage() throws Exception {
        // Given
        Request request = new Request(
                "<html><body>Recipe content</body></html>",
                "Test Recipe",
                "https://example.com/recipe"
        );

        // Override the default mock to simulate no storage configured
        when(storageService.getStorageInfo(anyString()))
                .thenThrow(new StorageNotConnectedException("No storage configured"));
        when(storageService.isStorageConnected(anyString()))
                .thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.createRecipe(
                request,
                "none",
                false,
                Map.of()
        ))
        .isInstanceOf(StorageNotConnectedException.class)
        .hasMessageContaining("No storage configured");

        // Verify storage service was called
        verify(storageService).getStorageInfo("test-user");
    }

    @Test
    @DisplayName("Should successfully create recipe when storage is configured")
    void shouldSuccessfullyCreateRecipeWithStorageConfigured() throws Exception {
        // Given
        Request request = new Request(
                "<html><body>Recipe content</body></html>",
                "Test Recipe",
                "https://example.com/recipe"
        );

        // Storage service is already mocked in setUp() to return valid storage info

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
        assertThat(response.driveFileUrl()).isNotNull();
        assertThat(response.driveFileUrl()).isEqualTo("https://drive.google.com/file/mock-file-id");

        // Verify storage service was used to get access token
        verify(storageService).getStorageInfo("test-user");

        // Verify recipe was uploaded to Google Drive with the token from storage
        verify(googleDriveService).uploadRecipeYaml(
                anyString(),  // accessToken from storage
                anyString(),  // folderId
                anyString(),  // fileName
                anyString()   // yaml content
        );
    }

}