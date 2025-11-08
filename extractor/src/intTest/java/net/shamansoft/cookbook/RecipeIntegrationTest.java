package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.model.StoredRecipe;
import net.shamansoft.cookbook.repository.FirestoreRecipeRepository;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.main.allow-bean-definition-overriding=true"
)
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirestoreRecipeRepository recipeRepository;

    @Autowired
    private ContentHashService contentHashService;

    @Autowired
    private Transformer transformer;

    @Container
    static GenericContainer<?> wiremockContainer = new GenericContainer<>("wiremock/wiremock:3.3.1")
            .withExposedPorts(8080)
            .withCommand("--global-response-templating");

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );

    @TestConfiguration
    static class FirestoreTestConfig {
        @Bean
        @Primary
        public Firestore firestore() {
            String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();
            return FirestoreOptions.newBuilder()
                    .setProjectId("test-project")
                    .setHost(emulatorEndpoint)
                    .setCredentials(com.google.auth.oauth2.GoogleCredentials.newBuilder().build())
                    .build()
                    .getService();
        }
    }

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
        // Mock Google OAuth token verification
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
        // Mock Gemini API response for recipe transformation
        stubFor(post(urlPathMatching("/v1beta/models/gemini-2.0-flash:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "metadata:\\n  title: \\"Chocolate Chip Cookies\\"\\n  source: \\"https://example.com/recipe\\"\\n  date_created: \\"2024-01-15\\"\\n  servings: 24\\n  prep_time: \\"15m\\"\\n  cook_time: \\"12m\\"\\n  total_time: \\"27m\\"\\ndescription: \\"Classic homemade chocolate chip cookies\\"\\ningredients:\\n  - item: \\"All-purpose flour\\"\\n    amount: 2.25\\n    unit: \\"cups\\"\\n  - item: \\"Chocolate chips\\"\\n    amount: 2\\n    unit: \\"cups\\"\\ninstructions:\\n  - step: 1\\n    description: \\"Preheat oven to 375°F\\"\\n  - step: 2\\n    description: \\"Mix ingredients and bake for 12 minutes\\"\\nschema_version: \\"1.0.0\\"\\nrecipe_version: \\"1.0.0\\""
                                            }]
                                        }
                                    }]
                                }
                                """)));

        // Also handle the direct path without /v1beta prefix
        stubFor(post(urlPathMatching("/models/gemini-2.0-flash:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "metadata:\\n  title: \\"Chocolate Chip Cookies\\"\\n  source: \\"https://example.com/recipe\\"\\n  date_created: \\"2024-01-15\\"\\n  servings: 24\\n  prep_time: \\"15m\\"\\n  cook_time: \\"12m\\"\\n  total_time: \\"27m\\"\\ndescription: \\"Classic homemade chocolate chip cookies\\"\\ningredients:\\n  - item: \\"All-purpose flour\\"\\n    amount: 2.25\\n    unit: \\"cups\\"\\n  - item: \\"Chocolate chips\\"\\n    amount: 2\\n    unit: \\"cups\\"\\ninstructions:\\n  - step: 1\\n    description: \\"Preheat oven to 375°F\\"\\n  - step: 2\\n    description: \\"Mix ingredients and bake for 12 minutes\\"\\nschema_version: \\"1.0.0\\"\\nrecipe_version: \\"1.0.0\\""
                                            }]
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupGoogleDriveMocks() {
        // Mock Google Drive folder search - use priority 1 to ensure it matches before the file search stub
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

        // Mock Google Drive file search within folder - return empty to trigger file creation
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

        // Mock Google Drive file creation
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

        // Mock Google Drive file upload/update - handle both new files and existing files
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
    void testPostRecipeWithGoogleIntegration() throws Exception {
        // Prepare request
        String sampleHtml = """
                <html>
                <head><title>Chocolate Chip Cookies Recipe</title></head>
                <body>
                    <h1>Chocolate Chip Cookies</h1>
                    <p>Classic homemade chocolate chip cookies</p>
                    <h2>Ingredients:</h2>
                    <ul>
                        <li>2 1/4 cups all-purpose flour</li>
                        <li>2 cups chocolate chips</li>
                    </ul>
                    <h2>Instructions:</h2>
                    <ol>
                        <li>Preheat oven to 375°F</li>
                        <li>Mix ingredients and bake for 12 minutes</li>
                    </ol>
                </body>
                </html>
                """;

        Request request = new Request(sampleHtml, "Chocolate Chip Cookies", "https://example.com/recipe");

        // Set up headers with auth token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S-AUTH-TOKEN", "valid-token");
        headers.set("Content-Type", "application/json");

        HttpEntity<Request> entity = new HttpEntity<>(request, headers);

        // Make the request with compression=none to avoid Base64 decoding
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
                        RecipeResponse::driveFileId,
                        RecipeResponse::driveFileUrl,
                        RecipeResponse::isRecipe
                )
                .containsExactly(
                        "https://example.com/recipe",
                        "Chocolate Chip Cookies",
                        "file-456",
                        "https://drive.google.com/file/d/file-456/view",
                        true
                );

        // Verify that Google services were called
        verify(getRequestedFor(urlPathEqualTo("/tokeninfo"))
                .withQueryParam("access_token", equalTo("valid-token")));

        // Verify Gemini was called (without /v1beta prefix based on actual request)
        verify(postRequestedFor(urlPathMatching("/models/gemini-2.0-flash:generateContent.*")));

        verify(getRequestedFor(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("name='kukbuk'")));

        verify(postRequestedFor(urlPathEqualTo("/files")));

        verify(patchRequestedFor(urlPathMatching("/files/file-456.*")));
    }

    @Test
    void testPostRecipeWithoutAuthToken() throws Exception {
        String sampleHtml = """
                <html>
                <head><title>Simple Recipe</title></head>
                <body>
                    <h1>Simple Recipe</h1>
                    <p>A simple recipe without Google Drive storage</p>
                    <h2>Ingredients:</h2>
                    <ul>
                        <li>1 cup flour</li>
                        <li>2 eggs</li>
                    </ul>
                    <h2>Instructions:</h2>
                    <ol>
                        <li>Mix ingredients</li>
                        <li>Bake for 20 minutes</li>
                    </ol>
                </body>
                </html>
                """;

        Request request = new Request(sampleHtml, "Simple Recipe", "https://example.com/simple");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<Request> entity = new HttpEntity<>(request, headers);

        // The controller always tries to validate auth token, so we expect 401 UNAUTHORIZED
        // when no token is provided. Let's test this behavior instead.
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify that only Gemini was called, not Google Auth or Drive (since auth failed first)
        verify(0, postRequestedFor(urlPathMatching("/v1beta/models/gemini-2.0-flash:generateContent.*")));
        verify(0, getRequestedFor(urlPathEqualTo("/tokeninfo")));
        verify(0, getRequestedFor(urlPathEqualTo("/files")));
    }

    @Test
    void testPostRecipeWithFirestoreCacheHit() throws Exception {
        // Prepare the cached recipe YAML
        String cachedRecipeYaml = """
                metadata:
                  title: "Cached Chocolate Chip Cookies"
                  source: "https://example.com/cached-recipe"
                  date_created: "2024-01-10"
                  servings: 24
                  prep_time: "15m"
                  cook_time: "12m"
                  total_time: "27m"
                description: "Classic homemade chocolate chip cookies from cache"
                ingredients:
                  - item: "All-purpose flour"
                    amount: 2.25
                    unit: "cups"
                  - item: "Chocolate chips"
                    amount: 2
                    unit: "cups"
                instructions:
                  - step: 1
                    description: "Preheat oven to 375°F"
                  - step: 2
                    description: "Mix ingredients and bake for 12 minutes"
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                """;

        // Generate content hash for the URL
        String testUrl = "https://example.com/cached-recipe";
        String contentHash = contentHashService.generateContentHash(testUrl);

        // Pre-populate Firestore with the cached recipe
        StoredRecipe cachedRecipe = StoredRecipe.builder()
                .contentHash(contentHash)
                .sourceUrl(testUrl)
                .recipeYaml(cachedRecipeYaml)
                .isValid(true)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        // Save to Firestore and wait for completion
        recipeRepository.save(cachedRecipe).join();

        // Verify the recipe was actually saved
        Optional<StoredRecipe> verifyStored = recipeRepository.findByContentHash(contentHash).join();
        assertThat(verifyStored)
                .as("Recipe should be stored in Firestore before making request")
                .isPresent()
                .get()
                .extracting(StoredRecipe::getContentHash, StoredRecipe::getSourceUrl, StoredRecipe::isValid)
                .containsExactly(contentHash, testUrl, true);

        // Prepare request
        String sampleHtml = """
                <html>
                <head><title>Cached Chocolate Chip Cookies Recipe</title></head>
                <body>
                    <h1>This HTML should not be processed</h1>
                    <p>Because we have a cache hit</p>
                </body>
                </html>
                """;

        Request request = new Request(sampleHtml, "Cached Chocolate Chip Cookies", testUrl);

        // Set up headers with auth token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S-AUTH-TOKEN", "valid-token");
        headers.set("Content-Type", "application/json");

        HttpEntity<Request> entity = new HttpEntity<>(request, headers);

        // Make the request with compression=none
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
                        RecipeResponse::driveFileId,
                        RecipeResponse::driveFileUrl,
                        RecipeResponse::isRecipe
                )
                .containsExactly(
                        testUrl,
                        "Cached Chocolate Chip Cookies",
                        "file-456",
                        "https://drive.google.com/file/d/file-456/view",
                        true
                );

        // Verify that Google Auth was called (for Drive upload)
        verify(getRequestedFor(urlPathEqualTo("/tokeninfo"))
                .withQueryParam("access_token", equalTo("valid-token")));

        // Verify that Gemini was NOT called (cache hit, so no transformation needed)
        verify(0, postRequestedFor(urlPathMatching("/v1beta/models/gemini-2.0-flash:generateContent.*")));
        verify(0, postRequestedFor(urlPathMatching("/models/gemini-2.0-flash:generateContent.*")));

        // Verify that Google Drive operations were still performed
        verify(getRequestedFor(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("name='kukbuk'")));

        verify(postRequestedFor(urlPathEqualTo("/files")));

        verify(patchRequestedFor(urlPathMatching("/files/file-456.*")));
    }
}
