# YouTube Recipe Extraction with Cloud Tasks

## Overview

Add support for extracting recipes from YouTube cooking videos using async background processing. Users submit a YouTube URL, receive a job ID immediately, and the recipe appears in their Google Drive when processing completes (typically 2-5 minutes due to transcript API).

**Key Features:**
- Extract recipe from YouTube video (metadata, description, comments, transcript)
- Use multiple transcript APIs with abstraction layer (youtube-transcript.io, supadata.ai)
- Async processing via Google Cloud Tasks (handles slow transcript APIs)
- Job tracking in Firestore
- Same Recipe output format as HTML extraction
- Same LLM processing (Gemini) with YouTube-specific prompt

**Benefits:**
- Expands recipe sources beyond websites to cooking videos
- Handles slow external APIs without blocking HTTP requests
- Job tracking allows users to monitor processing status
- Reuses existing validation, storage, and Drive integration

**Integration:**
- New endpoint: `POST /v1/recipes/youtube`
- Reuses: `ValidatingTransformerService`, `RecipeValidationService`, `GoogleDriveService`, `FirestoreRecipeRepository`
- New infrastructure: Google Cloud Tasks queue for async processing

## Context (from discovery)

**Files/components involved:**
- `/extractor/src/main/java/net/shamansoft/cookbook/`
  - `controller/RecipeController.java` - Existing HTML recipe endpoint pattern
  - `service/RecipeService.java` - Orchestrates recipe creation flow
  - `service/ValidatingTransformerService.java` - Transformer + validation with retry (REUSE)
  - `service/gemini/GeminiRestTransformer.java` - HTML→Recipe transformer (PATTERN)
  - `repository/FirestoreRecipeRepository.java` - Recipe caching (REUSE)
  - `client/GoogleDrive.java` - Drive file operations (REUSE)
  - `config/RestClientConfig.java` - HTTP client configuration (EXTEND)
- `/extractor/src/main/resources/`
  - `application.yaml` - Configuration (EXTEND)
  - `prompt.md` - HTML recipe extraction prompt (NEW: youtube-prompt.md)

**Related patterns found:**
- **Transformer Interface:** `Transformer<I, O>` with `transform(userId, input)` method - extensible design for different sources
- **Async Firestore:** Already using `CompletableFuture` with timeouts for Firestore operations
- **RestClient Infrastructure:** Pooled HTTP clients with timeouts, ready for external APIs
- **Service Layer Separation:** Controllers → Services → Repositories pattern
- **Configuration Externalization:** Property-based config with environment variable overrides
- **Validation & Retry:** Built-in framework with `ValidatingTransformerService`

**Dependencies identified:**
- Google Cloud Tasks client library (NEW)
- YouTube Data API v3 client (for metadata - optional, can use REST)
- Transcript APIs: youtube-transcript.io and supadata.ai (REST APIs)
- Existing: Gemini AI, Google Drive, Firestore, Firebase Auth

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- Run tests after each change
- Maintain backward compatibility

## Testing Strategy

- **Unit tests**: required for every task (see Development Approach above)
  - Mock external APIs (YouTube, transcript services, Cloud Tasks)
  - Test service logic in isolation
  - Test error handling and retries
  - Test job state transitions
- **Integration tests**: Add to existing `intTest` suite
  - Use Testcontainers + WireMock for external API mocks
  - Test full flow: POST /v1/recipes/youtube → Cloud Task → Drive upload
  - Test async task processing with Cloud Tasks emulator or in-memory queue
- **E2E tests**: Not applicable (no UI changes)

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix
- Update plan if implementation deviates from original scope
- Keep plan in sync with actual work done

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): tasks achievable within this codebase - code changes, tests, documentation updates
- **Post-Completion** (no checkboxes): items requiring external action - manual testing, GCP configuration, deployment configs

## Implementation Steps

### Task 1: Add Google Cloud Tasks dependency and configuration
- [x] Add `com.google.cloud:google-cloud-tasks` dependency to `extractor/build.gradle.kts`
- [x] Add Cloud Tasks configuration to `application.yaml`:
  - `cookbook.tasks.project-id` (default: `${google.cloud.project-id}`)
  - `cookbook.tasks.location` (default: `us-west1`)
  - `cookbook.tasks.queue-name` (default: `youtube-recipes`)
  - `cookbook.tasks.service-url` (Cloud Run URL for callbacks)
