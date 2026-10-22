# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Quick links:** [STATUS.md](STATUS.md) for current project state · [Documentation Index](#documentation-index) for all docs · [docs/archive/](docs/archive/README.md) for historical material. This repo (`sar-srv`) is the backend of the Save-A-Recipe (SAR) product; siblings are `sar-ext` (extension), `sar-kmp` (client), `sar-infra` (Terraform/GCP).

## Project Overview

Multi-module Gradle project for recipe extraction and management using AI:
- **`extractor/`** (Gradle module: `:cookbook`) - Spring Boot REST API that extracts recipes from HTML using Google Gemini AI
- **`recipe-sdk/`** (Gradle module: `:recipe-sdk`) - Shared library with recipe models, YAML parser/serializer, and validation

The system processes HTML content, extracts structured recipe data using AI, validates against a schema, and stores in Firestore.

> **Not on `main` (checked 2026-10-15):** the YouTube extraction feature (`YouTubeController`, `YouTubeTaskHandler`,
> `YouTubeTransformer`, transcript services, `CloudTasksService`, `/v1/recipes/youtube`) and admin tier management
> (`AdminController`, `@RequireAdmin`, `AdminAccessAspect`, `AdminUserService`, `/v1/admin/...`) described below exist
> only on the local, unpushed branches `youtube-prompt` and `update-tier`. On `main` only the `YOUTUBE_EXTRACTION`
> quota operation exists, and `FirebaseAuthFilter` does not read the `admin` claim.

## Multi-Module Gradle Structure

**IMPORTANT**: The directory `extractor/` is renamed to `:cookbook` in Gradle (see `settings.gradle`):
```gradle
project(':extractor').name = 'cookbook'
```

This means:
- Use `:cookbook` for Gradle tasks: `./gradlew :cookbook:build`, `./gradlew :cookbook:test`
- File paths still use `extractor/`: `extractor/src/main/java/...`
- Build outputs reference `cookbook`: `cookbook.jar`, `cookbook:<version>-SNAPSHOT` (version from `extractor/build.gradle.kts`)

## Build and Test Commands

**Root `gradlew` location**: All commands run from `/Users/alexey/dev/sar/sar-srv/`

### Common Development Tasks

```bash
# Build everything (all modules)
./gradlew build

# Build specific module
./gradlew :cookbook:build        # Spring Boot application
./gradlew :recipe-sdk:build      # SDK library

# Run unit tests
./gradlew test                   # All modules
./gradlew :cookbook:test         # Cookbook only

# Run integration tests (cookbook only, uses Testcontainers + WireMock)
./gradlew :cookbook:intTest

# Check code coverage (40% minimum enforced)
./gradlew :cookbook:checkCoverage

# Security vulnerability scan
./gradlew :cookbook:dependencyCheck

# Run Spring Boot locally (requires COOKBOOK_GEMINI_API_KEY env var)
./gradlew :cookbook:bootRun

# Build native image (slow, ~10-15 min)
./gradlew :cookbook:nativeCompile
```

### Running a Single Test

```bash
# Run specific test class
./gradlew :cookbook:test --tests CookbookControllerTest

# Run specific test method
./gradlew :cookbook:test --tests CookbookControllerTest.testPostRecipe
```

### Docker Build (Local Development)

Scripts located in `extractor/scripts/`:

```bash
# Build JVM Docker image (fast, for local testing)
cd extractor/scripts && ./build.sh

# Build with specific version tag
cd extractor/scripts && ./build.sh v1.0.0

# Build native image (slow, production-like, requires 12GB memory)
cd extractor/scripts && ./build.sh --native --memory=12g

# Build and push to GCR (requires GCP auth)
cd extractor/scripts && ./build.sh v1.0.0 --push

# Run locally
docker run -p 8080:8080 \
  -e COOKBOOK_GEMINI_API_KEY=$COOKBOOK_GEMINI_API_KEY \
  gcr.io/kukbuk-tf/cookbook:latest
```

## CI/CD and Deployment

### Automated CI/CD Pipeline

All deployments are automated via GitHub Actions:

**PR Validation** (`.github/workflows/pr-validation.yml`):
- Triggered on PRs to `main` or `develop`
- Ignores changes to docs, markdown files, and scripts
- Uses Java 25 for builds
- Runs all tests (unit + integration)
- Enforces 40% code coverage minimum
- Security vulnerability scanning (OWASP, fails on CVSS ≥ 7.0)
- Comments coverage report and test summary on PR

