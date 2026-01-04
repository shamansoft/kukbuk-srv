package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.repository.FirestoreRecipeRepository;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import net.shamansoft.cookbook.service.ContentHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the /recipe endpoint using the new StorageService flow.
 * This tests the complete flow where:
 * 1. User authenticates with Firebase ID token
 * 2. Backend retrieves Google Drive OAuth tokens from Firestore via StorageService
 * 3. Backend uses those tokens to save recipes to Drive
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
class AddRecipeIT {

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

        @Bean
        @Primary
        public com.google.firebase.auth.FirebaseAuth firebaseAuth() throws Exception {
            com.google.firebase.auth.FirebaseAuth mockAuth = org.mockito.Mockito.mock(com.google.firebase.auth.FirebaseAuth.class);
            com.google.firebase.auth.FirebaseToken mockToken = org.mockito.Mockito.mock(com.google.firebase.auth.FirebaseToken.class);
            org.mockito.Mockito.when(mockToken.getUid()).thenReturn("test-user-123");
            org.mockito.Mockito.when(mockToken.getEmail()).thenReturn("testuser@example.com");
            org.mockito.Mockito.when(mockAuth.verifyIdToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockToken);
            return mockAuth;
        }

        @Bean
        @Primary
        public TokenEncryptionService tokenEncryptionService() throws Exception {
            TokenEncryptionService mockService = org.mockito.Mockito.mock(TokenEncryptionService.class);
            // Mock encrypt: return "encrypted-" + input
            org.mockito.Mockito.when(mockService.encrypt(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
            // Mock decrypt: remove "encrypted-" prefix
            org.mockito.Mockito.when(mockService.decrypt(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> {
                        String encrypted = invocation.getArgument(0);
                        return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
                    });
            return mockService;
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
        registry.add("cookbook.google.oauth-secret", () -> "test-client-secret");
        registry.add("cookbook.gemini.api-key", () -> "test-api-key");

        // Configure Firestore to use emulator
        registry.add("recipe.store.enabled", () -> "true");
        registry.add("firestore.enabled", () -> "true");
        registry.add("google.cloud.project-id", () -> "test-project");

        // Disable Firebase (use mock instead)
        registry.add("firebase.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() throws Exception {
        WireMock.configureFor("localhost", wiremockContainer.getMappedPort(8080));
        WireMock.reset();

        // Clear Firestore data between tests
        clearFirestore();

        setupGeminiMock();
        setupGoogleDriveMocks();
    }

    private void clearFirestore() throws Exception {
        // Delete the test user document if it exists
        try {
            firestore.collection("users").document("test-user-123").delete().get();
        } catch (Exception e) {
            // Document might not exist, that's OK
        }
    }

    private void setupGeminiMock() {
        // Mock Gemini API response for recipe transformation
        stubFor(post(urlPathMatching("/models/gemini-2.5-flash:generateContent.*"))
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
        // Mock Google Drive folder search
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

        // Mock Google Drive file search within folder
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

        // Mock Google Drive file upload/update
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

    private void setupStorageInfoInFirestore(String userId, String accessToken) throws Exception {
        Timestamp futureTime = Timestamp.ofTimeSecondsAndNanos(
                System.currentTimeMillis() / 1000 + 3600, 0
        );
        Timestamp nowTime = Timestamp.now();

        // Store encrypted tokens (TokenEncryptionService mock adds "encrypted-" prefix)
        firestore.collection("users").document(userId).set(
                Map.of(
                        "userId", userId,
                        "email", "testuser@example.com",
                        "storage", Map.of(
                                "type", "googleDrive",
                                "connected", true,
                                "accessToken", "encrypted-" + accessToken,
                                "refreshToken", "encrypted-refresh-token-123",
                                "expiresAt", futureTime,
                                "connectedAt", nowTime,
                                "defaultFolderId", "folder-123"
                        )
                )
        ).get();
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-firebase-token");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @Test
    @DisplayName("Should successfully create recipe when storage is configured")
    void shouldCreateRecipeWithStorageConfigured() throws Exception {
        // Given: User has Google Drive storage configured
        setupStorageInfoInFirestore("test-user-123", "valid-drive-token");

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
        HttpEntity<Request> entity = new HttpEntity<>(request, createAuthHeaders());

        // When: Making a request to create recipe
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                RecipeResponse.class
        );

        // Then: Recipe is created successfully
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

        // Verify Gemini was called for transformation
        verify(postRequestedFor(urlPathMatching("/models/gemini-2.5-flash:generateContent.*")));

        // Verify Google Drive operations
        verify(getRequestedFor(urlPathEqualTo("/files"))
                .withQueryParam("q", containing("name='kukbuk'")));
        verify(postRequestedFor(urlPathEqualTo("/files")));
        verify(patchRequestedFor(urlPathMatching("/files/file-456.*")));
    }

    @Test
    @DisplayName("Should return HTTP 428 when storage not configured")
    void shouldReturn428WhenStorageNotConfigured() throws Exception {
        // Given: User has NO Google Drive storage configured (no document in Firestore)
        // Explicitly ensure no user document exists
        firestore.collection("users").document("test-user-123").delete().get();

        // Verify no document exists
        com.google.cloud.firestore.DocumentSnapshot doc = firestore.collection("users")
                .document("test-user-123")
                .get()
                .get();
        assertThat(doc.exists()).isFalse();

        String sampleHtml = """
                <html>
                <body>
                    <h1>Test Recipe</h1>
                    <p>A test recipe</p>
                </body>
                </html>
                """;

        Request request = new Request(sampleHtml, "Test Recipe", "https://example.com/test");
        HttpEntity<Request> entity = new HttpEntity<>(request, createAuthHeaders());

        // When: Making a request to create recipe
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                Map.class
        );

        // Then: Returns HTTP 428 Precondition Required
        assertThat(response.getStatusCode().value()).isEqualTo(428);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).asString().isEqualTo("Storage Not Connected");

        // Verify that Gemini and Drive were NOT called
        verify(0, postRequestedFor(urlPathMatching("/models/gemini-2.5-flash:generateContent.*")));
        verify(0, getRequestedFor(urlPathEqualTo("/files")));
    }

    @Test
    @DisplayName("Should use cached recipe when available")
    void shouldUseCachedRecipeWhenAvailable() throws Exception {
        // Given: User has storage configured
        setupStorageInfoInFirestore("test-user-123", "valid-drive-token");

        // AND: Recipe is already cached in Firestore
        String testUrl = "https://example.com/cached-recipe";
        String contentHash = contentHashService.generateContentHash(testUrl);

        String cachedRecipeYaml = """
                metadata:
                  title: "Cached Recipe"
                  source: "https://example.com/cached-recipe"
                  servings: 4
                description: "A cached recipe"
                ingredients:
                  - item: "Flour"
                    amount: 2
                    unit: "cups"
                instructions:
                  - step: 1
                    description: "Mix and bake"
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                """;

        StoredRecipe cachedRecipe = StoredRecipe.builder()
                .contentHash(contentHash)
                .sourceUrl(testUrl)
                .recipeYaml(cachedRecipeYaml)
                .isValid(true)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        recipeRepository.save(cachedRecipe).join();

        // Verify it was saved
        Optional<StoredRecipe> verifyStored = recipeRepository.findByContentHash(contentHash).join();
        assertThat(verifyStored).isPresent();

        String sampleHtml = "<html><body><h1>This should not be processed</h1></body></html>";
        Request request = new Request(sampleHtml, "Cached Recipe", testUrl);
        HttpEntity<Request> entity = new HttpEntity<>(request, createAuthHeaders());

        // When: Making a request for the cached recipe
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                RecipeResponse.class
        );

        // Then: Recipe is returned from cache
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isRecipe()).isTrue();
        assertThat(response.getBody().url()).isEqualTo(testUrl);

        // Verify Gemini was NOT called (cache hit)
        verify(0, postRequestedFor(urlPathMatching("/models/gemini-2.5-flash:generateContent.*")));

        // Verify Drive was still called to upload
        verify(postRequestedFor(urlPathEqualTo("/files")));
    }

    @Test
    @DisplayName("Should handle not-a-recipe content correctly")
    void shouldHandleNotARecipeContent() throws Exception {
        // Given: User has storage configured
        setupStorageInfoInFirestore("test-user-123", "valid-drive-token");

        // AND: Gemini returns isRecipe=false
        stubFor(post(urlPathMatching("/models/gemini-2.5-flash:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "This is not a recipe content"
                                            }]
                                        }
                                    }]
                                }
                                """)));

        String sampleHtml = "<html><body><h1>Not a recipe</h1><p>Just some random content</p></body></html>";
        Request request = new Request(sampleHtml, "Not a Recipe", "https://example.com/not-recipe");
        HttpEntity<Request> entity = new HttpEntity<>(request, createAuthHeaders());

        // When: Making a request
        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/recipe?compression=none",
                entity,
                RecipeResponse.class
        );

        // Then: Returns success but isRecipe=false
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isRecipe()).isFalse();
        assertThat(response.getBody().driveFileId()).isNull();
        assertThat(response.getBody().driveFileUrl()).isNull();

        // Verify Gemini was called but Drive was NOT
        verify(postRequestedFor(urlPathMatching("/models/gemini-2.5-flash:generateContent.*")));
        verify(0, postRequestedFor(urlPathEqualTo("/files")));
    }
}