- [x] Create `CloudTasksConfig.java` in `/config/` with `@Bean CloudTasksClient`
- [x] Write tests for CloudTasksConfig (verify bean creation)
- [x] Run tests - must pass before task 2

### Task 2: Create Firestore YouTubeJobRepository
- [x] Create `YouTubeJob` record in `/repository/`:
  - Fields: `jobId` (String), `userId` (String), `videoUrl` (String), `status` (enum: PENDING, PROCESSING, COMPLETED, FAILED), `createdAt` (Instant), `completedAt` (Instant), `driveFileId` (String), `errorMessage` (String)
- [x] Create `JobStatus` enum with states: PENDING, PROCESSING, COMPLETED, FAILED
- [x] Create `FirestoreYouTubeJobRepository.java` in `/repository/`:
  - `CompletableFuture<YouTubeJob> save(YouTubeJob job)` - creates/updates job doc in `youtube_jobs` collection
  - `CompletableFuture<Optional<YouTubeJob>> findById(String jobId)` - retrieves job by ID
  - `CompletableFuture<Void> updateStatus(String jobId, JobStatus status, String errorMessage)` - updates job status
  - Use `users/{userId}/youtube_jobs/{jobId}` document path
- [x] Write tests for save() (success case)
- [x] Write tests for findById() (found and not found cases)
- [x] Write tests for updateStatus() (success and error cases)
- [x] Run tests - must pass before task 3

### Task 3: Create transcript service abstraction layer
- [ ] Create `TranscriptService` interface in `/service/youtube/`:
  - `String getTranscript(String videoId)` - returns transcript text or throws exception
  - `String getProviderName()` - returns provider name for logging
- [ ] Create `YouTubeTranscriptIoService.java` implementing `TranscriptService`:
  - Uses `https://www.youtube-transcript.io/api` (check actual API docs)
  - Handles API errors with `TranscriptException`
  - Configure API key/endpoint in `application.yaml` under `cookbook.transcript.youtube-transcript-io.*`
- [ ] Create `SupadataTranscriptService.java` implementing `TranscriptService`:
  - Uses `https://docs.supadata.ai/get-transcript` API
  - Handles API errors with `TranscriptException`
  - Configure API key/endpoint in `application.yaml` under `cookbook.transcript.supadata.*`
- [ ] Create `TranscriptException` custom exception in `/exception/`
- [ ] Create `TranscriptServiceRouter.java` in `/service/youtube/`:
  - Injects `List<TranscriptService>` (all implementations)
  - `String getTranscript(String videoId)` - tries services in order (round-robin or failover)
  - Logs which provider was used
  - Throws `TranscriptException` if all providers fail
- [ ] Add RestClient beans for transcript APIs in `RestClientConfig.java`
- [ ] Write tests for YouTubeTranscriptIoService (mock HTTP responses)
- [ ] Write tests for SupadataTranscriptService (mock HTTP responses)
- [ ] Write tests for TranscriptServiceRouter (verify failover logic)
- [ ] Run tests - must pass before task 4

### Task 4: Create YouTubeDataService for metadata extraction
- [ ] Create `YouTubeMetadata` record in `/service/youtube/`:
  - Fields: `title` (String), `description` (String), `channelName` (String), `publishedAt` (Instant)
- [ ] Create `YouTubeDataService.java` in `/service/youtube/`:
  - `YouTubeMetadata getMetadata(String videoId)` - fetches video metadata
  - Uses YouTube Data API v3 or REST endpoint (no auth needed for public videos)
  - Option 1: Use `google-api-services-youtube` library
  - Option 2: Direct REST call to `https://www.googleapis.com/youtube/v3/videos?id={videoId}&part=snippet`
  - Handles API errors with `YouTubeApiException`
- [ ] Create `YouTubeApiException` custom exception in `/exception/`
- [ ] Add YouTube API configuration to `application.yaml` under `cookbook.youtube.*`
- [ ] Add RestClient bean for YouTube API in `RestClientConfig.java` (if using REST)
- [ ] Write tests for getMetadata() (success case with mocked API response)
- [ ] Write tests for getMetadata() (error cases: invalid video ID, API error)
- [ ] Run tests - must pass before task 5