**Deployment Pipeline** (`.github/workflows/deploy.yml`):
- Triggered on merge to `main` or manual workflow dispatch
- Concurrency: Only one deployment at a time (no cancellation)
- Uses Java 25 for builds
- **4-Phase Deployment**:
  1. **Phase 0 & 1: Test & Validate** (2-3 min) - Extract version, run all tests, check coverage
  2. **Phase 2: Build & Push** (10-15 min) - Build GraalVM native image for linux/amd64, push to GCR
  3. **Phase 3: Trigger Deploy** (<1 min) - Fire `repository_dispatch` event to `shamansoft/sar-infra`; actual Terraform apply, Cloud Run update, and health check run there
  4. **Phase 4: Finalize** (1 min) - Create git tag, bump version, update coverage badge, create GitHub Release
- Auto-increments version (removes `-SNAPSHOT`, tags, bumps to next SNAPSHOT)
- Creates GitHub Release with deployment details
- Target: `gcr.io/kukbuk-tf/cookbook` in `us-west1` region
- **Deployment completion**: Phase 3 only fires a dispatch event. Monitor `shamansoft/sar-infra` Actions for actual deploy status (Terraform apply + health check).
- **Required secret**: `CROSS_REPO_PAT` — GitHub PAT with `repo` scope on `shamansoft/sar-infra`, stored in `shamansoft/kukbuk-srv` repository secrets. Without it, Phase 3 fails.

**Total deployment time**: ~12-15 min sar-srv pipeline + sar-infra deploy running in parallel

### Version Management

Follows Maven/Gradle conventions with `-SNAPSHOT` suffix:
- **Development**: `X.Y.Z-SNAPSHOT` (current in `main` branch; read the real value from `version` in `extractor/build.gradle.kts`)
- **Release**: `X.Y.Z` (when deployed to production)
- **Next**: `X.Y.(Z+1)-SNAPSHOT` (auto-bumped after release)

Version controlled by `extractor/build.gradle.kts` and managed by `extractor/scripts/version-updater.sh`.

### Manual Deployment

Only use for testing or emergencies (prefer automated pipeline):

```bash
# Trigger deployment workflow manually
gh workflow run deploy.yml --ref main

# Build and push only, skip triggering sar-infra deploy
gh workflow run deploy.yml --ref main -f skip_deploy_trigger=true
```

## Key Architecture

### Service Layer Pattern

The Spring Boot application (`extractor/`) follows a clean layered architecture:

```
Controllers → Services → Repositories/Clients
    ↓           ↓             ↓
   DTOs    Business Logic   External APIs
```

**Package Structure**:
- `controller/` - REST endpoints (`RecipeController`, `YouTubeController`, `StorageController`, `UserProfileController`, `AdminController`, `MediaController`, `TestController`, `MiscController`)
  - `controller/internal/` - Internal endpoints (`YouTubeTaskHandler` - Cloud Tasks callback)
- `service/` - Business logic (AI extraction, validation, storage, caching, admin user management)
  - `gemini/` - Gemini AI integration (`GeminiClient`, `GeminiRestTransformer`, `RequestBuilder`)
  - `youtube/` - YouTube recipe extraction (`YouTubeTransformer`, `YouTubeDataService`, `TranscriptServiceRouter`, `YouTubeTranscriptIoService`, `SupadataTranscriptService`)
- `repository/` - Data access (`FirestoreRecipeRepository`, `FirestoreUserProfileRepository`, `FirestoreYouTubeJobRepository`)
- `client/` - External API clients (`GoogleDrive`)
- `dto/` - Request/response models
- `security/` - Authentication (`FirebaseAuthFilter`); admin access guard (`@RequireAdmin` annotation, `AdminAccessAspect` — `@Around` on `@RequireAdmin`-annotated methods, throws `AdminAccessException` → 403)
- `config/` - Spring configuration
- `validation/` - Recipe validation logic
- `entitlement/` - AOP quota enforcement (`@CheckEntitlement`, `EntitlementAspect`, `EntitlementService`, `FirestoreEntitlementRepository`, `EntitlementResponseAdvice`, `EntitlementPlanConfig`); exceptions: `EntitlementException` (quota denied → 429), `EntitlementAuthException` (unauthenticated → 401); domain: `UserTier`, `Operation`, `EntitlementOutcome`, `EntitlementResult`, `QuotaWindow`

