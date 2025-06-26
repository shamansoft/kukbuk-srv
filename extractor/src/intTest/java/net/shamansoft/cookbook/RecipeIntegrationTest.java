package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
                                            "text": "schema_version: \\"1.0.0\\"\\nrecipe_version: \\"1.0\\"\\ndate_created: \\"2024-01-15\\"\\ntitle: \\"Chocolate Chip Cookies\\"\\ndescription: \\"Classic homemade chocolate chip cookies\\"\\nservings: 24\\nprep_time: \\"15m\\"\\ncook_time: \\"12m\\"\\ntotal_time: \\"27m\\"\\ningredients:\\n  - name: \\"All-purpose flour\\"\\n    amount: 2.25\\n    unit: \\"cups\\"\\n  - name: \\"Chocolate chips\\"\\n    amount: 2\\n    unit: \\"cups\\"\\ninstructions:\\n  - step: 1\\n    description: \\"Preheat oven to 375°F\\"\\n  - step: 2\\n    description: \\"Mix ingredients and bake for 12 minutes\\"\\nisRecipe: true"
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
                                            "text": "schema_version: \\"1.0.0\\"\\nrecipe_version: \\"1.0\\"\\ndate_created: \\"2024-01-15\\"\\ntitle: \\"Chocolate Chip Cookies\\"\\ndescription: \\"Classic homemade chocolate chip cookies\\"\\nservings: 24\\nprep_time: \\"15m\\"\\ncook_time: \\"12m\\"\\ntotal_time: \\"27m\\"\\ningredients:\\n  - name: \\"All-purpose flour\\"\\n    amount: 2.25\\n    unit: \\"cups\\"\\n  - name: \\"Chocolate chips\\"\\n    amount: 2\\n    unit: \\"cups\\"\\ninstructions:\\n  - step: 1\\n    description: \\"Preheat oven to 375°F\\"\\n  - step: 2\\n    description: \\"Mix ingredients and bake for 12 minutes\\"\\nisRecipe: true"
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
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        RecipeResponse recipeResponse = response.getBody();
        assertEquals("https://example.com/recipe", recipeResponse.url());
        assertEquals("Chocolate Chip Cookies", recipeResponse.title());
        assertEquals("file-456", recipeResponse.driveFileId());
        assertEquals("https://drive.google.com/file/d/file-456/view", recipeResponse.driveFileUrl());
        assertTrue(recipeResponse.isRecipe());

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

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        // Verify that only Gemini was called, not Google Auth or Drive (since auth failed first)
        verify(0, postRequestedFor(urlPathMatching("/v1beta/models/gemini-2.0-flash:generateContent.*")));
        verify(0, getRequestedFor(urlPathEqualTo("/tokeninfo")));
        verify(0, getRequestedFor(urlPathEqualTo("/files")));
    }
}