### Task 5: Create YouTubeTransformer implementing Transformer interface
- [ ] Create `YouTubeInput` record in `/service/youtube/`:
  - Fields: `videoUrl` (String), `videoId` (String)
- [ ] Create `YouTubeTransformer.java` in `/service/youtube/` implementing `Transformer<YouTubeInput, Recipe>`:
  - Inject: `YouTubeDataService`, `TranscriptServiceRouter`, `GeminiClient`, `RequestBuilder`
  - `Response transform(String userId, YouTubeInput input)`:
    1. Extract videoId from videoUrl
    2. Fetch metadata via `YouTubeDataService.getMetadata()`
    3. Fetch transcript via `TranscriptServiceRouter.getTranscript()`
    4. Build combined text input: title + description + transcript
    5. Call `GeminiClient.request()` with YouTube-specific prompt
    6. Return `Transformer.Response(isRecipe, recipe, rawLlmResponse)`
  - Handle errors with appropriate exceptions
- [ ] Extract videoId utility method: `extractVideoId(String url)` - handles youtu.be and youtube.com URLs
- [ ] Write tests for extractVideoId() (various URL formats)
- [ ] Write tests for transform() success case (mock all dependencies)
- [ ] Write tests for transform() when not a recipe (isRecipe=false)
- [ ] Write tests for transform() error cases (API failures, invalid URL)
- [ ] Run tests - must pass before task 6

### Task 6: Integrate YouTube prompt with RequestBuilder
- [ ] Review existing `youtube-prompt.md` in `/extractor/src/main/resources/`:
  - Already exists with proper YouTube context (title, description, transcript)
  - Uses format placeholders: `%s` for title, description, transcript
  - Handles YouTube-specific extraction rules (description ingredients, timestamps, etc.)
- [ ] Update `RequestBuilder.java` to support loading youtube-prompt.md:
  - Add method: `buildYouTubeRecipeRequest(String title, String description, String transcript, String language, String schema)`
  - Load `youtube-prompt.md` from classpath (similar to existing `prompt.md`)
  - Format prompt with `String.format()` using title, description, transcript parameters
  - Return `GeminiRequest` with formatted prompt
- [ ] Write tests for buildYouTubeRecipeRequest() (verify prompt is loaded and formatted correctly)
- [ ] Write tests for prompt formatting with sample YouTube data
- [ ] Run tests - must pass before task 7

### Task 7: Create Cloud Tasks handler endpoint (internal)
- [ ] Create `YouTubeTaskHandler.java` in `/controller/internal/`:
  - `@RestController` with `@RequestMapping("/internal/youtube-tasks")`
  - POST `/internal/youtube-tasks/process` - processes YouTube job
  - Requires `X-CloudTasks-TaskName` header (validates request is from Cloud Tasks)
  - Request body: `YouTubeTaskRequest(String jobId, String userId, String videoUrl)`
  - Flow:
    1. Update job status to PROCESSING
    2. Create `YouTubeInput` from videoUrl
    3. Call `ValidatingTransformerService.transform()` with `YouTubeTransformer`
    4. If `isRecipe=true`: upload to Drive via `GoogleDriveService`, update job with driveFileId
    5. If `isRecipe=false`: update job status to COMPLETED with no file
    6. Update job status to COMPLETED
    7. On error: update job status to FAILED with error message
  - Returns 200 OK when done (Cloud Tasks expects success response)
- [ ] Create `YouTubeTaskRequest` record in `/dto/`
- [ ] Add security validation: only accept requests with Cloud Tasks headers
- [ ] Write tests for task handler success path (mock all services)
- [ ] Write tests for task handler when not a recipe
- [ ] Write tests for task handler error cases (transformer failure, Drive upload failure)
- [ ] Write tests for security validation (reject requests without Cloud Tasks header)
- [ ] Run tests - must pass before task 8

### Task 8: Create YouTubeController public endpoint
- [ ] Create `YouTubeController.java` in `/controller/`:
  - `@RestController` with `@RequestMapping("/v1/recipes/youtube")`
  - POST `/v1/recipes/youtube` - creates YouTube recipe extraction job
  - Request body: `YouTubeRecipeRequest(String videoUrl)`
  - Requires authentication (userId from `@RequestAttribute`)
  - Flow:
    1. Validate videoUrl format
    2. Generate jobId (UUID)
    3. Create `YouTubeJob` (status=PENDING, createdAt=now)
    4. Save job to Firestore via `FirestoreYouTubeJobRepository`
    5. Create Cloud Task with payload `{jobId, userId, videoUrl}`
    6. Task target: `/internal/youtube-tasks/process` on same Cloud Run instance
    7. Return `YouTubeRecipeResponse(String jobId, String status)`
  - Returns 200 OK with jobId