**Core Components**:
- `RecipeController` - `/v1/recipes` endpoints (GET list, GET by ID, POST create, POST custom)
- `RecipeService` - Main orchestrator for recipe operations
- `StorageController` - `/storage/*` endpoints for Google Drive integration
- `GeminiClient` - HTTP client for Gemini API communication
- `GeminiRestTransformer` - Converts HTML → structured JSON via Gemini AI; also structures free-text descriptions (`transformDescription()`)
- `RequestBuilder` - Builds Gemini API requests with schema and retry feedback; also builds description-based requests (`buildRequestFromDescription()`)
- `RecipeValidationService` - Validates recipes against JSON schema
- `ValidatingTransformerService` - Orchestrates transform + validate with retry logic
- `RecipePostProcessor` - Populates deterministic fields (schemaVersion, recipeVersion, dateCreated, source URL) after validation
- `RecipeStoreService` - Recipe caching with configurable timeouts
- `ContentHashService` - Deduplication via content hashing
- `HtmlExtractor` - HTML source selection: `extractHtml()` uses provided HTML or fetches from URL (jsoup only, `POST /v1/recipes`); `fetchWithFallback()` fetches with jsoup, then markdown.new if blocked, returning `PageContent` (HTML or MARKDOWN; `POST /v1/recipes/url`)
- `HtmlFetcher` (jsoup) / `MarkdownNewClientImpl` (markdown.new) - `UrlContentFetcher` implementations; `BlockedPageDetector` flags challenge pages and near-empty pages
- `Transformer.transformMarkdown()` - Markdown path: skips HTML cleaning (`AdaptiveCleaningTransformerService`) and uses `RequestBuilder.buildMarkdownRequest()` (`<PAGE_MARKDOWN>` user template)
- `Compressor` / `CompressorHTMLBase64` - Base64+GZIP decompression (called by `RecipeService` before `HtmlExtractor`)
- `StringToCompressionConverter` - Spring `Converter` for `?compression=` param; `"gzip"` → `BASE64_GZIP`, anything else → `NONE`
- `FirestoreRecipeRepository` - Stores recipes in Firestore (users → recipes → items)
- `TokenRestService` - Validates Google OAuth tokens
- `YouTubeController` - `/v1/recipes/youtube` endpoints (POST create job, GET status)
- `YouTubeTaskHandler` - Internal Cloud Tasks callback handler (`/internal/youtube-tasks/process`)
- `YouTubeTransformer` - AI-powered YouTube video → Recipe extraction via Gemini
- `YouTubeDataService` - YouTube Data API v3 client for video metadata
- `TranscriptServiceRouter` - Multi-provider transcript fetching with failover
- `CloudTasksService` - Google Cloud Tasks job enqueuing
- `FirestoreYouTubeJobRepository` - YouTube job persistence in Firestore
- `EntitlementAspect` - AOP `@Around` interceptor for `@CheckEntitlement`-annotated methods; reads `userId`/`userTier` from request attributes; throws `EntitlementAuthException` (→ HTTP 401) when `userId` is absent or blank; throws `EntitlementException` (→ HTTP 429) on quota denial
- `EntitlementService` - Quota/tier check orchestrator; paid-tier fast-path (no Firestore read); FREE-tier quota check with credits fallback; emits `entitlement.check` Micrometer counters
- `FirestoreEntitlementRepository` - Firestore-backed quota windows (`quota_windows` collection); atomic check-and-increment transaction; **no-write on deny** (skips `tx.set()` when quota exhausted; safe due to TTL invariant: `expireAt = resetAt + 2 days`); `deductCredit()` returns `CompletableFuture<OptionalInt>` (`OptionalInt.of(remaining)` when deducted, `OptionalInt.empty()` when no credits); configurable timeouts from `EntitlementPlanConfig`
- `EntitlementResponseAdvice` - `@ControllerAdvice` appending `X-Quota-Outcome`, `X-Quota-Remaining`, `X-Credits-Remaining`, `X-Quota-Resets-At` headers to successful responses
- `EntitlementPlanConfig` - `@ConfigurationProperties(prefix="entitlement")`; validates all `UserTier × Operation` pairs on startup; timeouts configurable via `entitlement.timeouts.*`
- `AdminController` - `PUT /v1/admin/users/{userId}/tier` annotated with `@RequireAdmin`; reads `adminUserId` from request attribute; delegates to `AdminUserService`
- `AdminUserService` - Admin tier update orchestrator; loads Firestore profile → writes updated tier (`FirestoreUserProfileRepository.updateTier()`) → merges-and-sets Firebase custom claim (preserves existing claims like `admin:true`) → emits audit log

### Recipe SDK Module

