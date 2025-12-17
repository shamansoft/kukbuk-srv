package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.model.StoredRecipe;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.HtmlExtractor;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CookbookControllerAdditionalTest {

    private static final String TITLE = "New Recipe";
    private static final String URL = "http://example.org";
    private static final String HTML = "<html><body>content</body></html>";
    private static final String YAML = "recipe: true";
    private static final String HASH = "hash-abc";
    private static final String PROFILE_TOKEN = "profile-token";

    @Mock
    private HtmlExtractor htmlExtractor;
    @Mock
    private Transformer transformer;
    @Mock
    private DriveService driveService;
    @Mock
    private RecipeStoreService recipeStoreService;
    @Mock
    private ContentHashService contentHashService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private CookbookController controller;

    @BeforeEach
    void setUp() throws Exception {
        // Set up storage service mock
        StorageInfo mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(PROFILE_TOKEN)
                .refreshToken("mock-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .connectedAt(Instant.now())
                .defaultFolderId("mock-folder-id")
                .build();

        when(storageService.getStorageInfo(anyString())).thenReturn(mockStorageInfo);
        when(storageService.isStorageConnected(anyString())).thenReturn(true);

        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(driveService.getOrCreateFolder(any())).thenReturn("folder");
        when(driveService.generateFileName(TITLE)).thenReturn("recipe.yaml");
        when(driveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));
    }

    @Test
    void createRecipeUsesHtmlExtractorAndStoresRecipe() throws Exception {
        Request request = new Request("payload", TITLE, URL);
        when(htmlExtractor.extractHtml(request, null)).thenReturn(HTML);
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(true, YAML));

        RecipeResponse response = controller.createRecipe(request, null, false, Map.of());

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.driveFileUrl()).isEqualTo("drive-url");
        verify(htmlExtractor).extractHtml(request, null);
        verify(recipeStoreService).storeValidRecipe(HASH, URL, YAML);
    }

    @Test
    void createRecipeSkipsDriveWhenTransformerSaysNotRecipe() throws Exception {
        Request request = new Request("payload", TITLE, URL);
        when(htmlExtractor.extractHtml(request, "none")).thenReturn(HTML);
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(false, "raw"));

        RecipeResponse response = controller.createRecipe(request, "none", false, Map.of());

        assertThat(response.isRecipe()).isFalse();
        assertThat(response.driveFileId()).isNull();
        verify(recipeStoreService).storeInvalidRecipe(HASH, URL);
        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    @Test
    void createRecipeUsesCachedEntryWithoutExtractingHtml() throws Exception {
        StoredRecipe storedRecipe = mock(StoredRecipe.class);
        when(storedRecipe.isValid()).thenReturn(true);
        when(storedRecipe.getRecipeYaml()).thenReturn(YAML);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.of(storedRecipe));

        Request request = new Request("ignored", TITLE, URL);
        RecipeResponse response = controller.createRecipe(request, null, false, Map.of());

        assertThat(response.isRecipe()).isTrue();
        verify(htmlExtractor, never()).extractHtml(any(), any());
        verify(transformer, never()).transform(any());
    }

    @Test
    void createRecipeThrowsWhenStorageNotConnected() throws Exception {
        when(storageService.getStorageInfo(anyString()))
                .thenThrow(new StorageNotConnectedException("No storage configured"));

        Request request = new Request("payload", TITLE, URL);

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configured");

        verify(htmlExtractor, never()).extractHtml(any(), any());
    }

    @Test
    void createRecipeThrowsWhenStorageServiceFails() throws Exception {
        when(storageService.getStorageInfo(anyString()))
                .thenThrow(new RuntimeException("Storage service error"));

        Request request = new Request("payload", TITLE, URL);

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to retrieve storage credentials");
        verify(htmlExtractor, never()).extractHtml(any(), any());
    }

    @Test
    void createRecipePropagatesExtractorException() throws Exception {
        Request request = new Request("payload", TITLE, URL);
        when(htmlExtractor.extractHtml(request, null)).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("boom");
        verify(transformer, never()).transform(any());
    }
}