- [ ] Create `YouTubeRecipeRequest` record in `/dto/`
- [ ] Create `YouTubeRecipeResponse` record in `/dto/`
- [ ] Create `CloudTasksService.java` in `/service/`:
  - `void enqueueYouTubeJob(String jobId, String userId, String videoUrl)` - creates Cloud Task
  - Uses `CloudTasksClient` to create task
  - Task name: `youtube-job-{jobId}`
  - Target: `POST {service-url}/internal/youtube-tasks/process`
- [ ] Add URL validation utility: `isValidYouTubeUrl(String url)`
- [ ] Write tests for YouTubeController (mock CloudTasksService and repository)
- [ ] Write tests for CloudTasksService (verify task creation)
- [ ] Write tests for URL validation
- [ ] Run tests - must pass before task 9

### Task 9: Add job status query endpoint (optional but recommended)
- [ ] Add GET `/v1/recipes/youtube/{jobId}` to `YouTubeController`:
  - Returns `YouTubeJobStatusResponse(String jobId, String status, String driveFileId, String driveFileUrl, String errorMessage)`
  - Fetches job from `FirestoreYouTubeJobRepository`
  - Returns 404 if job not found or belongs to different user
  - Includes driveFileId and driveFileUrl when status=COMPLETED
- [ ] Create `YouTubeJobStatusResponse` record in `/dto/`
- [ ] Write tests for job status endpoint (found, not found, completed, failed)
- [ ] Run tests - must pass before task 10

### Task 10: Integration testing
- [ ] Create `YouTubeRecipeIntegrationTest.java` in `/extractor/src/intTest/java/`:
  - Use Testcontainers for WireMock (mock YouTube API, transcript APIs, Gemini)
  - Test full flow: POST /v1/recipes/youtube → verify job created → simulate Cloud Task callback → verify Drive upload
  - Mock Cloud Tasks client (no actual task queue in integration tests)
  - Verify job status transitions: PENDING → PROCESSING → COMPLETED
  - Test error scenarios: invalid URL, API failures, not a recipe
- [ ] Run integration tests: `./gradlew :cookbook:intTest`
- [ ] Fix any failures before task 11

### Task 11: Configure Terraform for Cloud Tasks infrastructure
- [ ] Add Cloud Tasks API to `terraform/apis.tf`:
  - Add `google_project_service` resource for `cloudtasks.googleapis.com`
  - Add dependency on `serviceusage_api`
  - Add to `enabled_apis` output
- [ ] Add IAM role to `terraform/iam.tf`:
  - Add `google_project_iam_member` resource for `roles/cloudtasks.enqueuer`
  - Grant to `cookbook_cloudrun_service_account`
- [ ] Create `terraform/cloudtasks.tf`:
  - Create `google_cloud_tasks_queue` resource named `youtube-recipes`
  - Location: `var.region` (us-west1)
  - Configure retry settings: max_attempts=3, max_backoff=300s
  - Configure rate limits: max_concurrent_dispatches=10, max_dispatches_per_second=5
  - Add depends_on for Cloud Tasks API and service account
  - Add output: `cloud_tasks_queue_name`
- [ ] Write tests for Terraform (validate configuration): `cd terraform && terraform validate`
- [ ] No unit tests needed (infrastructure code)

### Task 12: Update Cloud Run Terraform and application config
- [ ] Add Secret Manager secrets to `terraform/cloudrun.tf`:
  - Create `google_secret_manager_secret.youtube_transcript_io_api_key`
  - Create `google_secret_manager_secret.supadata_api_key`
- [ ] Add environment variables to Cloud Run service in `terraform/cloudrun.tf`:
  - `COOKBOOK_YOUTUBE_TRANSCRIPT_IO_API_KEY` from Secret Manager
  - `COOKBOOK_SUPADATA_API_KEY` from Secret Manager
  - `COOKBOOK_TASKS_SERVICE_URL` using `google_cloud_run_service.cookbook.status[0].url`
  - `COOKBOOK_TASKS_QUEUE_NAME` = "youtube-recipes"
  - `COOKBOOK_TASKS_PROJECT_ID` = `var.project_id`
  - `COOKBOOK_TASKS_LOCATION` = `var.region`
