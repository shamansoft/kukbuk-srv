# sar-srv — Status
_Last updated: 2026-07-25_

## What this is
The Spring Boot backend for the SAR / MyKukBuk recipe product. It extracts structured recipes from
web pages, free-text descriptions, and YouTube videos using Google Gemini AI, enforces per-user
quotas (entitlements), and stores recipes in Firestore and the user's Google Drive. It serves the
sar-ext browser extension and the sar-kmp client; infrastructure is owned by sar-infra.

## Tech stack
- **Language/runtime:** Java 25 (toolchain enforced), targeting GraalVM native image for prod
- **Framework:** Spring Boot 4.0.1 (Spring Framework 7.x), Gradle 9.2.0
- **Modules:** `extractor/` (Gradle `:cookbook`, the REST API) · `recipe-sdk/` (recipe models, YAML parse/serialize, validation) · `token-broker/` (Node.js Cloud Function for OAuth tokens)
- **LLM:** Google Gemini `gemini-2.5-flash-lite` via REST, with JSON `responseSchema` structured output
- **Data/identity:** Firestore (recipes, profiles, YouTube jobs, quota windows), Google Drive (recipe files), Firebase Auth (JWT, custom claims for tier/admin)
- **Async:** Google Cloud Tasks for YouTube extraction jobs
- **HTTP client:** Spring `RestClient` (migrated off WebClient) with virtual threads
- **Deploy:** GitHub Actions → GraalVM native image → GCR → `repository_dispatch` to sar-infra → Cloud Run (`us-west1`, project `kukbuk-tf`)
- **Quality:** JaCoCo (40% min enforced; currently ~80%), OWASP Dependency Check (fails on CVSS ≥ 7.0)

## Current status
- **Overall:** Mature and in production. Version `0.15.10-SNAPSHOT`. Actively maintained.
- **Implemented / working:**
  - Recipe extraction from HTML (`POST /v1/recipes`) and free-text (`POST /v1/recipes/custom`)
  - Multi-recipe extraction (a page yielding several recipes) + adaptive HTML cleaning chain
  - YouTube recipe extraction (async via Cloud Tasks: `POST /v1/recipes/youtube`, status `GET`)
  - Entitlement/quota system (FREE-tier daily limits, credits fallback, fail-open, 429 + headers)
  - Admin tier management (`PUT /v1/admin/users/{userId}/tier`, `admin:true` claim + AOP guard)
  - Google Drive storage, recipe caching/dedup, Firestore persistence, Firebase auth
  - Prompt-injection defense (Gemini `systemInstruction` + XML-delimited user content)
  - GraalVM native build in CI, deployed to Cloud Run
- **In progress:**
  - Build-time OpenAPI YAML generation (springdoc) — planned, not yet wired (see active plan)
- **Planned / backlog:**
  - OpenAPI spec artifact for downstream client SDK generation (sar-kmp/sar-ext)
  - Deferred entitlement items (idempotency keys, credits expiry, admin webhook, quota-status endpoint, Redis backend) — see entitlement RFC §9
  - Production alerts/dashboards in Terraform (monitoring doc lists recommended, not yet implemented)
- **Known issues:**
  - Local native-image builds not possible on Apple Silicon (x86-64-v3 required) — native builds run in CI only
  - JWT tier custom-claim staleness window (up to ~1h) is accepted behavior

## Active work
| Item | Status | Doc |
|---|---|---|
| OpenAPI YAML generation via build | Planned | [docs/plans/20260315-openapi-yaml-generation.md](docs/plans/20260315-openapi-yaml-generation.md) |

## Recent milestones
- Fixed UTF-8 encoding loss on new-recipe uploads to Google Drive (non-ASCII text was mangled on write)
- Gemini generation-parameter tuning; model bumped to `gemini-3.5-flash-lite` then reverted to `gemini-2.5-flash-lite`
- Extraction prompts updated to preserve the source language instead of translating to English
- Spring Boot 3.5.9 → 4.0.1 migration (Java 21 → 25, Jackson 2 → 3, GraalVM 25)
- Recipe post-processing (deterministic fields after validation)
- YouTube recipe extraction (async Cloud Tasks pipeline)
- Entitlement service + admin update-tier endpoint
- Multi-recipe extraction + adaptive HTML cleaning chain (cache redesigned YAML → JSON)
- Prompt-injection defense (system instruction + delimiters)
- WebClient → RestClient migration; Gemini structured-output (responseSchema) migration
- Terraform/CD split: deploy now dispatches to sar-infra
- Test coverage raised to ~80%

## Build / run / test
See [CLAUDE.md](CLAUDE.md). Quick reference:
```bash
./gradlew build                 # all modules
./gradlew :cookbook:bootRun     # run API locally (needs COOKBOOK_GEMINI_API_KEY)
./gradlew :cookbook:test        # unit tests
./gradlew :cookbook:intTest     # integration tests (Testcontainers + WireMock, Docker)
./gradlew :cookbook:checkCoverage
```

## Documentation
See [CLAUDE.md](CLAUDE.md) for the full documentation index.
