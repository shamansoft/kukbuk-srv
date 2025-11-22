package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.model.StoredRecipe;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.HtmlExtractor;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CookbookControllerAdditionalTest {

    private static final String TITLE = "New Recipe";
    private static final String URL = "http://example.org";
    private static final String HTML = "<html><body>content</body></html>";
    private static final String YAML = "recipe: true";
    private static final String HASH = "hash-abc";
    private static final String PROFILE_TOKEN = "profile-token";
    private static final String HEADER_TOKEN = "header-token";

    @Mock
    private HtmlExtractor htmlExtractor;
    @Mock
    private Transformer transformer;
    @Mock
    private DriveService driveService;
    @Mock
    private TokenService tokenService;
    @Mock
    private RecipeStoreService recipeStoreService;
    @Mock
    private ContentHashService contentHashService;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private CookbookController controller;

    @BeforeEach
    void setUp() throws Exception {
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
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
    void createRecipeFallsBackToHeaderTokenWhenProfileLookupFails() throws Exception {
        when(userProfileService.getValidOAuthToken("test-user")).thenThrow(new IllegalStateException("missing"));
        when(tokenService.getAuthToken(any())).thenReturn(HEADER_TOKEN);
        Request request = new Request("payload", TITLE, URL);
        when(htmlExtractor.extractHtml(request, null)).thenReturn(HTML);
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(true, YAML));

        RecipeResponse response = controller.createRecipe(
                request,
                null,
                false,
                Map.of("X-Google-Token", HEADER_TOKEN)
        );

        assertThat(response.driveFileId()).isEqualTo("file-id");
        verify(tokenService).getAuthToken(any());
        verify(driveService).getOrCreateFolder(HEADER_TOKEN);
    }

    @Test
    void createRecipeThrowsWhenBothTokenLookupsFail() throws Exception {
        when(userProfileService.getValidOAuthToken("test-user")).thenThrow(new IllegalStateException("missing"));
        when(tokenService.getAuthToken(any())).thenThrow(new AuthenticationException("bad header token") {});

        Request request = new Request("payload", TITLE, URL);

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(AuthenticationException.class);
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