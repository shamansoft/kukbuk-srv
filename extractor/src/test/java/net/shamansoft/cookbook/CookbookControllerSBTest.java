package net.shamansoft.cookbook;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.naming.AuthenticationException;
import java.util.Map;
import java.io.IOException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookbookControllerSBTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private RawContentService rawContentService;

    @MockitoBean
    private Transformer transformer;

    @MockitoBean
    private Compressor compressor;
    @MockitoBean
    private DriveService googleDriveService;
    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private RecipeStoreService recipeStoreService;

    @MockitoBean
    private ContentHashService contentHashService;

    @BeforeEach
    void setUp() {
        // Set up default mock behavior for store services
        when(recipeStoreService.findStoredRecipeByHash(anyString()))
                .thenReturn(Optional.empty());
        
        // Set up content hash service mock
        when(contentHashService.generateContentHash(anyString()))
                .thenReturn("mock-hash");
    }

    @Test
    void healthEndpointReturnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
    }

    @Test
    void helloEndpointReturnsGreetingWithName() {
        ResponseEntity<String> response = restTemplate.getForEntity("/hello/John", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, Cookbook user John!");
    }

    @Test
    void createRecipeFromCompressedHtml() throws IOException, AuthenticationException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("auth-token");
        when(googleDriveService.getOrCreateFolder("auth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                request,
                RecipeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe)
                .containsExactly("Title", "http://example.com", true);
    }

    @Test
    void createRecipeFromUrl() throws IOException, AuthenticationException {
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("auth-token");
        when(googleDriveService.getOrCreateFolder("auth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                request,
                RecipeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe)
                .containsExactly("Title", "http://example.com", true);
    }

    @Test
    void createRecipe_returns_500_when_transform_throws_ClientException() {
        Request request = new Request("raw html", "Title", "http://example.com");
        when(transformer.transform("raw html")).thenThrow(new ClientException("Transformation error"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                request,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Internal Server Error");
    }

    @Test
    void invalidRequestReturnsBadRequest() {
        Request request = new Request(null, null, null);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                request,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Validation Error");
        assertThat(response.getBody().get("validationErrors")).isNotNull();
    }

    @Test
    void createRecipe_withDecompressionFailure_shouldThrowException() throws IOException, AuthenticationException {
        // Given
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenThrow(new IOException("Decompression failed"));

        // When/Then
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                request,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("IO Error");
    }

    @Test
    void createRecipe_withNoneCompression_shouldSkipDecompression() throws IOException, AuthenticationException {
        // Given
        Request request = new Request("raw html", "Title", "http://example.com");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("auth-token");
        when(googleDriveService.getOrCreateFolder("auth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        // When
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe?compression=none",
                request,
                RecipeResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isRecipe()).isTrue();
    }

    @Test
    void createRecipe_whenContentIsNotRecipe_shouldNotStoreToDrive() throws IOException, AuthenticationException {
        // Given
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(false, "not a recipe"));
        when(tokenService.getAuthToken(any())).thenReturn("auth-token");

        // When
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                request,
                RecipeResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isRecipe()).isFalse();
        assertThat(response.getBody().driveFileId()).isNull();
        assertThat(response.getBody().driveFileUrl()).isNull();
    }

    @Test
    void createRecipe_withAuthenticationFailure_shouldReturnUnauthorized() throws IOException, AuthenticationException {
        // Given
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenThrow(new AuthenticationException("Invalid token"));

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                request,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("error")).isEqualTo("Authentication Error");
    }

    @Test
    void createRecipe_whenUrlFetchFails_shouldReturnBadRequest() throws IOException {
        // Given
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenThrow(new IOException("Failed to fetch URL"));

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                request,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("IO Error");
    }

    @Test
    void controllerMethod_createRecipe_worksWithParams() throws IOException, AuthenticationException {
        // Given
        CookbookController controller = new CookbookController(
                rawContentService, transformer, compressor, googleDriveService, tokenService, 
                contentHashService, recipeStoreService
        );
        Request request = new Request("raw html", "Title", "http://example.com");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(googleDriveService.getOrCreateFolder(any())).thenReturn("folder-id");
        when(googleDriveService.generateFileName(any())).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        // When
        RecipeResponse response = controller.createRecipe(
                request,
                "none",
                false,
                Map.of("Authorization", "Bearer token")
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.title()).isEqualTo("Title");
    }
}