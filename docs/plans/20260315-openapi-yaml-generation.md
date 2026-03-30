# OpenAPI YAML Generation via Build Process

## Overview

Add build-time OpenAPI YAML generation to the `:cookbook` module using the `springdoc-openapi-gradle-plugin`. The plugin starts a Spring Boot context, calls `/v3/api-docs.yaml`, and writes the spec to `build/openapi.yaml` as a build artifact.

**Goal**: Produce a versioned `openapi.yaml` artifact per build for downstream consumers (client SDK generation, documentation publishing, API contract testing).

**Out of scope**: Swagger UI at runtime in production (springdoc UI dependency added but disabled via config for non-local profiles).

## Context (from discovery)

- Build config: `extractor/build.gradle.kts`, `gradle/libs.versions.toml`
- Controllers to annotate: `RecipeController`, `YouTubeController`, `StorageController`, `UserProfileController`, `MediaController`, `MiscController`
- DTOs: `dto/` package — `Request`, `CustomRecipeRequest`, `RecipeResponse`, `RecipeListResponse`, `RecipeDto`, `QuotaErrorResponse`, `ErrorResponse`, `StorageConnectionRequest`, `StorageConnectionResponse`, `StorageStatusResponse`, `UserProfileResponseDto`
- Spring Boot: 4.0.1 — requires springdoc-openapi with Spring Boot 4 compatibility (verify latest 2.x/3.x release)
- No existing OpenAPI setup; no springdoc dependency present

## Development Approach

- Complete each task fully before moving to the next
- All tests must pass before starting next task
- Update this plan when scope changes

## Testing Strategy

- Unit tests: verify OpenAPI config bean is correct, annotations are syntactically valid (compile-time)
- Integration test: `@SpringBootTest` assertion that `/v3/api-docs` returns HTTP 200 with expected paths
- Build-level: `./gradlew generateOpenApiDocs` produces `build/openapi.yaml`

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document blockers with ⚠️ prefix

## Implementation Steps

### Task 1: Add springdoc-openapi dependency and Gradle plugin

- [ ] Check latest springdoc-openapi version compatible with Spring Boot 4.0.1 (look for `2.9.x` or `3.x` releases)
- [ ] Add `springdoc-openapi` version to `gradle/libs.versions.toml` under `[versions]`
- [ ] Add `springdoc-openapi-starter-webmvc-ui` library alias to `[libraries]` in `libs.versions.toml`
- [ ] Add `org.springdoc.openapi-gradle-plugin` plugin alias to `[plugins]` in `libs.versions.toml`
- [ ] Add `implementation(libs.springdoc.openapi.starter)` to `extractor/build.gradle.kts` dependencies
- [ ] Apply `alias(libs.plugins.springdoc.openapi)` in `extractor/build.gradle.kts` plugins block
- [ ] Configure `openApi { }` block in `build.gradle.kts`: set `outputDir`, `outputFileName = "openapi.yaml"`, `apiDocsUrl = "http://localhost:8080/v3/api-docs.yaml"`, `forkProperties = ["-Dspring.profiles.active=local,openapi"]`
- [ ] Run `./gradlew :cookbook:build --dry-run` to verify plugin is recognized
- [ ] Run `./gradlew :cookbook:test` — tests must pass before next task

### Task 2: Create OpenAPI configuration class and `openapi` Spring profile

- [ ] Create `extractor/src/main/java/net/shamansoft/cookbook/config/OpenApiConfig.java`
  - `@Configuration` + `@Bean OpenAPI openAPI()` with title "Cookbook API", version from `@Value("${cookbook.version:dev}")`, description, contact
  - Define `SecurityScheme` named `"firebase-auth"` (HTTP Bearer, format JWT)
  - Apply security requirement globally
- [ ] Create `extractor/src/main/resources/application-openapi.yaml` profile to override settings that block startup during doc generation:
  - Disable Firestore: `cookbook.firestore.enabled: false`
  - Set dummy Gemini key: `cookbook.gemini.api-key: dummy`
  - Set `springdoc.api-docs.enabled: true`, `springdoc.swagger-ui.enabled: false`
