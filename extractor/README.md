# Kukbuk Server - Recipe Extractor

[![PR Validation](https://github.com/username/kukbuk-srv/actions/workflows/pr-validation.yml/badge.svg)](https://github.com/username/kukbuk-srv/actions/workflows/pr-validation.yml)
[![codecov](https://codecov.io/gh/username/kukbuk-srv/branch/main/graph/badge.svg)](https://codecov.io/gh/username/kukbuk-srv)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)

A Spring Boot service that extracts recipe data from web pages using AI (Google Gemini) and stores them in Firestore.

## Features

- 🤖 **AI-Powered Recipe Extraction**: Uses Google Gemini 2.5 Flash Lite to convert HTML content to structured JSON
  recipes
- 📁 **Firestore Integration**: Automatically stores extracted recipes in Firestore NoSQL database
- 🔐 **OAuth Authentication**: Secure authentication using Google OAuth 2.0
- 📊 **Recipe Schema Validation**: Follows a comprehensive recipe schema (v1.0.0) with strict JSON validation
- 🔄 **Retry Logic**: Automatic retry with validation feedback for better extraction accuracy
- 💾 **Recipe Caching**: In-memory caching with configurable timeouts for improved performance
- 🔗 **Content Deduplication**: SHA-256 hashing to prevent duplicate recipe storage
- 🗜️ **Content Compression**: Supports Base64 compressed HTML input
- 🌐 **URL Fetching**: Can extract content directly from URLs
- 🔍 **Recipe Detection**: AI determines if content is actually a recipe
- 📝 **Transliteration**: Generates clean file names from recipe titles

## API Endpoints

### POST /v1/recipes

Extracts recipe data from HTML content.

**Request:**
```json
{
  "html": "<html>...</html>",  // Optional: HTML content (raw or Base64 compressed)
  "title": "Recipe Title",     // Required: Recipe title
  "url": "https://..."         // Required: Source URL (used if html not provided)
}
```

**Headers:**
- `X-S-AUTH-TOKEN`: Google OAuth access token (optional, required for Firestore storage)

**Query Parameters:**
- `compression`: Set to "none" for uncompressed HTML (default: auto-detect Base64)

**Response:**
```json
{
  "url": "https://...",
  "title": "Recipe Title",
  "recipeId": "abc123",              // Present if stored in Firestore
  "isRecipe": true
}
```

### GET /v1/recipes

Lists all recipes with pagination.

**Query Parameters:**

- `pageSize`: Number of recipes per page (1-100, default: 20)
- `pageToken`: Token for next page (from previous response)

**Response:**

```json
{
  "recipes": [
    ...
  ],
  "nextPageToken": "token123"
  // Present if more results available
}
```

### GET /v1/recipes/{id}

Retrieves a single recipe by its Drive file ID.

**Response:**

- Recipe data as JSON
- Cache-Control: 1 hour

## Building and Running

### Prerequisites

- Java 25+ (required for Spring Boot 4)
- Docker (for integration tests)
- Google Cloud Project with Gemini API and Drive API enabled
- GraalVM 25+ (for native image builds)

### Configuration

Set the following environment variables or update `application.yaml`:

```yaml
cookbook:
  gemini:
    api-key: "YOUR_GEMINI_API_KEY"
    base-url: "https://generativelanguage.googleapis.com/v1beta"
    model: "gemini-2.5-flash-lite"
    temperature: 0.1
    top-p: 0.8
    max-output-tokens: 4096
  google:
    oauth-id: "YOUR_GOOGLE_OAUTH_CLIENT_ID"
  drive:
    folder-name: "_save_a_recipe"

# Recipe validation retry (default: 1)
  RECIPE_LLM_RETRY=1

        # GCP configuration
  GOOGLE_CLOUD_PROJECT_ID=your-project-id
  FIRESTORE_ENABLED=true
```

### Build

```bash
./gradlew build
```

### Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests (requires Docker)
./gradlew intTest

# All tests with coverage
./gradlew test intTest jacocoTestReport
```

### Run Application

```bash
./gradlew bootRun
```

## Testing

### Test Structure

- **Unit Tests** (`src/test/java`): Fast, isolated tests with mocks
- **Integration Tests** (`src/intTest/java`): Full integration tests with Testcontainers and WireMock

### Integration Test Setup

Integration tests use:
- **Testcontainers**: For WireMock container management
- **WireMock**: Mock Google services (Auth, Gemini AI, Drive API)
- **Spring Boot Test**: Full application context testing

Example integration test:
```java
@Test
void testPostRecipeWithGoogleIntegration() {
    // Tests full flow: Auth → Gemini AI → Google Drive storage
    // Uses WireMock testcontainers for external service mocking
}
```

## Code Coverage

| Type | Coverage | Target |
|------|----------|--------|
| **Overall** | ![Coverage](https://img.shields.io/badge/coverage-40%25-yellow) | 40%+ |
| **New Code** | ![Coverage](https://img.shields.io/badge/coverage-60%25-green) | 60%+ |

### Coverage Reports

Coverage reports are generated with JaCoCo:

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Coverage Verification

```bash
./gradlew jacocoTestCoverageVerification
```

Fails build if coverage drops below 40%.

## CI/CD

### GitHub Actions

The project uses GitHub Actions for continuous integration and deployment:

- **PR Validation**: Runs on every pull request
  - Unit tests (`./gradlew test`)
  - Integration tests (`./gradlew intTest`)
  - Code coverage verification
  - Security vulnerability scanning
  - Coverage reporting with badges

- **Automated Deployment**: Deploys to Google Cloud Run on main branch
  - Automated testing before deployment
  - Version auto-increment
  - Native Docker image build
  - Health check verification
  - Git tagging and GitHub releases

- **Branch Protection**: Main branch is protected
  - Requires PR approval
  - Requires all tests to pass
  - Requires up-to-date branch

### Documentation

For detailed CI/CD documentation, see:
- `../docs/CI_CD_WORKFLOW.md` - Complete CI/CD workflow guide
- `../docs/TESTING_WORKFLOW.md` - Testing workflow on feature branches
- `../docs/deployment/` - Deployment strategy, rollback, and monitoring
- `../docs/github-setup/` - GitHub Actions and branch protection setup

## Security

- **Dependency Scanning**: OWASP dependency check runs on all PRs
- **Vulnerability Threshold**: Build fails on CVSS 7.0+ vulnerabilities  
- **Secret Management**: API keys via environment variables
- **OAuth Security**: Google OAuth 2.0 token validation

## Architecture

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Web Client    │───▶│  Controller  │───▶│  Gemini AI API  │
└─────────────────┘    └──────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │    Firestore     │
                    └──────────────────┘
```

### Key Components

- **RecipeController**: REST API endpoints for recipe operations (GET, POST)
- **RecipeService**: Orchestrates recipe extraction and storage workflow
- **GeminiClient**: HTTP client for Gemini API communication
- **GeminiRestTransformer**: AI-powered HTML to JSON conversion
- **RequestBuilder**: Builds Gemini API requests with schema and retry feedback
- **RecipeValidationService**: Validates recipes against JSON schema
- **ValidatingTransformerService**: Orchestrates transformation with retry logic
- **RecipeStoreService**: Caching layer for recipe data
- **ContentHashService**: Deduplication via content hashing
- **FirestoreRecipeRepository**: Recipe storage and management in Firestore
- **TokenRestService**: OAuth token validation
- **CompressorHTMLBase64**: Content compression/decompression
- **HtmlExtractor**: HTML cleanup and preparation

For detailed Firestore schema, see `../terraform/firestore-schema.md`.

## Recipe Schema

Extracted recipes follow a structured JSON schema (v1.0.0):

```json
{
  "schema_version": "1.0.0",
  "recipe_version": "1.0.0",
  "metadata": {
    "title": "Recipe Title",
    "source": "https://...",
    "language": "en",
    "servings": 4,
    "prep_time": "15m",
    "cook_time": "30m",
    "total_time": "45m",
    "difficulty": "easy"
  },
  "description": "Recipe description",
  "ingredients": [
    {
      "item": "Ingredient name",
      "amount": 1.0,
      "unit": "cup",
      "optional": false,
      "component": "main"
    }
  ],
  "instructions": [
    {
      "step": 1,
      "description": "Instruction text",
      "time": "5m"
    }
  ],
  "is_recipe": true
}
```

Schema is strictly validated with automatic retry on validation errors.

## Native Image

Build GraalVM native image:

```bash
./gradlew nativeCompile
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass and coverage targets are met
5. Submit a pull request

All PRs must:
- ✅ Pass unit and integration tests
- ✅ Meet code coverage requirements (40% overall, 60% new code)
- ✅ Pass security vulnerability scanning
- ✅ Receive code review approval

## Spring Boot 4 Migration

This application has been migrated from Spring Boot 3.5.9 to Spring Boot 4.0.1.

### Key Changes

- Spring Boot: 3.5.9 → 4.0.1
- Java: 21 → 25 (toolchain enforced)
- Jackson: 2.18.2 → 3.0.3 (new group IDs: tools.jackson.*)
- GraalVM Build Tools: 0.10.5 → 0.11.3
- Modular Starters: spring-boot-starter-web → spring-boot-starter-webmvc
- Spring Framework: 6.x → 7.x

### Migration Impact

- Jakarta EE 11 baseline with Servlet 6.1
- javax.annotation.PostConstruct → jakarta.annotation.PostConstruct
- Testing: @MockBean/@SpyBean → @MockitoBean/@MockitoSpyBean
- TestRestTemplate requires @AutoConfigureTestRestTemplate
- Health probes (liveness/readiness) enabled by default
- GraalVM 25+ required for native images

### Native Image Build

GraalVM 25+ native image builds require x86-64-v3 CPU support:
- Apple Silicon/ARM Macs cannot build native images locally via Docker
- Native image builds run in CI/CD pipeline on x86-64-v3 hardware
- Local development uses JVM builds only (./gradlew :cookbook:bootRun)
- Production uses native images deployed via GitHub Actions

For detailed migration documentation, see `docs/plans/20260125-spring-boot-4-migration.md`.

## License

[Add your license information here]