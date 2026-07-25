# Documentation Archive

_Last updated: 2026-06-19_

Superseded, completed, or historical documents kept for reference. Nothing here is current
guidance — see [STATUS.md](../../STATUS.md) and [CLAUDE.md](../../CLAUDE.md) for the live picture.

## Superseded / outdated docs

| File | What it was | Why archived |
|---|---|---|
| `native-image-verification.md` | GraalVM native-image verification process for the Spring Boot 4 migration | Migration is complete; native build is now a routine CI step. Build facts live in CLAUDE.md / docs/deployment. |
| `TESTING_WORKFLOW.md` | Guide to testing the CI/CD pipeline on feature branches | Describes the pre-split workflow (Phase 3 = direct Cloud Run deploy, `skip_deploy`/`skip_finalize` inputs). Deploy now dispatches to sar-infra. Superseded by `docs/CI_CD_WORKFLOW.md`. |
| `deployment-historical/prod-ready-workflow-migration.md` | One-time plan (2025-11-08) to lock down `deploy.yml` for production | The described changes were applied long ago; the current `deploy.yml` already dispatches to sar-infra. Historical. |
| `stackdriver-metrics-plan.md` | Plan to add `micrometer-registry-stackdriver` Cloud Monitoring export | Metrics export to Cloud Monitoring is already in place (see entitlement RFC §4.11, docs/metrics.md). Plan complete. Was at `.opencode/plans/metrics.md`. |
| `20260327-prompt-injection-defense.active-duplicate.md` | Duplicate copy of the prompt-injection-defense plan that lingered in `docs/plans/` | Identical to `docs/plans/completed/20260327-prompt-injection-defense.md` (canonical). Feature is implemented. |

## `.aki-plan/` scratch analysis

Ad-hoc engineering analysis notes (an old scratch directory). All describe work that has since
landed or been resolved.

| File | Topic |
|---|---|
| `aki-plan/refactor-review.md` | HtmlCleaner refactoring review |
| `aki-plan/refactor-review-final.md` | HtmlCleaner refactoring — final review |
| `aki-plan/rest-client.md` | WebClient → RestClient + virtual-threads migration (done) |
| `aki-plan/json-schema.md` | Gemini structured-output (responseSchema) migration (done) |
| `aki-plan/integration-test-failure-analysis.md` | Integration test failure investigation |
| `aki-plan/why-not-failing-before.md` | Investigation: why a test wasn't failing pre-refactor |
| `aki-plan/ANSWER-why-not-failing-before.md` | Answer to the above |

## Progress logs (`progress/`)

Append-only execution logs from past automated/agent runs. One file per task. Kept for history;
outcomes are reflected in STATUS.md milestones and the relevant completed plans.

| File | Task |
|---|---|
| `progress/progress-20260125-spring-boot-4-migration.txt` | Spring Boot 3 → 4 migration |
| `progress/progress-20260201-recipe-post-processing.txt` | Recipe post-processing feature |
| `progress/progress-20260206-youtube-recipe-extraction.txt` | YouTube recipe extraction |
| `progress/progress-plan-add-transformation-post-processing-after-model-tra.txt` | Post-model transformation step |
| `progress/progress-plan-create-a-loop-of-iterations-of-prompt-improvements.txt` | Prompt-improvement loop |
| `progress/progress-2026-03-08-entitlement-service.txt` | Entitlement service implementation |
| `progress/progress-2026-05-29-update-tier-endpoint.txt` | Admin update-tier endpoint |
| `progress/progress-20260319-entitlement-pr77-fixes.txt` | Entitlement PR #77 review fixes |
| `progress/progress-20260327-terraform-split-sar-srv-1-pipeline.txt` | Terraform split — pipeline (cross-repo to sar-infra) |
| `progress/progress-20260327-terraform-split-sar-srv-2-cutover.txt` | Terraform split — cutover |
| `progress/progress-20260421-coverage-to-80-percent.txt` | Coverage-to-80% run |
