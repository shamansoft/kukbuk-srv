package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.model.StoredRecipe;
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
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );

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

        // Configure Firestore to use emulator
        registry.add("recipe.store.enabled", () -> "true");
        registry.add("firestore.enabled", () -> "true");
        registry.add("google.cloud.project-id", () -> "test-project");

        // Point Firestore SDK to emulator via environment variable
        String emulatorHost = firestoreEmulator.getEmulatorEndpoint();
        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);
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
                                                "text": "metadata:\n  title: \"Chocolate Chip Cookies\"\n  source: \"https://example.com/recipe\"\n  date_created: \"2024-01-15\"\n  servings: 24\n  prep_time: \"15m\"\n  cook_time: \"12m\"\n  total_time: \"27m\"\ndescription: \"Classic homemade chocolate chip cookies\"\ningredients:\n  - item: \"All-purpose flour\"\n    amount: 2.25\n    unit: \"cups\"\n  - item: \"Chocolate chips\"\n    amount: 2\n    unit: \"cups\"\ninstructions:\n  - step: 1\n    description: \"Preheat oven to 375°F\"\n  - step: 2\n    description: \"Mix ingredients and bake for 12 minutes\"\nschema_version: \"1.0.0\"\nrecipe_version: \"1.0.0\""
                                            }]
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupGoogleDriveMocks() {
        // Folder search stub - use priority 1 to ensure it matches before the file search stub
        stubFor(get(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("mimeType='application/vnd.google-apps.folder'"))
                .atPriority(1)
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
        StoredRecipe storedRecipe = StoredRecipe.builder()
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
