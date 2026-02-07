package net.shamansoft.cookbook;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import net.shamansoft.cookbook.dto.YouTubeJobStatusResponse;
import net.shamansoft.cookbook.dto.YouTubeRecipeRequest;
import net.shamansoft.cookbook.dto.YouTubeRecipeResponse;
import net.shamansoft.cookbook.dto.YouTubeTaskRequest;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.repository.YouTubeJob;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import net.shamansoft.cookbook.service.CloudTasksService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Integration tests for YouTube recipe extraction flow.
 * Tests the full lifecycle: create job, process via internal handler, check status.
 *
 * Key components tested:
 * - YouTubeController (POST /v1/recipes/youtube, GET /v1/recipes/youtube/{jobId})
 * - YouTubeTaskHandler (POST /internal/youtube-tasks/process)
 * - FirestoreYouTubeJobRepository (job persistence and status transitions)
 * - YouTubeTransformer (metadata + transcript + Gemini integration)
 * - DriveService (recipe upload to Google Drive)
 *
 * CloudTasksService is mocked (no real Cloud Tasks queue in integration tests).
 * External APIs (YouTube Data, transcript, Gemini, Drive) are mocked via WireMock.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureTestRestTemplate
class YouTubeRecipeIT {

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String TEST_VIDEO_URL = "https://www.youtube.com/watch?v=" + TEST_VIDEO_ID;

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @Container
    static GenericContainer<?> wiremockContainer = new GenericContainer<>("wiremock/wiremock:3.3.1")
            .withExposedPorts(8080)
            .withCommand("--global-response-templating");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirestoreYouTubeJobRepository jobRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!wiremockContainer.isRunning()) {
            wiremockContainer.start();
        }

        String wiremockUrl = "http://localhost:" + wiremockContainer.getMappedPort(8080);

        // Point all external APIs to WireMock
        registry.add("cookbook.gemini.base-url", () -> wiremockUrl);
        registry.add("cookbook.drive.base-url", () -> wiremockUrl);
        registry.add("cookbook.drive.upload-url", () -> wiremockUrl);
        registry.add("cookbook.drive.auth-url", () -> wiremockUrl);
        registry.add("cookbook.youtube.api-url", () -> wiremockUrl);
        registry.add("cookbook.transcript.youtube-transcript-io.api-url", () -> wiremockUrl);
        registry.add("cookbook.transcript.supadata.api-url", () -> wiremockUrl);

        registry.add("cookbook.google.oauth-id", () -> "test-client-id");
        registry.add("cookbook.google.oauth-secret", () -> "test-client-secret");
        registry.add("cookbook.gemini.api-key", () -> "test-api-key");

        // Configure Firestore emulator
        registry.add("firestore.enabled", () -> "true");
        registry.add("google.cloud.project-id", () -> "test-project");
        registry.add("recipe.store.enabled", () -> "true");

        // Disable Firebase (use mock)
        registry.add("firebase.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() throws Exception {
        WireMock.configureFor("localhost", wiremockContainer.getMappedPort(8080));
        WireMock.reset();
        clearFirestore();
    }

    private void clearFirestore() throws Exception {
        try {
            Thread.sleep(500);
            // Clear youtube_jobs subcollection
            var jobsCollection = firestore.collection("users")
                    .document(TEST_USER_ID)
                    .collection("youtube_jobs");
            var jobsSnapshot = jobsCollection.get().get();
            for (var document : jobsSnapshot.getDocuments()) {
                document.getReference().delete().get();
            }

            // Clear recipes subcollection
            var recipesCollection = firestore.collection("users")
                    .document(TEST_USER_ID)
                    .collection("recipes");
            var recipesSnapshot = recipesCollection.get().get();
            for (var document : recipesSnapshot.getDocuments()) {
                document.getReference().delete().get();
            }

            // Delete the user document
            firestore.collection("users").document(TEST_USER_ID).delete().get();
        } catch (Exception e) {
            // Don't fail setup
        }
    }

    // ---- WireMock stub setup helpers ----

    private void setupYouTubeMetadataMock() {
        stubFor(get(urlPathEqualTo("/videos"))
                .withQueryParam("id", equalTo(TEST_VIDEO_ID))
                .withQueryParam("part", equalTo("snippet"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "items": [{
                                        "snippet": {
                                            "title": "Easy Pasta Recipe",
                                            "description": "A simple and delicious pasta recipe. Ingredients: 200g spaghetti, garlic, olive oil.",
                                            "channelTitle": "Cooking Channel",
                                            "publishedAt": "2024-06-15T10:00:00Z"
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupTranscriptMock() {
        stubFor(post(urlPathEqualTo("/api/transcripts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{
                                    "tracks": [{
                                        "transcript": [
                                            {"text": "Today we are making a simple pasta."},
                                            {"text": "First boil 200g of spaghetti."},
                                            {"text": "Then saute garlic in olive oil."},
                                            {"text": "Toss the pasta in the garlic oil."},
                                            {"text": "Serve with parmesan cheese."}
                                        ]
                                    }]
                                }]
                                """)));
    }

    private void setupGeminiRecipeMock() {
        stubFor(post(urlPathMatching("/models/gemini-2.5-flash-lite:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "{\\"is_recipe\\": true, \\"metadata\\": {\\"title\\": \\"Easy Pasta\\", \\"source\\": \\"https://youtube.com/watch?v=dQw4w9WgXcQ\\", \\"date_created\\": \\"2024-06-15\\", \\"servings\\": 2, \\"prep_time\\": \\"5m\\", \\"cook_time\\": \\"15m\\", \\"total_time\\": \\"20m\\"}, \\"description\\": \\"A simple and delicious pasta recipe\\", \\"ingredients\\": [{\\"item\\": \\"Spaghetti\\", \\"amount\\": 200, \\"unit\\": \\"g\\"}, {\\"item\\": \\"Garlic\\", \\"amount\\": 3, \\"unit\\": \\"cloves\\"}, {\\"item\\": \\"Olive oil\\", \\"amount\\": 2, \\"unit\\": \\"tbsp\\"}], \\"instructions\\": [{\\"step\\": 1, \\"description\\": \\"Boil 200g of spaghetti\\"}, {\\"step\\": 2, \\"description\\": \\"Saute garlic in olive oil\\"}, {\\"step\\": 3, \\"description\\": \\"Toss the pasta in garlic oil and serve\\"}], \\"schema_version\\": \\"1.0.0\\", \\"recipe_version\\": \\"1.0.0\\"}"
                                            }]
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupGeminiNotRecipeMock() {
        stubFor(post(urlPathMatching("/models/gemini-2.5-flash-lite:generateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "candidates": [{
                                        "content": {
                                            "parts": [{
                                                "text": "{\\"is_recipe\\": false, \\"metadata\\": {\\"title\\": \\"Not a Recipe\\", \\"source\\": \\"https://youtube.com/test\\", \\"date_created\\": \\"2024-01-01\\", \\"servings\\": 1}, \\"ingredients\\": [{\\"item\\": \\"placeholder\\"}], \\"instructions\\": [{\\"step\\": 1, \\"description\\": \\"placeholder\\"}], \\"schema_version\\": \\"1.0.0\\", \\"recipe_version\\": \\"1.0.0\\"}"
                                            }]
                                        }
                                    }]
                                }
                                """)));
    }

    private void setupGoogleDriveMocks() {
        setupStorageInfoInFirestore();

        // Folder search
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

        // File search within folder
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

        // File creation
        stubFor(post(urlPathEqualTo("/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "file-789",
                                    "name": "easy-pasta.yaml",
                                    "webViewLink": "https://drive.google.com/file/d/file-789/view"
                                }
                                """)));

        // File upload/update
        stubFor(patch(urlPathMatching("/files/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "file-789",
                                    "name": "easy-pasta.yaml",
                                    "webViewLink": "https://drive.google.com/file/d/file-789/view"
                                }
                                """)));
    }

    private void setupStorageInfoInFirestore() {
        try {
            com.google.cloud.Timestamp futureTime = com.google.cloud.Timestamp.ofTimeSecondsAndNanos(
                    System.currentTimeMillis() / 1000 + 3600, 0);
            com.google.cloud.Timestamp nowTime = com.google.cloud.Timestamp.now();

            firestore.collection("users").document(TEST_USER_ID).set(
                    Map.of(
                            "userId", TEST_USER_ID,
                            "email", "testuser@example.com",
                            "storage", Map.of(
                                    "type", "googleDrive",
                                    "connected", true,
                                    "accessToken", "encrypted-valid-drive-token",
                                    "refreshToken", "encrypted-refresh-token-123",
                                    "expiresAt", futureTime,
                                    "connectedAt", nowTime,
                                    "folderId", "folder-123")))
                    .get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup storage info", e);
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-firebase-token");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders createCloudTaskHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-CloudTasks-TaskName", "test-task-name");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    // ---- Tests ----

    @Test
    @DisplayName("Should create YouTube recipe job and return PENDING status")
    void shouldCreateYouTubeJob() {
        YouTubeRecipeRequest request = new YouTubeRecipeRequest(TEST_VIDEO_URL);
        HttpEntity<YouTubeRecipeRequest> entity = new HttpEntity<>(request, createAuthHeaders());

        ResponseEntity<YouTubeRecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/recipes/youtube",
                entity,
                YouTubeRecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isNotNull().isNotEmpty();
        assertThat(response.getBody().status()).isEqualTo("PENDING");

        // Verify job was saved to Firestore
        var savedJob = jobRepository.findById(TEST_USER_ID, response.getBody().jobId()).join();
        assertThat(savedJob).isPresent();
        assertThat(savedJob.get().status()).isEqualTo(JobStatus.PENDING);
        assertThat(savedJob.get().videoUrl()).isEqualTo(TEST_VIDEO_URL);
    }

    @Test
    @DisplayName("Should reject invalid YouTube URL with bad request")
    void shouldRejectInvalidYouTubeUrl() {
        YouTubeRecipeRequest request = new YouTubeRecipeRequest("https://example.com/not-youtube");
        HttpEntity<YouTubeRecipeRequest> entity = new HttpEntity<>(request, createAuthHeaders());

        ResponseEntity<YouTubeRecipeResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/recipes/youtube",
                entity,
                YouTubeRecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("INVALID_URL");
    }

    @Test
    @DisplayName("Should process YouTube task and update job to COMPLETED with Drive file")
    void shouldProcessYouTubeTaskSuccessfully() throws Exception {
        setupYouTubeMetadataMock();
        setupTranscriptMock();
        setupGeminiRecipeMock();
        setupGoogleDriveMocks();

        // Pre-create a job in PENDING state
        String jobId = "test-job-" + System.currentTimeMillis();
        YouTubeJob pendingJob = new YouTubeJob(
                jobId, TEST_USER_ID, TEST_VIDEO_URL,
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );
        jobRepository.save(pendingJob).join();

        // Simulate Cloud Task callback
        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest(jobId, TEST_USER_ID, TEST_VIDEO_URL);
        HttpEntity<YouTubeTaskRequest> entity = new HttpEntity<>(taskRequest, createCloudTaskHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                entity,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "processed");

        // Verify job was updated to COMPLETED
        var completedJob = jobRepository.findById(TEST_USER_ID, jobId).join();
        assertThat(completedJob).isPresent();
        assertThat(completedJob.get().status()).isEqualTo(JobStatus.COMPLETED);

        // Verify YouTube metadata API was called
        verify(getRequestedFor(urlPathEqualTo("/videos"))
                .withQueryParam("id", equalTo(TEST_VIDEO_ID)));

        // Verify transcript API was called
        verify(postRequestedFor(urlPathEqualTo("/api/transcripts")));

        // Verify Gemini was called
        verify(postRequestedFor(urlPathMatching("/models/gemini-2.5-flash-lite:generateContent.*")));

        // Verify Drive upload happened
        verify(postRequestedFor(urlPathEqualTo("/files")));
    }

    @Test
    @DisplayName("Should handle not-a-recipe YouTube video")
    void shouldHandleNotRecipeVideo() throws Exception {
        setupYouTubeMetadataMock();
        setupTranscriptMock();
        setupGeminiNotRecipeMock();

        // Pre-create a job in PENDING state
        String jobId = "test-job-notrecipe-" + System.currentTimeMillis();
        YouTubeJob pendingJob = new YouTubeJob(
                jobId, TEST_USER_ID, TEST_VIDEO_URL,
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );
        jobRepository.save(pendingJob).join();

        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest(jobId, TEST_USER_ID, TEST_VIDEO_URL);
        HttpEntity<YouTubeTaskRequest> entity = new HttpEntity<>(taskRequest, createCloudTaskHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                entity,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify job was updated to COMPLETED (no recipe, but still completed)
        var completedJob = jobRepository.findById(TEST_USER_ID, jobId).join();
        assertThat(completedJob).isPresent();
        assertThat(completedJob.get().status()).isEqualTo(JobStatus.COMPLETED);

        // Verify Drive was NOT called (not a recipe)
        verify(0, postRequestedFor(urlPathEqualTo("/files")));
    }

    @Test
    @DisplayName("Should update job to FAILED when YouTube API returns error")
    void shouldFailJobWhenYouTubeApiErrors() throws Exception {
        // YouTube metadata API returns 404 (video not found)
        stubFor(get(urlPathEqualTo("/videos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "items": []
                                }
                                """)));

        String jobId = "test-job-fail-" + System.currentTimeMillis();
        YouTubeJob pendingJob = new YouTubeJob(
                jobId, TEST_USER_ID, TEST_VIDEO_URL,
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );
        jobRepository.save(pendingJob).join();

        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest(jobId, TEST_USER_ID, TEST_VIDEO_URL);
        HttpEntity<YouTubeTaskRequest> entity = new HttpEntity<>(taskRequest, createCloudTaskHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                entity,
                Map.class);

        // Handler always returns 200 to Cloud Tasks
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // But job should be FAILED
        var failedJob = jobRepository.findById(TEST_USER_ID, jobId).join();
        assertThat(failedJob).isPresent();
        assertThat(failedJob.get().status()).isEqualTo(JobStatus.FAILED);
        assertThat(failedJob.get().errorMessage()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Should reject internal task request without Cloud Tasks header")
    void shouldRejectTaskWithoutCloudTasksHeader() {
        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest("job-1", TEST_USER_ID, TEST_VIDEO_URL);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        // No X-CloudTasks-TaskName header
        HttpEntity<YouTubeTaskRequest> entity = new HttpEntity<>(taskRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                entity,
                Map.class);

        // Missing required header results in error response (500 due to global exception handler)
        assertThat(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError())
                .as("Expected error status code but got: " + response.getStatusCode())
                .isTrue();
    }

    @Test
    @DisplayName("Should return job status PENDING for newly created job")
    void shouldReturnJobStatusPending() throws Exception {
        // Create a job first
        String jobId = "status-test-job";
        YouTubeJob job = new YouTubeJob(
                jobId, TEST_USER_ID, TEST_VIDEO_URL,
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );
        jobRepository.save(job).join();

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<YouTubeJobStatusResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/v1/recipes/youtube/" + jobId,
                HttpMethod.GET,
                entity,
                YouTubeJobStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo(jobId);
        assertThat(response.getBody().status()).isEqualTo("PENDING");
        assertThat(response.getBody().driveFileId()).isNull();
        assertThat(response.getBody().driveFileUrl()).isNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent job")
    void shouldReturn404ForNonExistentJob() {
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<YouTubeJobStatusResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/v1/recipes/youtube/non-existent-job",
                HttpMethod.GET,
                entity,
                YouTubeJobStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Full flow: create job, process it, check status shows COMPLETED")
    void shouldCompleteFullYouTubeRecipeFlow() throws Exception {
        setupYouTubeMetadataMock();
        setupTranscriptMock();
        setupGeminiRecipeMock();
        setupGoogleDriveMocks();

        // Step 1: Create the job via public endpoint
        YouTubeRecipeRequest createRequest = new YouTubeRecipeRequest(TEST_VIDEO_URL);
        HttpEntity<YouTubeRecipeRequest> createEntity = new HttpEntity<>(createRequest, createAuthHeaders());

        ResponseEntity<YouTubeRecipeResponse> createResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/recipes/youtube",
                createEntity,
                YouTubeRecipeResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String jobId = createResponse.getBody().jobId();
        assertThat(jobId).isNotNull();

        // Step 2: Simulate Cloud Task callback to process the job
        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest(jobId, TEST_USER_ID, TEST_VIDEO_URL);
        HttpEntity<YouTubeTaskRequest> taskEntity = new HttpEntity<>(taskRequest, createCloudTaskHeaders());

        ResponseEntity<Map> processResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                taskEntity,
                Map.class);

        assertThat(processResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 3: Check job status via public endpoint
        HttpEntity<Void> statusEntity = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<YouTubeJobStatusResponse> statusResponse = restTemplate.exchange(
                "http://localhost:" + port + "/v1/recipes/youtube/" + jobId,
                HttpMethod.GET,
                statusEntity,
                YouTubeJobStatusResponse.class);

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).isNotNull();
        assertThat(statusResponse.getBody().jobId()).isEqualTo(jobId);
        assertThat(statusResponse.getBody().status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should handle transcript API failure gracefully")
    void shouldHandleTranscriptApiFailure() throws Exception {
        setupYouTubeMetadataMock();

        // Both transcript APIs fail
        stubFor(post(urlPathEqualTo("/api/transcripts"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        stubFor(get(urlPathMatching("/v1/transcript.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        String jobId = "test-job-transcript-fail-" + System.currentTimeMillis();
        YouTubeJob pendingJob = new YouTubeJob(
                jobId, TEST_USER_ID, TEST_VIDEO_URL,
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );
        jobRepository.save(pendingJob).join();

        YouTubeTaskRequest taskRequest = new YouTubeTaskRequest(jobId, TEST_USER_ID, TEST_VIDEO_URL);
        HttpEntity<YouTubeTaskRequest> entity = new HttpEntity<>(taskRequest, createCloudTaskHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/internal/youtube-tasks/process",
                entity,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Job should be FAILED
        var failedJob = jobRepository.findById(TEST_USER_ID, jobId).join();
        assertThat(failedJob).isPresent();
        assertThat(failedJob.get().status()).isEqualTo(JobStatus.FAILED);
        assertThat(failedJob.get().errorMessage()).isNotNull();
    }

    @TestConfiguration
    static class YouTubeTestConfig {
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
        public FirebaseAuth firebaseAuth() throws Exception {
            FirebaseAuth mockAuth = Mockito.mock(FirebaseAuth.class);
            FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
            Mockito.when(mockToken.getUid()).thenReturn(TEST_USER_ID);
            Mockito.when(mockToken.getEmail()).thenReturn("testuser@example.com");
            Mockito.when(mockAuth.verifyIdToken(anyString())).thenReturn(mockToken);
            return mockAuth;
        }

        @Bean
        @Primary
        public TokenEncryptionService tokenEncryptionService() throws Exception {
            TokenEncryptionService mockService = Mockito.mock(TokenEncryptionService.class);
            Mockito.when(mockService.encrypt(anyString()))
                    .thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
            Mockito.when(mockService.decrypt(anyString()))
                    .thenAnswer(invocation -> {
                        String encrypted = invocation.getArgument(0);
                        return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
                    });
            return mockService;
        }

        @Bean
        @Primary
        public CloudTasksService cloudTasksService() {
            CloudTasksService mockService = Mockito.mock(CloudTasksService.class);
            doNothing().when(mockService).enqueueYouTubeJob(anyString(), anyString(), anyString());
            return mockService;
        }
    }
}
