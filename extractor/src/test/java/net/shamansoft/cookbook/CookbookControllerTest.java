package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookbookControllerTest {

    @Mock
    private RawContentService rawContentService;

    @Mock
    private Transformer transformer;

    @Mock
    private Compressor compressor;

    @Mock
    private DriveService driveService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private CookbookController controller;

    private static final String TITLE = "Recipe Title";
    private static final String URL = "http://example.com";
    private static final String TOKEN = "token";
    private static final String FOLDER_ID = "folder-id";
    private static final String FILE_NAME = "recipe-file-name";
    private static final String RAW_HTML = "<html><body>Recipe content</body></html>";
    private static final String TRANSFORMED_CONTENT = "transformed recipe content";

    @Test
    void createRecipeStoresIfItIsARecipe() throws IOException, AuthenticationException {
        when(tokenService.getAuthToken(any(HttpHeaders.class))).thenReturn(TOKEN);
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(driveService.getOrCreateFolder("token")).thenReturn("folder-id");
        when(driveService.generateFileName("Title")).thenReturn("file-name");
        when(driveService.uploadRecipeYaml("token", "folder-id", "file-name", "transformed content"))
                .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, null, true, Map.of("X-S-AUTH-TOKEN", "token"));

        assertThat(response)
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe)
                .containsExactly("Title", "http://example.com", true);
    }

    @Test
    void createRecipeSkipsStorageIfNotARecipe() throws IOException, AuthenticationException {
        when(tokenService.getAuthToken(any(HttpHeaders.class))).thenReturn(TOKEN);
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(false, "not a recipe"));

        RecipeResponse response = controller.createRecipe(request, null, true, Map.of("X-S-AUTH-TOKEN", "token"));

        assertThat(response)
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe)
                .containsExactly("Title", "http://example.com", false);

        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    // Other tests that depend on `transformer.transform` should also be updated similarly.

}