Shared library (`recipe-sdk/`) used by cookbook for recipe data:
- **Models**: Java 21 records (`Recipe`, `Ingredient`, `Instruction`, `Nutrition`)
- **Parser**: `YamlRecipeParser` - Parse YAML to Recipe objects
- **Serializer**: `RecipeSerializer` - Serialize Recipe to YAML
- **Validation**: Jakarta Bean Validation annotations
- **GraalVM**: Pre-configured reflection metadata for native compilation

### Testing Architecture

**Unit Tests** (`extractor/src/test/java`):
- Fast, isolated tests with mocks
- Standard JUnit 5 + Mockito

**Integration Tests** (`extractor/src/intTest/java`):
- Separate source set: `./gradlew :cookbook:intTest`
- Uses **Testcontainers** for WireMock container
- **WireMock** mocks external services (Google Auth, Gemini AI, Drive API)
- Full Spring Boot context testing
- Docker required for execution

## Configuration and Environment

### Required Environment Variables

For local development:
```bash
COOKBOOK_GEMINI_API_KEY=your_gemini_api_key
COOKBOOK_GOOGLE_OAUTH_ID=your_google_oauth_client_id
```

For GCP deployment (set via Cloud Run environment or Secret Manager):
```bash
GOOGLE_CLOUD_PROJECT_ID=kukbuk-tf
FIRESTORE_PROJECT_ID=kukbuk-tf
FIRESTORE_ENABLED=true
```

### Spring Profiles

- `application.yaml` - Default configuration
- `application-gcp.yaml` - GCP-specific overrides for Cloud Run
- `application-local.yaml` - Local development overrides
- Gemini AI configured via `cookbook.gemini.*` properties
- Firestore uses GCP Application Default Credentials in production

### Entitlement / Quota Configuration

Per-user daily quota system enforced via AOP (`@CheckEntitlement`):
- `@CheckEntitlement` is placed on **controller** methods (`RecipeController.createRecipe`, `RecipeController.createCustomRecipe`, `RecipeController.createRecipeFromUrl`), not on service methods — ensures the AOP proxy is always entered for HTTP requests
- `entitlement.plans.{FREE|PRO|ENTERPRISE}.{RECIPE_EXTRACTION|YOUTUBE_EXTRACTION}.daily` — daily limit (-1 = unlimited for PRO/ENTERPRISE)
- `ENTITLEMENT_FREE_RECIPE_DAILY` env var (default: 5) — FREE tier recipe daily limit
- `ENTITLEMENT_FREE_YOUTUBE_DAILY` env var (default: 1) — FREE tier YouTube daily limit
- `entitlement.timeouts.check-ms` (default: 500) — timeout for Firestore profile load (read)
- `entitlement.timeouts.increment-ms` (default: 1000) — timeout for quota `checkAndIncrement` and `deductCredit` (both are Firestore write transactions)
- Startup validation: all `UserTier × Operation` pairs must be present; missing entry fails startup
- **Timeout asymmetry**: `checkAndIncrement` timeout → `CIRCUIT_OPEN` (fail-open, request **allowed**); `deductCredit` timeout → `OptionalInt.empty()` (fail-safe, treated as no credits, falls through to `DENIED_QUOTA`)
- **Fail-open on timeout (`CIRCUIT_OPEN`)**: If Firestore exceeds `increment-ms` on a quota check, the request is **allowed** (not rejected). `X-Quota-Outcome: CIRCUIT_OPEN` appears in the response. Quota DB unavailability does not degrade API availability.
- **HTTP 429**: FREE-tier users exceeding quota receive 429 with `QuotaErrorResponse` body and `Retry-After` header
- **Response headers**: `X-Quota-Outcome`, `X-Quota-Remaining`, `X-Credits-Remaining` (absent for paid), `X-Quota-Resets-At` added to all `@CheckEntitlement`-gated responses
- **Tier claim fast-path**: JWT `tier` claim extracted by `FirebaseAuthFilter` → skips Firestore profile read for paid users

### Recipe Store Configuration

Recipe caching system with configurable timeouts:
- **Lookup timeout**: 200ms (fast check if recipe exists)
- **Save timeout**: 5000ms (allow time for write operations)
- **Count timeout**: 1000ms (for pagination/statistics)
- **Content hashing**: SHA-256 for deduplication
- **Retry logic**: Configurable via `RECIPE_LLM_RETRY` (default: 1)

### Recipe Post-Processing