- [ ] Add to default `application.yaml`: `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false` (disabled unless openapi profile active)
- [ ] Write unit test `OpenApiConfigTest` — verifies `OpenAPI` bean is not null and has expected title/security scheme
- [ ] Run `./gradlew :cookbook:test` — must pass before next task

### Task 3: Annotate RecipeController

- [ ] Add `@Tag(name = "Recipes")` at class level
- [ ] `GET /v1/recipes`: `@Operation(summary = "List recipes")`, `@ApiResponse(200, RecipeListResponse)`, `@ApiResponse(401)`
- [ ] `GET /v1/recipes/{id}`: `@Operation(summary = "Get recipe by ID")`, `@ApiResponse(200, RecipeDto)`, `@ApiResponse(401)`, `@ApiResponse(404)`
- [ ] `POST /v1/recipes`: `@Operation(summary = "Create recipe from HTML")`, `@ApiResponse(200, RecipeResponse)`, `@ApiResponse(401)`, `@ApiResponse(429, QuotaErrorResponse)`, document `X-Quota-*` response headers via `@Header`
- [ ] `POST /v1/recipes/custom`: `@Operation(summary = "Create recipe from description")`, same response docs as above
- [ ] Add `@Parameter(hidden = true)` to injected request attributes (`userId`, `userTier`) to suppress from spec
- [ ] Run `./gradlew :cookbook:test` — must pass before next task

### Task 4: Annotate YouTubeController and internal handler

- [ ] Add `@Tag(name = "YouTube Recipes")` at class level
- [ ] `POST /v1/recipes/youtube`: `@Operation`, `@ApiResponse(200, YouTubeRecipeResponse)`, `@ApiResponse(401)`, `@ApiResponse(429)`
- [ ] `GET /v1/recipes/youtube/{jobId}`: `@Operation`, `@ApiResponse(200, YouTubeJobStatusResponse)`, `@ApiResponse(401)`, `@ApiResponse(404)`
- [ ] Mark `YouTubeTaskHandler` (internal) with `@Hidden` — exclude from public spec
- [ ] Run `./gradlew :cookbook:test` — must pass before next task

### Task 5: Annotate StorageController, UserProfileController, MediaController

- [ ] `StorageController`: `@Tag(name = "Storage")`, annotate connect/disconnect/status endpoints
- [ ] `UserProfileController`: `@Tag(name = "User Profile")`, annotate GET/POST profile
- [ ] `MediaController`: `@Tag(name = "Media")`, annotate proxy endpoint with `Cache-Control` response header doc
- [ ] `MiscController`: `@Hidden` (health/debug endpoints excluded from public spec)
- [ ] Run `./gradlew :cookbook:test` — must pass before next task

### Task 6: Annotate DTOs with `@Schema`

- [ ] `Request` — add `@Schema` to fields: `html` (nullable, base64+gzip), `url` (required), `title` (nullable)
- [ ] `CustomRecipeRequest` — `description` (required), `title`, `url`
- [ ] `RecipeResponse` — `recipeId`, `isRecipe`, `recipes`
- [ ] `RecipeListResponse` — `recipes`, `nextPageToken`, `count`
- [ ] `QuotaErrorResponse` — `error`, `remainingQuota`, `remainingCredits`, `resetsAt`
- [ ] `ErrorResponse` — all fields
- [ ] Storage and UserProfile DTOs
- [ ] Run `./gradlew :cookbook:test` — must pass before next task

### Task 7: Configure and validate build-time generation

