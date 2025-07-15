package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.model.Recipe;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeIntegrationNewTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private RecipeStoreService recipeStoreService;

    @MockitoSpyBean
    private Transformer transformer;

    @Container
    static GenericContainer<?> wiremockContainer = new GenericContainer<>("wiremock/wiremock:3.3.1")
            .withExposedPorts(8080)
            .withCommand("--global-response-templating");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String wiremockUrl = "http://localhost:" + wiremockContainer.getMappedPort(8080);

        // Configure Google services to use WireMock
        registry.add("cookbook.gemini.base-url", () -> wiremockUrl);
        registry.add("cookbook.drive.base-url", () -> wiremockUrl);
        registry.add("cookbook.drive.upload-url", () -> wiremockUrl);
        registry.add("cookbook.drive.auth-url", () -> wiremockUrl);
        registry.add("cookbook.google.oauth-id", () -> "test-client-id");
        registry.add("cookbook.gemini.api-key", () -> "test-api-key");
    }

    @BeforeEach
    void setUp() {
        WireMock.configureFor("localhost", wiremockContainer.getMappedPort(8080));
        WireMock.reset(); // Clear any existing stubs

        setupGoogleAuthMock();
        setupGeminiMock();
        setupGoogleDriveMocks();
    }

    private void setupGoogleAuthMock() {
        stubFor(get(urlPathEqualTo("/tokeninfo"))
                .withQueryParam("access_token", equalTo("valid-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "aud": "test-client-id",
                                    "user_id": "123456789",
                                    "scope": "https://www.googleapis.com/auth/drive.file",
                                    "expires_in": 3600
                                }
                                """)));
    }

    private void setupGeminiMock() {
        stubFor(post(urlPathMatching("/models/gemini-2.0-flash:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "schema_version: \"1.0.0\"\nrecipe_version: \"1.0\"\ndate_created: \"2024-01-15\"\ntitle: \"Chocolate Chip Cookies\"\ndescription: \"Classic homemade chocolate chip cookies\"\nservings: 24\nprep_time: \"15m\"\ncook_time: \"12m\"\ntotal_time: \"27m\"\ningredients:\n  - name: \"All-purpose flour\"\n    amount: 2.25\n    unit: \"cups\"\n  - name: \"Chocolate chips\"\n    amount: 2\n    unit: \"cups\"\ninstructions:\n  - step: 1\n    description: \"Preheat oven to 375°F\"\n  - step: 2\n    description: \"Mix ingredients and bake for 12 minutes\"\nisRecipe: true"
                                            }]
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupGoogleDriveMocks() {
        stubFor(get(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("mimeType='application/vnd.google-apps.folder'"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "files": [{
                                        "id": "folder-123",
                                        "name": "kukbuk",
                                        "mimeType": "application/vnd.google-apps.folder"
                                    }]
                                }
                                """)));

        stubFor(get(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("in parents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "files": []
                                }
                                """)));

        stubFor(post(urlPathEqualTo("/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "file-456",
                                    "name": "chocolate-chip-cookies.yaml",
                                    "webViewLink": "https://drive.google.com/file/d/file-456/view"
                                }
                                """)));

        stubFor(patch(urlPathMatching("/files/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "file-456",
                                    "name": "chocolate-chip-cookies.yaml",
                                    "webViewLink": "https://drive.google.com/file/d/file-456/view"
                                }
                                """)));
    }

    @Test
    void testPostRecipe_whenAlreadyInStore_isReturnedFromStore() throws Exception {
        // Prepare request
        String sampleHtml = "<html><body><h1>Chocolate Chip Cookies</h1></body></html>";
        Request request = new Request(sampleHtml, "Chocolate Chip Cookies", "https://example.com/recipe");

        // Mock recipe store to return a recipe
        Recipe storedRecipe = Recipe.builder()
                .contentHash("hash")
                .sourceUrl("https://example.com/recipe")
                .recipeYaml("yaml")
                .isValid(true)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        doReturn(Optional.of(storedRecipe)).when(recipeStoreService).findStoredRecipeByHash(anyString());

        // Set up headers with auth token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S-AUTH-TOKEN", "valid-token");
        headers.set("Content-Type", "application/json");

        HttpEntity<Request> entity = new HttpEntity<>(request, headers);

        // Make the request
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                RecipeResponse.class
        );

        // Verify the response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        RecipeResponse recipeResponse = response.getBody();
        assertThat(recipeResponse)
                .extracting(
                        RecipeResponse::url,
                        RecipeResponse::title,
                        RecipeResponse::isRecipe,
                        RecipeResponse::driveFileId,
                        RecipeResponse::driveFileUrl
                )
                .containsExactly(
                        "https://example.com/recipe",
                        "Chocolate Chip Cookies",
                        true,
                        "file-456",
                        "https://drive.google.com/file/d/file-456/view"
                );

        // Verify that Gemini and Google Drive were NOT called
        WireMock.verify(0, postRequestedFor(urlPathMatching("/models/gemini-2.0-flash:generateContent.*")));
        WireMock.verify(1, postRequestedFor(urlPathEqualTo("/files")));
        WireMock.verify(1, patchRequestedFor(urlPathMatching("/files/.*")));
    }

    @Test
    void testPostRecipe_whenNotARecipe_isNotStored() {
        // Mock Gemini to return not a recipe
        doReturn(new Transformer.Response(false, null)).when(transformer).transform(anyString());

        // Prepare request
        String sampleHtml = "<html><body><h1>Not a recipe</h1></body></html>";
        Request request = new Request(sampleHtml, "Not a recipe", "https://example.com/not-a-recipe");

        // Set up headers with auth token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S-AUTH-TOKEN", "valid-token");
        headers.set("Content-Type", "application/json");

        HttpEntity<Request> entity = new HttpEntity<>(request, headers);

        // Make the request
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                RecipeResponse.class
        );

        // Verify the response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isRecipe()).isFalse();

        // Verify that recipe was not stored
        verify(recipeStoreService).storeInvalidRecipe(anyString(), anyString());
        WireMock.verify(0, postRequestedFor(urlPathEqualTo("/files")));
        WireMock.verify(0, patchRequestedFor(urlPathMatching("/files/.*")));
    }
}