Post-processing automatically populates deterministic fields after successful AI transformation and validation:
- **Schema version**: From `recipe.schema.version` config (default: "1.0.0")
- **Recipe version**: Always "1.0.0" for new recipes (user can update manually later)
- **Date created**: Current UTC date when recipe is created (LocalDate.now(Clock.systemUTC()))
- **Source URL**: Original URL from the request
- **Language**: normalized to two-letter ISO 639-1 by `LanguageCodes` ("en-US", "en/us", "EN" → "en"); anything else
  falls back to the SDK default "en". `RecipeMapper` applies the same normalization when returning stored recipes.
- **Category**: only values on the closed English list in `RecipeCategories` are kept (must match the `category`
  enum in `llm-recipe-schema.json`); the page's own category words go in `tags`. Stored recipes are returned as stored.
- **Processing order**: Happens AFTER validation, BEFORE caching
- **Immutability**: Uses Java record "with" methods to create updated copies

### Gemini AI Integration

- **Model**: `gemini-3.1-flash-lite` (via REST API; switched from `gemini-2.5-flash-lite` in Oct 2026)
- **Base URL**: `https://generativelanguage.googleapis.com/v1beta`
- **Configuration** (`cookbook.gemini.*` in `application.yaml`):
  - Temperature: 0.0, Top-P: 1.0
  - Max output tokens: 8192
  - Thinking budget: 0 (off)
  - Safety threshold: BLOCK_NONE
- **Prompt templates**:
  - `extractor/src/main/resources/prompt.md` — HTML recipe extraction
  - `extractor/src/main/resources/description-prompt.md` — free-text description structuring (no date, single `%s` placeholder for description)
- **Validation**: Bean Validation on the `recipe-sdk` records (`RecipeValidationService`); `llm-recipe-schema.json` is the Gemini response schema
  - Schema enforced - no fallback to raw YAML
  - Retry logic with validation feedback (configurable via `RECIPE_LLM_RETRY`)
- **Features**:
  - Auto-detects language from HTML content
  - Returns JSON format conforming to schema
  - Returns `is_recipe: false` if content is not a recipe
  - Supports retry with validation error feedback

### API Endpoints

**Recipe Endpoints** (`/v1/recipes`):
- `GET /v1/recipes` - List recipes with pagination
  - Query params: `pageSize` (1-100, default 20), `pageToken`
  - Returns: `RecipeListResponse` with recipes and `nextPageToken`

- `GET /v1/recipes/{id}` - Get single recipe by Drive file ID
  - Returns: `RecipeDto`
  - Cache-Control: 1 hour

- `POST /v1/recipes` - Create recipe from HTML
  - Body: `Request` with `url`, `title`, `html` (optional, Base64+GZIP compressed)
  - Query params: `compression` — `gzip` to decompress HTML, omit for raw (default: no decompression)
  - Returns: `RecipeResponse` with `recipeId`, `isRecipe`
  - Returns HTTP 429 + `QuotaErrorResponse` + `Retry-After` header when FREE-tier quota exhausted
  - Successful responses include `X-Quota-Outcome`, `X-Quota-Remaining`, `X-Quota-Resets-At` headers

- `POST /v1/recipes/url` - Create recipe from a URL alone (mobile "add from URL")
  - Body: `UrlRecipeRequest` with `url` (required, absolute http(s)), `title` (optional)
  - Fetches directly (jsoup); if blocked (HTTP 401/403/429/503, timeout, challenge or near-empty page) falls back to markdown.new, which returns Markdown
  - Shares the URL cache with `POST /v1/recipes`; "not a recipe" verdicts on Markdown are not cached
  - Returns HTTP 422 with a user-presentable `message` when the page cannot be fetched either way
  - Fallback toggle: `cookbook.fetcher.markdown-new.enabled` / `COOKBOOK_FETCHER_MARKDOWN_NEW_ENABLED` (default true)

- `POST /v1/recipes/custom` - Create recipe from free-text description (user-authored)
  - Body: `CustomRecipeRequest` with `description` (required), `title` (optional), `url` (optional)
  - Query params: `compression` — same as above, applies to the description field
  - No HTML extraction or caching; Gemini structures the description directly
  - Supports multiple recipes in a single description (returns `recipes[]` array)
  - Returns: `RecipeResponse` with `isRecipe: true` always

**YouTube Recipe Endpoints** (`/v1/recipes/youtube`):
- `POST /v1/recipes/youtube` - Create async YouTube recipe extraction job
  - Body: `YouTubeRecipeRequest` with `videoUrl`
  - Returns: `YouTubeRecipeResponse` with `jobId`, `status` (PENDING)
  - Creates Cloud Task for background processing

