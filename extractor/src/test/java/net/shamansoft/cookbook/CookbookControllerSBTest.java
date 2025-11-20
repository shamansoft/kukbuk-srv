package net.shamansoft.cookbook;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.config.TestFirebaseConfig;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.HtmlExtractor;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
@Import(TestFirebaseConfig.class)
class CookbookControllerSBTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private HtmlExtractor htmlExtractor;

    @MockitoBean
    private Transformer transformer;

    @MockitoBean
    private DriveService googleDriveService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private RecipeStoreService recipeStoreService;

    @MockitoBean
    private ContentHashService contentHashService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private Compressor compressor;

    @MockitoBean
    private RawContentService rawContentService;

    @BeforeEach
    void setUp() throws Exception {
        // Set up default mock behavior for store services
        when(recipeStoreService.findStoredRecipeByHash(anyString()))
                .thenReturn(Optional.empty());

        // Set up content hash service mock
        when(contentHashService.generateContentHash(anyString()))
                .thenReturn("mock-hash");

        // Set up UserProfileService to fall back to header tokens
        when(userProfileService.getValidOAuthToken(anyString()))
                .thenThrow(new Exception("No OAuth token in profile"));
    }

    private HttpHeaders createHeadersWithOAuthToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Google-Token", "test-oauth-token");
        headers.setBearerAuth("test-firebase-token");
        return headers;
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
        when(htmlExtractor.extractHtml(request, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("test-oauth-token");
        when(googleDriveService.getOrCreateFolder("test-oauth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("test-oauth-token");
        when(googleDriveService.getOrCreateFolder("test-oauth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Internal Server Error");
    }

    @Test
    void invalidRequestReturnsBadRequest() {
        Request request = new Request(null, null, null);

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, null)).thenThrow(new IOException("Decompression failed"));

        // When/Then
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, "none")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenReturn("test-oauth-token");
        when(googleDriveService.getOrCreateFolder("test-oauth-token")).thenReturn("folder-id");
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe?compression=none",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(false, "not a recipe"));
        when(tokenService.getAuthToken(any())).thenReturn("test-oauth-token");

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(new Transformer.Response(true, "transformed content"));
        when(tokenService.getAuthToken(any())).thenThrow(new AuthenticationException("Invalid token"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
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
        when(htmlExtractor.extractHtml(request, null)).thenThrow(new IOException("Failed to fetch URL"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/recipe",
                requestEntity,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("IO Error");
    }

    @Test
    void controllerMethod_createRecipe_worksWithParams() throws IOException, AuthenticationException {
        // Given
        HtmlExtractor htmlExtractor = new HtmlExtractor(compressor, rawContentService);
        CookbookController controller = new CookbookController(
                htmlExtractor, transformer, googleDriveService, tokenService,
                contentHashService, recipeStoreService, userProfileService
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