- [ ] Verify `openApi { }` block in `build.gradle.kts` is complete (outputDir = `"$buildDir/openapi"`, fork properties for openapi profile)
- [ ] Add `generateOpenApiDocs` as a dependency of `:cookbook:assemble` or as a standalone CI task (not blocking normal build — keep it optional/manual or CI-only)
- [ ] Run `./gradlew :cookbook:generateOpenApiDocs` — confirm `build/openapi/openapi.yaml` is created
- [ ] Inspect generated YAML: verify all expected paths present (`/v1/recipes`, `/v1/recipes/{id}`, `/v1/recipes/custom`, `/v1/recipes/youtube`, `/v1/recipes/youtube/{jobId}`, `/v1/user/profile`, `/v1/storage/google-drive/*`, `/v1/media/{driveFileId}`)
- [ ] Add integration test `OpenApiEndpointTest` (in `intTest`): `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate GET /v3/api-docs` → HTTP 200 when `openapi` profile active
- [ ] Run `./gradlew :cookbook:intTest` — must pass before next task

### Task 8: Add `.gitignore` and CI/CD integration

- [ ] Add `build/openapi/` to `.gitignore` (generated artifact, not committed)
- [ ] Add step to `.github/workflows/pr-validation.yml`: run `./gradlew :cookbook:generateOpenApiDocs` and upload `build/openapi/openapi.yaml` as a GitHub Actions artifact (for review on PRs)
- [ ] Add equivalent step to `.github/workflows/deploy.yml`: generate and attach to GitHub Release as `openapi-{version}.yaml`
- [ ] Run `./gradlew :cookbook:test` — must pass

### Task 9: Verify acceptance criteria

- [ ] `./gradlew :cookbook:generateOpenApiDocs` succeeds from clean checkout with only `COOKBOOK_GEMINI_API_KEY=dummy` env (the openapi profile handles the rest)
- [ ] Generated `openapi.yaml` is valid — pipe through `swagger-cli validate` or equivalent (or inspect manually)
- [ ] All expected endpoint paths and tags present in the YAML
- [ ] Security scheme (Bearer JWT) defined at spec root
- [ ] Run full test suite: `./gradlew test`
- [ ] Run integration tests: `./gradlew :cookbook:intTest`

### Task 10: Update documentation

- [ ] Update `CLAUDE.md` — add `generateOpenApiDocs` to Common Development Tasks section
- [ ] Update `extractor/README.md` if it exists with instructions for generating the spec

## Technical Details

### springdoc-openapi Gradle plugin configuration

```kotlin
// extractor/build.gradle.kts
openApi {
    outputDir.set(file("$buildDir/openapi"))
    outputFileName.set("openapi.yaml")
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    forkProperties.set(mapOf(
        "spring.profiles.active" to "local,openapi",
        "spring.main.lazy-initialization" to "true"
    ))
}
```

### OpenAPI profile overrides (`application-openapi.yaml`)

Disables Firestore and sets dummy credentials so the app starts without real GCP access during doc generation. Real beans that depend on GCP must be conditionally loaded or mocked — check if `FirestoreRecipeRepository` and `FirebaseAuthFilter` require conditional startup handling.

### Security scheme

```java
@SecurityScheme(
    name = "firebase-auth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
```

Applied globally via `@SecurityRequirement(name = "firebase-auth")` on each `@Operation` or via global config.

### Known complexity: Spring Boot startup for doc generation

The plugin forks a JVM and starts Spring Boot. This requires:
- Firestore disabled (or mocked) — `FirebaseApp` initialization needs `GOOGLE_APPLICATION_CREDENTIALS` or must be conditional
- Gemini API key — can be `dummy` since no requests are made
- Cloud Tasks client — may need disabling or stubbing

The `openapi` profile must suppress all GCP clients that fail without credentials. This is the most likely area of friction.

## Post-Completion

**Manual verification**:
- Open generated `openapi.yaml` in Swagger Editor or Stoplight to visually verify the spec looks correct
- Check that quota response headers (`X-Quota-Outcome`, `X-Quota-Remaining`, `X-Quota-Resets-At`) appear in the 200 response docs for recipe endpoints

**External consumers** (if applicable):
- If any client SDK is generated from this spec, update its generation pipeline to consume `openapi-{version}.yaml` from GitHub Releases