- `GET /v1/recipes/youtube/{jobId}` - Query job status
  - Returns: `YouTubeJobStatusResponse` with `jobId`, `status`, `driveFileId`, `driveFileUrl`, `errorMessage`
  - Status values: PENDING, PROCESSING, COMPLETED, FAILED
  - Returns 404 if job not found or belongs to different user

**Internal Endpoints** (`/internal/*`):
- `POST /internal/youtube-tasks/process` - Cloud Tasks callback for YouTube job processing
  - Validates `X-CloudTasks-TaskName` header
  - Processes video: metadata + transcript + Gemini AI extraction + Drive upload

**Storage Endpoints** (`/storage/*`):
- Google Drive integration and OAuth management

**User Profile Endpoints** (`/v1/profiles`):
- User profile data management

**Admin Endpoints** (`/v1/admin`):
- `PUT /v1/admin/users/{userId}/tier` - Update a user's subscription tier
  - Requires Firebase token with `admin:true` custom claim
  - Body: `UpdateTierRequest` with `tier` (FREE, PRO, ENTERPRISE)
  - Returns: `UpdateTierResponse` with `userId`, `oldTier`, `newTier`
  - 400 → invalid tier value; 403 → caller not admin; 404 → target user not found

**Test Endpoints** (`/test/*`):
- Testing and debugging endpoints (development only)

### Firestore Data Model

Hierarchical structure:
```
users/{userId}/
  recipes/{recipeId}/
    items/{itemId}
  youtube_jobs/{jobId}
  profiles/...
quota_windows/{userId}::{operation}::{YYYYMMDD}/   (top-level, not nested under users/)
  — count, limit, resetAt, expireAt (TTL field, auto-deleted after resetAt + 2 days)
```

