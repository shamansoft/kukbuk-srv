# sar-srv — Cookbook Service

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen)
![Gradle](https://img.shields.io/badge/Gradle-9.2.0-blue)

Spring Boot (Kotlin/Java) backend for the **SAR / MyKukBuk** recipe product. It extracts structured
recipes from web pages, free-text descriptions, and YouTube videos using Google Gemini AI, enforces
per-user quotas (entitlements), and stores recipes in the user's Google Drive or Dropbox. Deployed
as a GraalVM native image on Google Cloud Run.

It is the backend consumed by **sar-ext** (browser extension) and **sar-kmp** (Kotlin Multiplatform
client). Infrastructure lives in **sar-infra** (OpenTofu/Terraform).

> For day-to-day status see [STATUS.md](STATUS.md). All docs are listed under
> [Documentation](#documentation) below.

## Modules

| Path | Gradle name | Purpose |
|---|---|---|
| `extractor/` | `:cookbook` | Spring Boot REST API — extraction, entitlements, storage, YouTube |
| `recipe-sdk/` | `:recipe-sdk` | Shared recipe models, YAML parse/serialize, validation |

> **Note:** `extractor/` is renamed to `:cookbook` in `settings.gradle`. Use `:cookbook` for Gradle
> tasks; use `extractor/` for file paths.

## Tech stack

- **Java 25** (toolchain enforced), **Spring Boot 4.0.1** (Spring Framework 7.x), **Gradle 9.2.0**
- **Google Gemini** (`gemini-3.1-flash-lite`) for recipe extraction
- **GraalVM native image** for production (fast cold start, low memory)
- **Firestore** for the recipe cache, profiles and quotas; **Google Drive** or **Dropbox** for the
  user's recipe files; **Firebase Auth** for identity
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

Run a single test class with `./gradlew :cookbook:test --tests CookbookControllerTest`. Docker and
native-image builds: [extractor/scripts/README.md](extractor/scripts/README.md) and
[extractor/README.md](extractor/README.md).

## CI/CD

Every PR runs unit + integration tests, coverage, and an OWASP dependency scan. Merging to `main`
triggers `deploy.yml`, which tests, builds a GraalVM native image, pushes to
`gcr.io/kukbuk-tf/cookbook`, then dispatches a `repository_dispatch` event to
[`shamansoft/sar-infra`](https://github.com/shamansoft/sar-infra) where the actual Terraform apply
and health check run.

- [CI/CD Workflow Guide](docs/CI_CD_WORKFLOW.md)
- [Deployment Strategy](docs/deployment/strategy.md) · [Rollback](docs/deployment/rollback.md) · [Monitoring](docs/deployment/monitoring.md) · [Production Readiness](docs/deployment/production-readiness.md)

## Documentation

Status: [STATUS.md](STATUS.md). Superseded docs: [docs/archive/](docs/archive/README.md).

**Specs / RFCs**
- [Entitlements and quotas](docs/specs/entitlement.md) (as built) · [design history](docs/rfc/entitlement.md)
- [Debug endpoint flags](docs/specs/debug-flags.md) · [Gemini tuning parameters](docs/specs/gemini-tuning.md)

**Plans**
- Active: [OpenAPI YAML generation](docs/plans/20260315-openapi-yaml-generation.md)
- Completed: [docs/plans/completed/](docs/plans/completed/) (extractor on gemini-3.1-flash-lite,
  relative image URLs, prompt-injection defense, coverage to 80%, adaptive cleaning + multi-recipe)

**Runbooks**
- [Dropbox integration rollout](docs/runbooks/dropbox-integration.md)
- [Prompt evaluation loop](docs/runbooks/prompt-evaluation.md) · [prompt improvement log](docs/prompt-improvement-log.md)

**Deployment / CI**
- [CI/CD workflow](docs/CI_CD_WORKFLOW.md) · [deployment strategy](docs/deployment/strategy.md) ·
  [rollback](docs/deployment/rollback.md) · [monitoring](docs/deployment/monitoring.md) ·
  [production readiness](docs/deployment/production-readiness.md)
- [GitHub deployment setup](docs/github-setup/deployment-setup.md) · [branch protection](docs/github-setup/branch-protection.md)
- [HTML preprocessing metrics](docs/metrics.md)

**Setup / reference**
- [Create an admin user](docs/create-admin-instructions.md)

**Modules**
- [extractor/](extractor/README.md) (`:cookbook` REST API) · [extractor/scripts/](extractor/scripts/README.md) ·
  [recipe-sdk/](recipe-sdk/README.md)

## Contributing

1. Branch from `main`, make changes with tests.
2. Open a PR — automated checks must pass.
3. Request review from `@khisamutdinov` (code owner).
4. Merge to `main` triggers automatic deployment to production.
