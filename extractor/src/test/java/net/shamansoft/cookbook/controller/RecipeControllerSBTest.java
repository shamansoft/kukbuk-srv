package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.config.TestFirebaseConfig;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.html.HtmlFetcher;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.UserProfileService;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestFirebaseConfig.class)
class RecipeControllerSBTest {

    public static final String RECIPE_PATH = "/v1/recipes";
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
    private StorageService storageService;

    @MockitoBean
    private Compressor compressor;

    @MockitoBean
    private HtmlFetcher htmlFetcher;

    @MockitoBean
    private RecipeValidationService validationService;

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
                .folderId("mock-folder-id")
                .build();

        when(storageService.getStorageInfo(anyString()))
                .thenReturn(mockStorageInfo);
        when(storageService.isStorageConnected(anyString()))
                .thenReturn(true);

        // Set up default mock behavior for store services
        when(recipeStoreService.findStoredRecipeByHash(anyString()))
                .thenReturn(Optional.empty());

        // Set up content hash service mock
        when(contentHashService.generateContentHash(anyString()))
                .thenReturn("mock-hash");

        // Set up UserProfileService to fall back to header tokens
        when(userProfileService.getValidOAuthToken(anyString()))
                .thenThrow(new Exception("No OAuth token in profile"));

        // Set up validation service mock
        when(validationService.toYaml(any(Recipe.class)))
                .thenReturn("transformed content");
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
        Recipe testRecipe = createTestRecipe();
        when(htmlExtractor.extractHtml("http://example.com", "compressed html", null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(Transformer.Response.recipe(testRecipe));
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                RECIPE_PATH,
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
        Recipe testRecipe = createTestRecipe();
        when(htmlExtractor.extractHtml("http://example.com", null, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(Transformer.Response.recipe(testRecipe));
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                RECIPE_PATH,
                requestEntity,
                RecipeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::isRecipe)
                .containsExactly("Title", "http://example.com", true);
    }

    @Test
    void createRecipe_returns_500_when_transform_throws_ClientException() throws IOException {
        Request request = new Request("raw html", "Title", "http://example.com");
        when(htmlExtractor.extractHtml("http://example.com", "raw html", null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenThrow(new ClientException("Transformation error"));

        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                RECIPE_PATH,
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
                RECIPE_PATH,
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
        when(htmlExtractor.extractHtml("http://example.com", "compressed html", null)).thenThrow(new IOException("Decompression failed"));

        // When/Then
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                RECIPE_PATH,
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
        Recipe testRecipe = createTestRecipe();
        when(htmlExtractor.extractHtml("http://example.com", "raw html", "none")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(Transformer.Response.recipe(testRecipe));
        when(googleDriveService.generateFileName("Title")).thenReturn("Title.yaml");
        when(googleDriveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult("file-id", "http://example.com/file-id"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                RECIPE_PATH + "?compression=none",
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
        when(htmlExtractor.extractHtml("http://example.com", null, null)).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn(Transformer.Response.notRecipe());

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                RECIPE_PATH,
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
    void createRecipe_whenStorageServiceFailsWithGenericException_shouldReturnInternalServerError() throws Exception {
        // Given
        Request request = new Request(null, "Title", "http://example.com");
        when(storageService.getStorageInfo(anyString()))
                .thenThrow(new RuntimeException("Storage service error"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                RECIPE_PATH,
                requestEntity,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("error")).isEqualTo("Internal Server Error");
    }

    @Test
    void createRecipe_whenUrlFetchFails_shouldReturnBadRequest() throws IOException {
        // Given
        Request request = new Request(null, "Title", "http://example.com");
        when(htmlExtractor.extractHtml("http://example.com", null, null)).thenThrow(new IOException("Failed to fetch URL"));

        // When
        HttpEntity<Request> requestEntity = new HttpEntity<>(request, createHeadersWithOAuthToken());
        ResponseEntity<Map> response = restTemplate.postForEntity(
                RECIPE_PATH,
                requestEntity,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("IO Error");
    }

    @Test
    void testRestTemplate_isConfigured_withAutoConfigureTestRestTemplate() {
        // Verify that TestRestTemplate is properly autowired with Spring Boot 4
        // using @AutoConfigureTestRestTemplate annotation
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRestTemplate()).isNotNull();
    }

    private Recipe createTestRecipe() {
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Test Recipe",
                        null,
                        "Test Author",
                        null,
                        LocalDate.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "Test description",
                List.of(new Ingredient("flour", "100g", null, null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(null, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null
        );
    }

}