See [`shamansoft/sar-infra`](https://github.com/shamansoft/sar-infra) for Firestore schema documentation.

## Infrastructure (Google Cloud Platform)

Infrastructure is managed in the [`sar-infra`](https://github.com/shamansoft/sar-infra) repository using OpenTofu. See that repo for `.tf` files, deploy scripts, and infrastructure docs.

## Critical Files Reference

### Application Code
- `extractor/src/main/java/net/shamansoft/cookbook/`
  - `controller/RecipeController.java` - Main `/v1/recipes` endpoints (GET list, GET by ID, POST HTML, POST URL, POST custom)
  - `controller/StorageController.java` - Google Drive integration endpoints
  - `dto/Compression.java` - Enum: `BASE64_GZIP`, `NONE`
  - `dto/CustomRecipeRequest.java` - Request DTO for `/v1/recipes/custom`
  - `dto/UrlRecipeRequest.java` - Request DTO for `/v1/recipes/url`
  - `config/StringToCompressionConverter.java` - Case-insensitive `?compression=` param binding
  - `service/RecipeService.java` - Recipe operations orchestrator; `createRecipe()` + `createRecipeFromUrl()` + `createRecipeFromDescription()`
  - `service/gemini/GeminiClient.java` - Gemini API HTTP client
  - `service/gemini/GeminiRestTransformer.java` - AI-powered HTML→JSON extraction + description structuring
  - `service/gemini/RequestBuilder.java` - Builds Gemini API requests (HTML and description variants)
  - `service/RecipeValidationService.java` - JSON schema validation
  - `service/ValidatingTransformerService.java` - Transform + validate with retry
  - `service/RecipeStoreService.java` - Recipe caching layer
  - `service/ContentHashService.java` - Content deduplication
  - `repository/FirestoreRecipeRepository.java` - Firestore data access
  - `security/FirebaseAuthFilter.java` - OAuth token validation; extracts `userId`, `userEmail`, `userTier`, and `isAdmin` (sets `isAdmin=true` when `admin:true` JWT claim present)
  - `security/RequireAdmin.java` - Method-level annotation marking admin-only endpoints
  - `security/AdminAccessAspect.java` - AOP guard; reads `isAdmin` from request context; throws `AdminAccessException` → 403
  - `controller/AdminController.java` - `PUT /v1/admin/users/{userId}/tier` endpoint
  - `service/AdminUserService.java` - Admin tier update: Firestore merge-write + Firebase claim merge-write + audit log
  - `dto/admin/UpdateTierRequest.java` - Request DTO (`@NotNull UserTier tier`)
  - `dto/admin/UpdateTierResponse.java` - Response DTO (`userId`, `oldTier`, `newTier`)
  - `controller/YouTubeController.java` - YouTube recipe endpoints (POST job, GET status)
  - `controller/internal/YouTubeTaskHandler.java` - Cloud Tasks callback handler
  - `service/youtube/YouTubeTransformer.java` - YouTube video → Recipe transformer
  - `service/youtube/YouTubeDataService.java` - YouTube Data API v3 metadata client
  - `service/youtube/TranscriptServiceRouter.java` - Multi-provider transcript fetching
  - `service/youtube/YouTubeTranscriptIoService.java` - youtube-transcript.io API client
  - `service/youtube/SupadataTranscriptService.java` - supadata.ai API client
  - `service/CloudTasksService.java` - Cloud Tasks job enqueuing
  - `repository/FirestoreYouTubeJobRepository.java` - YouTube job Firestore data access

### Configuration & Schema
- `extractor/src/main/resources/`
  - `application.yaml` - Spring Boot configuration
  - `prompt.md` - Gemini AI prompt template (HTML recipe extraction)
  - `description-prompt.md` - Gemini AI prompt template (free-text description → recipe)
  - `youtube-prompt.md` - Gemini AI prompt template (YouTube video recipe extraction)

### Build & Deploy
- `extractor/build.gradle.kts` - Dependencies, tasks, version (see `version` in the file)
- `settings.gradle` - Multi-module setup (extractor→cookbook rename)
- `gradle/libs.versions.toml` - Centralized version catalog (Gradle 9.2.0)
- `extractor/scripts/build.sh` - Docker image builder
- `extractor/scripts/version-updater.sh` - Version management utilities

### Infrastructure
- See [`shamansoft/sar-infra`](https://github.com/shamansoft/sar-infra) for all infrastructure files
- `.github/workflows/deploy.yml` — triggers sar-infra deployment via `repository_dispatch` after image push
- `.github/workflows/pr-validation.yml` - PR test automation

## Documentation Index

Project status snapshot: [STATUS.md](STATUS.md). Archived/superseded docs: [docs/archive/](docs/archive/README.md).

### Architecture / overview
- [STATUS.md](STATUS.md) — current maturity, what's implemented/in-progress/planned, milestones
- This file (CLAUDE.md) — architecture, build/run/test, conventions, agent guidance (the canonical engineering reference)

### Specs / RFCs
- [docs/specs/entitlement.md](docs/specs/entitlement.md) — **canonical** as-built spec for the per-user quota/entitlement system
- [docs/specs/debug-flags.md](docs/specs/debug-flags.md) — opt-in dump flags on the local `/debug/v1/recipes` endpoint
- [docs/rfc/entitlement.md](docs/rfc/entitlement.md) — entitlement design rationale & review history (background; spec is authoritative)

### Plans (active)
- [docs/plans/20260315-openapi-yaml-generation.md](docs/plans/20260315-openapi-yaml-generation.md) — build-time OpenAPI YAML generation (springdoc) — not yet implemented

Completed plans live in [docs/plans/completed/](docs/plans/completed/): prompt-injection defense, coverage-to-80%, adaptive-cleaning + multi-recipe, and the multi-recipe implementation notes — all implemented.

### Runbooks
- [docs/runbooks/prompt-evaluation.md](docs/runbooks/prompt-evaluation.md) — iterative Gemini prompt quality loop (uses debug dump flags)
- [docs/prompt-improvement-log.md](docs/prompt-improvement-log.md) — activity log for prompt-evaluation iterations

### Deployment / CI
- [docs/CI_CD_WORKFLOW.md](docs/CI_CD_WORKFLOW.md) — CI/CD pipeline, versioning, troubleshooting
- [docs/deployment/strategy.md](docs/deployment/strategy.md) — deployment strategy (4-phase, dispatch to sar-infra)
- [docs/deployment/rollback.md](docs/deployment/rollback.md) — rollback procedures (git revert, gcloud, sar-infra redeploy)
- [docs/deployment/monitoring.md](docs/deployment/monitoring.md) — health checks, logs, metrics, alerts
- [docs/deployment/production-readiness.md](docs/deployment/production-readiness.md) — Workload Identity, branch protection, security checklist
- [docs/metrics.md](docs/metrics.md) — HTML-preprocessing metrics via Actuator/Micrometer

### Reference / setup
- [docs/create-admin-instructions.md](docs/create-admin-instructions.md) — create an admin user (`admin:true` claim) and call the admin tier endpoint
- [docs/github-setup/deployment-setup.md](docs/github-setup/deployment-setup.md) — GitHub Actions deployment setup (secrets, service account)
- [docs/github-setup/branch-protection.md](docs/github-setup/branch-protection.md) — branch protection rules for `main`

### Module READMEs
- [extractor/README.md](extractor/README.md) — `:cookbook` REST API module
- [extractor/scripts/README.md](extractor/scripts/README.md) — legacy/local Docker build & version scripts
- [recipe-sdk/README.md](recipe-sdk/README.md) — recipe models, YAML parser/serializer, validation

### Archive
- [docs/archive/](docs/archive/README.md) — superseded docs, `.aki-plan/` scratch analysis, and all progress logs (with a one-line index)

## Technology Stack

### Core Technologies
- **Java**: 25 (toolchain enforced, required for Spring Boot 4)
- **Gradle**: 9.2.0
- **Spring Boot**: 4.0.1 (Spring Framework 7.x)
- **Spring Dependency Management**: 1.1.7
- **GraalVM Native Tools**: 0.11.3 (for GraalVM 25+)

### Key Dependencies
- **Jackson**: 3.0.3 (YAML/JSON processing, group ID: tools.jackson.*)
- **Google Cloud Firestore**: 3.33.4
- **Firebase Admin**: 9.7.0
- **Google Cloud Storage**: 2.60.0
- **Google Cloud KMS**: 2.30.0
- **Google Cloud Tasks**: Client library for async job processing

### Testing
- **JUnit**: 5.10.1
- **Mockito**: 5.18.0
- **Testcontainers**: 1.19.3
- **WireMock**: 3.3.1

### Quality & Security
- **JaCoCo**: 0.8.11 (code coverage, 40% minimum enforced)
- **OWASP Dependency Check**: 10.0.4 (security scanning)

### CI/CD
- **GitHub Actions**: Automated deployment pipeline
- **Java Version (CI/CD)**: 25 (for builds)
- **Target Platform**: linux/amd64

## Development Notes

### GraalVM Native Image

Production builds use GraalVM native compilation for:
- Fast startup time (<1 second vs ~5 seconds JVM)
- Low memory footprint (~200MB vs ~500MB JVM)
- Smaller Docker images

Challenges:
- Reflection requires hints (see `recipe-sdk/src/main/resources/META-INF/native-image/`)
- Build time is slow (10-15 minutes)
- Requires high memory (12GB+ for build)

### Adding Reflection Metadata (fixing `MissingReflectionRegistrationError`)

When a class fails in native image with `Cannot reflectively invoke constructor`, add it to:
`extractor/src/main/resources/META-INF/native-image/reflect-config.json`

**Common case — Jakarta `ConstraintValidator` implementations** (instantiated reflectively by Hibernate Validator):
```json
{
  "name": "net.shamansoft.cookbook.validation.MyValidator",
  "methods": [{ "name": "<init>", "parameterTypes": [] }]
}
```

**General case** (Jackson serialization, Spring beans, etc.):
```json
{
  "name": "net.shamansoft.cookbook.some.MyClass",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
}
```

GraalVM's error message always includes the exact JSON snippet to add — paste it into `reflect-config.json`. Unit tests cannot validate this fix; only a full `nativeCompile` or Cloud Run deployment confirms it.

### Code Owner

All changes require approval from `@khisamutdinov` (see `.github/CODEOWNERS`)

### Branch Protection

- `main` branch is protected
- Requires PR approval
- All tests must pass before merge
- Merging to `main` triggers automatic deployment to production

### Spring Boot 4 Migration

The application has been migrated from Spring Boot 3.5.9 to Spring Boot 4.0.1:

**Key Changes:**
- Java 25 required (Spring Framework 7.x baseline)
- Jakarta EE 11 with Servlet 6.1
- Modular starters: spring-boot-starter-webmvc (replacing spring-boot-starter-web)
- Jackson 3.x with new group IDs (tools.jackson.* instead of com.fasterxml.jackson.*)
- GraalVM 25+ for native images
- Testing annotations: @MockitoBean/@MockitoSpyBean (replacing @MockBean/@SpyBean)
- Package relocations: javax.annotation → jakarta.annotation

**Breaking Changes:**
- TestRestTemplate requires @AutoConfigureTestRestTemplate annotation
- Health probes (liveness/readiness) enabled by default in Actuator
- jackson-datatype-jsr310 removed (integrated into jackson-databind in Jackson 3)

**Native Image Build:**
- GraalVM 25+ container requires x86-64-v3 CPU support
- Local native builds not supported on Apple Silicon/ARM Macs
- Native image compilation happens in CI/CD pipeline on x86-64-v3 hardware
- Local development uses JVM builds (./gradlew :cookbook:bootRun)

The Spring Boot 4 migration progress log is archived at `docs/archive/progress/progress-20260125-spring-boot-4-migration.txt`.