- [ ] Update `application-gcp.yaml` with Cloud Tasks configuration:
  - `cookbook.tasks.service-url: ${COOKBOOK_TASKS_SERVICE_URL}`
  - `cookbook.tasks.queue-name: ${COOKBOOK_TASKS_QUEUE_NAME}`
  - `cookbook.tasks.project-id: ${COOKBOOK_TASKS_PROJECT_ID}`
  - `cookbook.tasks.location: ${COOKBOOK_TASKS_LOCATION}`
- [ ] Run Terraform validation: `cd terraform && terraform validate`
- [ ] No unit tests needed (configuration only)

### Task 13: [Final] Update documentation
- [ ] Update `extractor/README.md`:
  - Add section on YouTube recipe extraction
  - Document new endpoint: POST /v1/recipes/youtube
  - Document job status endpoint: GET /v1/recipes/youtube/{jobId}
  - Document environment variables for transcript APIs
- [ ] Update `CLAUDE.md`:
  - Add YouTubeController to endpoints list
  - Add Cloud Tasks to infrastructure section
  - Add transcript services to external API patterns
- [ ] Update root `README.md` if needed (feature highlights)
- [ ] No tests needed (documentation only)

## Technical Details

### Data Structures

**YouTubeJob (Firestore document):**
```java
record YouTubeJob(
    String jobId,           // UUID
    String userId,          // Firebase user ID
    String videoUrl,        // Original YouTube URL
    JobStatus status,       // PENDING, PROCESSING, COMPLETED, FAILED
    Instant createdAt,      // Job creation time
    Instant completedAt,    // Job completion time (null if not done)
    String driveFileId,     // Google Drive file ID (null until completed)
    String errorMessage     // Error details (null if successful)
)
```

**YouTubeInput:**
```java
record YouTubeInput(
    String videoUrl,        // Full YouTube URL
    String videoId          // Extracted video ID (11 characters)
)
```

**YouTubeMetadata:**
```java
record YouTubeMetadata(
    String title,           // Video title
    String description,     // Video description
    String channelName,     // Channel name
    Instant publishedAt     // Publication date
)
```

### Processing Flow

```
1. User Request:
   POST /v1/recipes/youtube {"videoUrl": "https://youtube.com/watch?v=..."}
   ↓
2. YouTubeController:
   - Generate jobId
   - Save YouTubeJob (status=PENDING) to Firestore
   - Enqueue Cloud Task
   - Return 200 OK {"jobId": "...", "status": "PENDING"}
   ↓
3. Cloud Tasks (async, 0-30s delay):
   POST /internal/youtube-tasks/process {"jobId": "...", "userId": "...", "videoUrl": "..."}
   ↓
4. YouTubeTaskHandler:
   - Update job status to PROCESSING
   - Extract videoId from URL
   ↓
5. YouTubeTransformer:
   - Fetch metadata (YouTubeDataService)
   - Fetch transcript (TranscriptServiceRouter → tries providers)
   - Combine: title + description + transcript
   - Call Gemini with YouTube prompt
   ↓
6. ValidatingTransformerService:
   - Validate Recipe against JSON schema
   - Retry with feedback if invalid
   - Post-process (add schema version, date, etc.)
   ↓
7. RecipeStoreService (optional caching):
   - Cache in Firestore if enabled
   ↓
8. GoogleDriveService:
   - Convert Recipe to YAML
   - Upload to user's Drive folder
   ↓
9. YouTubeTaskHandler:
   - Update job status to COMPLETED
   - Store driveFileId in job
   - Return 200 OK to Cloud Tasks
   ↓
10. User Query (optional):
    GET /v1/recipes/youtube/{jobId}
    → {"status": "COMPLETED", "driveFileId": "...", "driveFileUrl": "..."}
```

### Transcript Service Strategy

**Round-Robin with Fallback:**
1. Try Provider 1 (youtube-transcript.io)
2. If fails: Try Provider 2 (supadata.ai)
3. If both fail: Throw TranscriptException

**Future Enhancement:** Track provider success rates, prefer faster/more reliable provider

### API Endpoints

