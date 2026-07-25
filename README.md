# sar-srv — Cookbook Service

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen)
![Gradle](https://img.shields.io/badge/Gradle-9.2.0-blue)

Spring Boot (Kotlin/Java) backend for the **SAR / MyKukBuk** recipe product. It extracts structured
recipes from web pages, free-text descriptions, and YouTube videos using Google Gemini AI, enforces
per-user quotas (entitlements), and stores recipes in Firestore + the user's Google Drive. Deployed
as a GraalVM native image on Google Cloud Run.

It is the backend consumed by **sar-ext** (browser extension) and **sar-kmp** (Kotlin Multiplatform
client). Infrastructure lives in **sar-infra** (OpenTofu/Terraform).

> For day-to-day status see [STATUS.md](STATUS.md). For the full documentation index, build/test
> commands, architecture, and agent guidance see [CLAUDE.md](CLAUDE.md).

## Modules

| Path | Gradle name | Purpose |
|---|---|---|
| `extractor/` | `:cookbook` | Spring Boot REST API — extraction, entitlements, storage, YouTube |
| `recipe-sdk/` | `:recipe-sdk` | Shared recipe models, YAML parse/serialize, validation |
| `token-broker/` | (Node.js) | Google Cloud Function for OAuth token handling |

> **Note:** `extractor/` is renamed to `:cookbook` in `settings.gradle`. Use `:cookbook` for Gradle
> tasks; use `extractor/` for file paths.

## Tech stack

- **Java 25** (toolchain enforced), **Spring Boot 4.0.1** (Spring Framework 7.x), **Gradle 9.2.0**
- **Google Gemini** (`gemini-2.5-flash-lite`) for recipe extraction
- **GraalVM native image** for production (fast cold start, low memory)
- **Firestore** for storage; **Google Drive** for recipe files; **Firebase Auth** for identity
- **GitHub Actions** CI/CD → builds native image, pushes to GCR, dispatches deploy to `sar-infra`
- **Cloud Run** (`us-west1`, project `kukbuk-tf`)

## Quick start

```bash
# Build everything
./gradlew build

# Run the API locally (JVM) — requires COOKBOOK_GEMINI_API_KEY
./gradlew :cookbook:bootRun

# Unit tests / integration tests (Testcontainers + WireMock, needs Docker)
./gradlew :cookbook:test
./gradlew :cookbook:intTest

# Coverage check
./gradlew :cookbook:checkCoverage
```

Required env for local development:

```bash
COOKBOOK_GEMINI_API_KEY=your_gemini_api_key
COOKBOOK_GOOGLE_OAUTH_ID=your_google_oauth_client_id
```

See [CLAUDE.md](CLAUDE.md) for the complete build/run/test reference, Docker builds, and
native-image notes.

## CI/CD

Every PR runs unit + integration tests, coverage, and an OWASP dependency scan. Merging to `main`
triggers `deploy.yml`, which tests, builds a GraalVM native image, pushes to
`gcr.io/kukbuk-tf/cookbook`, then dispatches a `repository_dispatch` event to
[`shamansoft/sar-infra`](https://github.com/shamansoft/sar-infra) where the actual Terraform apply
and health check run.

- [CI/CD Workflow Guide](docs/CI_CD_WORKFLOW.md)
- [Deployment Strategy](docs/deployment/strategy.md) · [Rollback](docs/deployment/rollback.md) · [Monitoring](docs/deployment/monitoring.md) · [Production Readiness](docs/deployment/production-readiness.md)

## Documentation

[CLAUDE.md](CLAUDE.md) contains the full **Documentation Index** (architecture, specs/RFCs, plans,
runbooks, deployment/CI, setup, module READMEs, and the archive). Start there.

## Contributing

1. Branch from `main`, make changes with tests.
2. Open a PR — automated checks must pass.
3. Request review from `@khisamutdinov` (code owner).
4. Merge to `main` triggers automatic deployment to production.
