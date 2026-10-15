# sar-srv — Status
_Last updated: 2026-10-15_

## What this is
The Spring Boot backend for the SAR / MyKukBuk recipe product. It extracts structured recipes from
web pages, free-text descriptions, and YouTube videos using Google Gemini AI, enforces per-user
quotas (entitlements), and stores recipes in the user's Google Drive or Dropbox. It serves the
sar-ext browser extension and the sar-kmp client; infrastructure is owned by sar-infra.

## Tech stack
- **Language/runtime:** Java 25 (toolchain enforced), targeting GraalVM native image for prod
- **Framework:** Spring Boot 4.0.1 (Spring Framework 7.x), Gradle 9.2.0
- **Modules:** `extractor/` (Gradle `:cookbook`, the REST API) · `recipe-sdk/` (recipe models, YAML parse/serialize, validation)
- **LLM:** Google Gemini `gemini-3.1-flash-lite` via REST, thinking off, with JSON `responseSchema` structured output
- **Data/identity:** Firestore (recipe cache, profiles, YouTube jobs, quota windows), Google Drive or Dropbox (the user's recipe files), Firebase Auth (JWT, custom claims for tier/admin)
- **Async:** Google Cloud Tasks for YouTube extraction jobs
- **HTTP client:** Spring `RestClient` (migrated off WebClient) with virtual threads
- **Deploy:** GitHub Actions → GraalVM native image → GCR → `repository_dispatch` to sar-infra → Cloud Run (`us-west1`, project `kukbuk-tf`)
- **Quality:** JaCoCo (40% min enforced; currently ~80%), OWASP Dependency Check (fails on CVSS ≥ 7.0)

## Current status
- **Overall:** Mature and in production. Version `0.15.13-SNAPSHOT`. Actively maintained.
- **Implemented / working:**
  - Recipe extraction from HTML (`POST /v1/recipes`) and free-text (`POST /v1/recipes/custom`)
  - Multi-recipe extraction (a page yielding several recipes) + adaptive HTML cleaning chain
  - YouTube recipe extraction (async via Cloud Tasks: `POST /v1/recipes/youtube`, status `GET`)
  - Entitlement/quota system (FREE-tier daily limits, credits fallback, fail-open, 429 + headers)
  - Admin tier management (`PUT /v1/admin/users/{userId}/tier`, `admin:true` claim + AOP guard)
  - Recipe storage in Google Drive; Dropbox as a second backend (code merged, rollout pending, see below)
  - Recipe caching/dedup, Firestore persistence, Firebase auth
  - Relative recipe image URLs resolved to absolute at read time
  - Extraction quality on 39 English sample pages: 92% of ingredients fully right (63% before #94);
    11 non-English pages: 76%. Measured with `tools/extractor-tuner` in the sar umbrella folder
  - Post-LLM cleanup (`RecipeAdjustmentService`): units, empty quantities, ranges, thirds, nutrition
  - One retry for empty/truncated Gemini answers, then `422` instead of `500`
  - Prompt-injection defense (Gemini `systemInstruction` + XML-delimited user content)
  - GraalVM native build in CI, deployed to Cloud Run
- **In progress:**
  - Dropbox rollout: Dropbox app registration, secrets, deploy and device test — [runbook](docs/runbooks/dropbox-integration.md) (phases 2–6 open)
  - Build-time OpenAPI YAML generation (springdoc) — planned, not yet wired (see active plan)
- **Planned / backlog:**
  - Keep ingredient sections when the page has schema.org JSON-LD (lost today on JSON-LD pages; 5 of 11 sample pages with sections come out right)
  - OpenAPI spec artifact for downstream client SDK generation (sar-kmp/sar-ext)
  - Deferred entitlement items (idempotency keys, credits expiry, admin webhook, quota-status endpoint, Redis backend) — see entitlement RFC §9
  - Production alerts/dashboards in Terraform (monitoring doc lists recommended, not yet implemented)
- **Known issues:**
  - Local native-image builds not possible on Apple Silicon (x86-64-v3 required) — native builds run in CI only
  - JWT tier custom-claim staleness window (up to ~1h) is accepted behavior
  - Ingredient groups on JSON-LD pages change with any prompt edit; prompt changes must be measured, not eyeballed
  - gotovim-doma.ru sample returns "not a recipe" (HTML cleaning drops the recipe)

## Active work
| Item | Status | Doc |
|---|---|---|
| Dropbox storage rollout | Code merged (#88), app registration and deploy open | [docs/runbooks/dropbox-integration.md](docs/runbooks/dropbox-integration.md) |
| OpenAPI YAML generation via build | Planned | [docs/plans/20260315-openapi-yaml-generation.md](docs/plans/20260315-openapi-yaml-generation.md) |

## Recent milestones
- Extractor on `gemini-3.1-flash-lite` with required+nullable LLM schema fields, rewritten HTML and
  free-text prompts (keep the page's language), post-LLM cleanup, retry/422 for empty answers (#94)
  — [plan](docs/plans/completed/20261014-extractor-3.1-prompt.md)
- Relative recipe image URLs resolved to absolute (#93) — [plan](docs/plans/completed/20261009-resolve-relative-image-urls.md)
- Dropbox as a second recipe-storage backend (#88)
- Fixed UTF-8 encoding loss on new-recipe uploads to Google Drive (#91)
- Gemini generation-parameter tuning on the local debug endpoint (#90); `gemini-3.5-flash-lite` tried and reverted (#89)
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
See [README.md](README.md#quick-start). Quick reference:
```bash
./gradlew build                 # all modules
./gradlew :cookbook:bootRun     # run API locally (needs COOKBOOK_GEMINI_API_KEY)
./gradlew :cookbook:test        # unit tests
./gradlew :cookbook:intTest     # integration tests (Testcontainers + WireMock, Docker)
./gradlew :cookbook:checkCoverage
```

## Documentation
See the [documentation index in README.md](README.md#documentation).
