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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookbookControllerTest {

    private static final String TITLE = "Recipe Title";
    private static final String URL = "http://example.com";
    private static final String HTML = "<html>content</html>";
    private static final String YAML = "recipe: true";
    private static final String HASH = "hash-123";
    private static final String PROFILE_TOKEN = "profile-token";
    private static final String FALLBACK_TOKEN = "fallback-token";
    private static final String FOLDER_ID = "folder";
    private static final String FILE_NAME = "recipe.yaml";

    @Mock
    private HtmlExtractor htmlExtractor;
    @Mock
    private Transformer transformer;
    @Mock
    private DriveService driveService;
    @Mock
    private TokenService tokenService;
    @Mock
    private ContentHashService contentHashService;
    @Mock
    private RecipeStoreService recipeStoreService;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private CookbookController controller;

    @Test
    void createRecipeStoresToDriveWhenHtmlTransformsIntoRecipe() throws Exception {
        Request request = new Request("compressed", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
        when(htmlExtractor.extractHtml(request, null)).thenReturn(HTML);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(true, YAML));
        when(driveService.getOrCreateFolder(PROFILE_TOKEN)).thenReturn(FOLDER_ID);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(PROFILE_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, null, false, Map.of());

        assertThat(response)
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe,
                        RecipeResponse::driveFileId, RecipeResponse::driveFileUrl)
                .containsExactly(TITLE, URL, true, "file-id", "drive-url");

        verify(recipeStoreService).storeValidRecipe(HASH, URL, YAML);
    }

    @Test
    void createRecipeFallsBackToHeaderTokenWhenProfileLookupFails() throws Exception {
        Request request = new Request("payload", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenThrow(new IllegalStateException("missing"));
        when(tokenService.getAuthToken(any())).thenReturn(FALLBACK_TOKEN);
        when(htmlExtractor.extractHtml(request, null)).thenReturn(HTML);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(true, YAML));
        when(driveService.getOrCreateFolder(FALLBACK_TOKEN)).thenReturn(FOLDER_ID);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(FALLBACK_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(
                request,
                null,
                false,
                Map.of("X-Google-Token", FALLBACK_TOKEN)
        );

        assertThat(response.driveFileId()).isEqualTo("file-id");
        verify(tokenService).getAuthToken(any());
    }

    @Test
    void createRecipeSkipsDriveWhenNotARecipe() throws Exception {
        Request request = new Request("payload", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
        when(htmlExtractor.extractHtml(request, null)).thenReturn(HTML);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(false, "raw"));

        RecipeResponse response = controller.createRecipe(request, null, false, Map.of());

        assertThat(response.isRecipe()).isFalse();
        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
        verify(recipeStoreService).storeInvalidRecipe(HASH, URL);
    }

    @Test
    void createRecipeUsesCachedResultWhenAvailable() throws Exception {
        Request request = new Request("payload", TITLE, URL);
        StoredRecipe storedRecipe = mock(StoredRecipe.class);

        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.of(storedRecipe));
        when(storedRecipe.isValid()).thenReturn(true);
        when(storedRecipe.getRecipeYaml()).thenReturn(YAML);
        when(driveService.getOrCreateFolder(PROFILE_TOKEN)).thenReturn(FOLDER_ID);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(PROFILE_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult("file-id", "cached-url"));

        RecipeResponse response = controller.createRecipe(request, null, false, Map.of());

        assertThat(response.driveFileUrl()).isEqualTo("cached-url");
        verify(htmlExtractor, never()).extractHtml(any(), any());
        verify(transformer, never()).transform(any());
    }

    @Test
    void createRecipePropagatesExtractorFailures() throws Exception {
        Request request = new Request("payload", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(request, null)).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void createRecipeRespectsCompressionFlag() throws Exception {
        Request request = new Request("inline", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenReturn(PROFILE_TOKEN);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findStoredRecipeByHash(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(request, "none")).thenReturn(HTML);
        when(transformer.transform(HTML)).thenReturn(new Transformer.Response(true, YAML));
        when(driveService.getOrCreateFolder(PROFILE_TOKEN)).thenReturn(FOLDER_ID);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(PROFILE_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, "none", false, Map.of());

        assertThat(response.isRecipe()).isTrue();
        verify(htmlExtractor).extractHtml(request, "none");
    }

    @Test
    void createRecipeThrowsWhenTokenLookupFailsTwice() throws Exception {
        Request request = new Request("payload", TITLE, URL);

        when(userProfileService.getValidOAuthToken("test-user")).thenThrow(new IllegalStateException("missing"));
        when(tokenService.getAuthToken(any())).thenThrow(new AuthenticationException("bad header token") {});

        assertThatThrownBy(() -> controller.createRecipe(request, null, false, Map.of()))
                .isInstanceOf(AuthenticationException.class);
        verify(htmlExtractor, never()).extractHtml(any(), any());
    }
}