**New Endpoints:**
- `POST /v1/recipes/youtube` - Create YouTube recipe job
  - Auth: Required (Firebase token)
  - Body: `{"videoUrl": "https://youtube.com/watch?v=..."}`
  - Response: `{"jobId": "uuid", "status": "PENDING"}`
  - Status: 200 OK

- `GET /v1/recipes/youtube/{jobId}` - Query job status
  - Auth: Required (Firebase token, must own job)
  - Response: `{"jobId": "...", "status": "COMPLETED", "driveFileId": "...", "driveFileUrl": "...", "errorMessage": null}`
  - Status: 200 OK (job found), 404 Not Found

**Internal Endpoint:**
- `POST /internal/youtube-tasks/process` - Cloud Tasks callback
  - Auth: Cloud Tasks header validation
  - Body: `{"jobId": "...", "userId": "...", "videoUrl": "..."}`
  - Response: 200 OK (always, even on errors - Cloud Tasks expects success)

### Configuration Properties

```yaml
cookbook:
  tasks:
    project-id: ${google.cloud.project-id}
    location: us-west1
    queue-name: youtube-recipes
    service-url: ${COOKBOOK_TASKS_SERVICE_URL} # Cloud Run URL

  transcript:
    youtube-transcript-io:
      api-url: https://www.youtube-transcript.io/api
      api-key: ${COOKBOOK_YOUTUBE_TRANSCRIPT_IO_API_KEY}
      timeout-seconds: 120
    supadata:
      api-url: https://api.supadata.ai/v1/transcript
      api-key: ${COOKBOOK_SUPADATA_API_KEY}
      timeout-seconds: 120

  youtube:
    api-url: https://www.googleapis.com/youtube/v3
    api-key: ${COOKBOOK_YOUTUBE_API_KEY} # Optional if using public endpoint
    timeout-seconds: 10
```

### Error Handling

**Retry Strategy:**
- Cloud Tasks default: 3 retries with exponential backoff
- Transcript APIs: Try all providers before failing
- Gemini validation: Configurable retry (existing `recipe.llm.retry`)

**Error States:**
- Job status = FAILED with errorMessage stored in Firestore
- User queries job status and sees error
- No automatic retry from user perspective (can resubmit)

### GCP Resources Required

**New Resources:**
1. **Cloud Tasks Queue**: `youtube-recipes` in `us-west1`
2. **IAM Role**: Cloud Run service account needs `cloudtasks.tasks.create` permission
3. **Environment Variables**: Transcript API keys in Cloud Run

**Existing Resources (reused):**
- Cloud Run service (adds new endpoints)
- Firestore (new collection: `users/{userId}/youtube_jobs/{jobId}`)
- Google Drive (same upload logic)
- Gemini API (same model, new prompt)

## Post-Completion

*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification:**
- Test with real YouTube cooking videos (sample URLs: Babish, Tasty, etc.)
- Verify transcript quality from both providers
- Test with videos in different languages
- Verify job status updates in Firestore console
- Check Cloud Tasks queue in GCP console

**GCP Infrastructure Deployment:**
- Deploy Terraform changes: `cd terraform && terraform plan && terraform apply`
- Verify Cloud Tasks queue created: `gcloud tasks queues describe youtube-recipes --location=us-west1`
- Add secret values to Secret Manager (Terraform creates secrets, values added manually):
  - `gcloud secrets versions add youtube-transcript-io-api-key --data-file=-` (paste API key)
  - `gcloud secrets versions add supadata-api-key --data-file=-` (paste API key)
- Verify Cloud Run deployment picked up new environment variables

**External API Setup:**
- Register for youtube-transcript.io API key
- Register for supadata.ai API key
- Verify API rate limits and quotas
- Monitor API costs (if applicable)

**Monitoring:**
- Set up Cloud Monitoring alerts for:
  - High Cloud Tasks queue depth (> 100)
  - High task failure rate (> 10%)
  - Slow transcript API response times (> 2 minutes)
  - Firestore query costs

**Future Enhancements:**
- Add GET /v1/recipes/youtube endpoint to list user's jobs (pagination)
- Add DELETE /v1/recipes/youtube/{jobId} to cancel pending jobs
- Add webhook support for job completion notifications
- Add retry button in UI for failed jobs
- Support YouTube playlists (batch processing)
- Add transcript caching to avoid redundant API calls
- Add cost tracking per user (transcript API usage)
