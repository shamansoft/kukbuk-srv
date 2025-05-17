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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
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
    private static final String TOKEN = "auth-token";
    private static final String FOLDER_ID = "folder-id";
    private static final String FILE_NAME = "recipe-file-name";
    private static final String RAW_HTML = "<html><body>Recipe content</body></html>";
    private static final String TRANSFORMED_CONTENT = "transformed recipe content";

    @Test
    void healthEndpointReturnsOk() {
        assertThat(controller.gcpHealth()).isEqualTo("OK");
    }

    @Test
    void helloEndpointReturnsGreetingWithName() {
        assertThat(controller.index("John")).isEqualTo("Hello, Cookbook user John!");
    }

    @Test
    void createRecipeFromHtml() throws IOException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");
        when(tokenService.verifyToken("token")).thenReturn(true);
        when(driveService.getOrCreateFolder("token")).thenReturn("folder-id");
        when(driveService.generateFileName("Title")).thenReturn("file-name");
        when(driveService.uploadRecipeYaml("token", "folder-id", "file-name", "transformed content"))
            .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, null, true, Map.of("X-S-AUTH-TOKEN", "token"));

        assertThat(response)
            .extracting(RecipeResponse::title, RecipeResponse::url)
            .containsExactly("Title", "http://example.com");
    }

    @Test
    void createRecipeFromUrl() throws IOException {
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");
        when(tokenService.verifyToken("token")).thenReturn(true);
        when(driveService.getOrCreateFolder("token")).thenReturn("folder-id");
        when(driveService.generateFileName("Title")).thenReturn("file-name");
        when(driveService.uploadRecipeYaml("token", "folder-id", "file-name", "transformed content"))
            .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, null, false, Map.of("X-S-AUTH-TOKEN", "token"));

        assertThat(response)
            .extracting(RecipeResponse::title, RecipeResponse::url)
            .containsExactly("Title", "http://example.com");
    }

    @Test
    void createRecipeWithDebugIncludesRawHtml() throws IOException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");
        when(tokenService.verifyToken("token")).thenReturn(true);
        when(driveService.getOrCreateFolder("token")).thenReturn("folder-id");
        when(driveService.generateFileName("Title")).thenReturn("file-name");
        when(driveService.uploadRecipeYaml("token", "folder-id", "file-name", "transformed content"))
            .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));
        
        RecipeResponse response = controller.createRecipe(request, null, true, Map.of("X-S-AUTH-TOKEN", "token"));

        assertThat(response.title()).isEqualTo("Title");
        assertThat(response.url()).isEqualTo("http://example.com");
        assertThat(response.driveFileUrl()).isEqualTo("drive-url");
    }

    @Test
    void createRecipeWithNoCompressionSkipsDecompression() throws IOException {
        Request request = new Request("raw html", "Title", "http://example.com");
        when(transformer.transform("raw html")).thenReturn("transformed content");
        when(tokenService.verifyToken("token")).thenReturn(true);
        when(driveService.getOrCreateFolder("token")).thenReturn("folder-id");
        when(driveService.generateFileName("Title")).thenReturn("file-name");
        when(driveService.uploadRecipeYaml("token", "folder-id", "file-name", "transformed content"))
            .thenReturn(new DriveService.UploadResult("file-id", "drive-url"));

        RecipeResponse response = controller.createRecipe(request, "none", false, Map.of("X-S-AUTH-TOKEN", "token"));

        verify(compressor, never()).decompress(any());
        assertThat(response.title()).isEqualTo("Title");
        assertThat(response.url()).isEqualTo("http://example.com");
    }

    @Test
    void ioExceptionFromDecompressionReturnsBadRequest() throws IOException {

        ResponseEntity<String> response = controller.handleIOException(new IOException("Decompression failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Decompression failed");
    }

    @Test
    void validationExceptionReturnsBadRequest() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = mock(FieldError.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        when(fieldError.getDefaultMessage()).thenReturn("Title is required");

        ResponseEntity<String> response = controller.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Validation error: Title is required");
    }
    
    @Test
    void comprehensiveRecipeCreationTest() throws IOException {
        // Setup request with all fields
        Request request = new Request("compressed content", TITLE, URL);
        
        // Setup mocks for all services
        when(compressor.decompress("compressed content")).thenReturn(RAW_HTML);
        when(transformer.transform(RAW_HTML)).thenReturn(TRANSFORMED_CONTENT);
        when(tokenService.verifyToken(TOKEN)).thenReturn(true);
        when(driveService.getOrCreateFolder(TOKEN)).thenReturn(FOLDER_ID);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(TOKEN, FOLDER_ID, FILE_NAME, TRANSFORMED_CONTENT))
            .thenReturn(new DriveService.UploadResult("file-id-123", "https://drive.google.com/file-id-123"));
        
        // Create headers with auth token
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", TOKEN);
        
        // Execute controller method
        RecipeResponse response = controller.createRecipe(request, null, false, headers);
        
        // Verify all service interactions
        verify(compressor).decompress("compressed content");
        verify(transformer).transform(RAW_HTML);
        verify(tokenService).verifyToken(TOKEN);
        verify(driveService).getOrCreateFolder(TOKEN);
        verify(driveService).generateFileName(TITLE);
        verify(driveService).uploadRecipeYaml(TOKEN, FOLDER_ID, FILE_NAME, TRANSFORMED_CONTENT);
        
        // Assert response properties
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.driveFileId()).isEqualTo("file-id-123");
        assertThat(response.driveFileUrl()).isEqualTo("https://drive.google.com/file-id-123");
    }
    
    @Test
    void unauthorizedTokenReturnsException() throws IOException {
        // Setup request
        Request request = new Request("content", TITLE, URL);
        
        // Mock token verification to fail
        when(tokenService.verifyToken(TOKEN)).thenReturn(false);
        
        // Create headers with auth token
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", TOKEN);
        assertThatThrownBy(() -> controller.createRecipe(request, null, false, headers))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Invalid auth token");
        // Verify token service was called
        verify(tokenService).verifyToken(TOKEN);
        // Verify no further services were called
        verify(driveService, never()).getOrCreateFolder(any());
    }
}