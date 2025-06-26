# Kukbuk Server - Recipe Extractor

[![PR Validation](https://github.com/username/kukbuk-srv/actions/workflows/pr-validation.yml/badge.svg)](https://github.com/username/kukbuk-srv/actions/workflows/pr-validation.yml)
[![codecov](https://codecov.io/gh/username/kukbuk-srv/branch/main/graph/badge.svg)](https://codecov.io/gh/username/kukbuk-srv)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

A Spring Boot service that extracts recipe data from web pages using AI (Google Gemini) and stores them in Google Drive.

## Features

- 🤖 **AI-Powered Recipe Extraction**: Uses Google Gemini 2.0 Flash to convert HTML content to structured YAML recipes
- 📁 **Google Drive Integration**: Automatically stores extracted recipes in your Google Drive
- 🔐 **OAuth Authentication**: Secure authentication using Google OAuth 2.0
- 📊 **Recipe Schema Validation**: Follows a comprehensive recipe schema (v1.0.0)
- 🗜️ **Content Compression**: Supports Base64 compressed HTML input
- 🌐 **URL Fetching**: Can extract content directly from URLs
- 🔍 **Recipe Detection**: AI determines if content is actually a recipe
- 📝 **Transliteration**: Generates clean file names from recipe titles

## API Endpoints

### POST /recipe

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
- `X-S-AUTH-TOKEN`: Google OAuth access token (optional, required for Drive storage)

**Query Parameters:**
- `compression`: Set to "none" for uncompressed HTML (default: auto-detect Base64)

**Response:**
```json
{
  "url": "https://...",
  "title": "Recipe Title",
  "driveFileId": "abc123",           // Present if stored in Drive
  "driveFileUrl": "https://...",     // Present if stored in Drive  
  "isRecipe": true
}
```

## Building and Running

### Prerequisites

- Java 21+
- Docker (for integration tests)
- Google Cloud Project with Gemini API and Drive API enabled

### Configuration

Set the following environment variables or update `application.yaml`:

```yaml
cookbook:
  gemini:
    api-key: "YOUR_GEMINI_API_KEY"
    base-url: "https://generativelanguage.googleapis.com/v1beta"
  google:
    oauth-id: "YOUR_GOOGLE_OAUTH_CLIENT_ID"
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

The project uses GitHub Actions for continuous integration:

- **PR Validation**: Runs on every pull request
  - Unit tests (`./gradlew test`)
  - Integration tests (`./gradlew intTest`) 
  - Code coverage verification
  - Security vulnerability scanning
  - Coverage reporting to Codecov

- **Branch Protection**: Main branch is protected
  - Requires PR approval
  - Requires all tests to pass
  - Requires up-to-date branch

### Workflow Files

- `../.github/workflows/pr-validation.yml`: Main CI pipeline (at repository root)
- `../.github/branch-protection.md`: Branch protection setup guide (at repository root)

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
                    │ Google Drive API │              
                    └──────────────────┘              
```

### Key Components

- **CookbookController**: REST API endpoint
- **GeminiRestTransformer**: AI-powered HTML to YAML conversion
- **GoogleDriveService**: File storage and management  
- **TokenRestService**: OAuth token validation
- **CompressorHTMLBase64**: Content compression/decompression

## Recipe Schema

Extracted recipes follow a structured YAML schema (v1.0.0):

```yaml
schema_version: "1.0.0"
recipe_version: "1.0"
title: "Recipe Title"
description: "Recipe description"
servings: 4
prep_time: "15m"
cook_time: "30m"
total_time: "45m"
ingredients:
  - name: "Ingredient name"
    amount: 1
    unit: "cup"
instructions:
  - step: 1
    description: "Instruction text"
isRecipe: true
```

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

## License

[Add your license information here]