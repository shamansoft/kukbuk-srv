# Plan: Automated Prompt Improvement Loop

## Overview

Implement an automated iterative improvement loop for the Gemini AI prompt (`extractor/src/main/resources/prompt.md`).
Each iteration:

1. Evaluates recipe extraction quality against Gordon Ramsay test recipe
2. Scores multiple quality dimensions (0-10 scale)
3. Provides recommendations for prompt improvements
4. Automatically improves prompt based on previous iteration feedback
5. Exits early when quality score ≥ 9.0/10

**Problem it solves:** Systematic, data-driven prompt engineering with objective quality metrics

**Key benefits:**

- Quantitative evaluation of prompt effectiveness
- Automated improvement suggestions based on actual extraction results
- Version history of prompt evolution
- Reproducible quality assessment

**Integration:** Uses existing DebugController and DumpService infrastructure for intermediate state inspection

## Context

**Files/components involved:**

- `extractor/src/main/resources/prompt.md` - Target prompt for improvement
- `extractor/src/main/resources/recipe-schema-1.0.0.json` - Schema for validation
- `docs/prompt-improvement-log.md` - Activity log tracking iterations, scores, recommendations (NEW)
- `extractor/src/main/resources/prompt_v{date}-iter{n}.md` - Versioned backups (NEW)
- `/tmp/sar-srv/dumps/{date}/` - Dump output location for validation
- `extractor/src/main/java/net/shamansoft/cookbook/debug/DebugController.java` - Test endpoint
- `extractor/src/main/java/net/shamansoft/cookbook/service/DumpService.java` - File dumping service

**Test Recipe URL:** `https://www.gordonramsay.com/gr/recipes/chickenthighswithbacongravy/`

- Complex recipe with multiple components (chicken + gravy)
- Tests component grouping, ingredient parsing, instruction clarity
- Known structure for validation baseline

**Related patterns:**

- Debug dump flags (from `20260201-debug-dump-flags-implementation.md`)
- LLM response dumping (from `20260201-dump-llm-response-flag.md`)

**Dependencies:**

- Spring Boot local profile must be active
- Gemini API key configured (`COOKBOOK_GEMINI_API_KEY`)
- `/tmp/sar-srv/dumps/` directory writable

## Development Approach

- **Testing approach**: Regular (iterative evaluation loop, not TDD)
- **Exit condition**: Overall score ≥ 9.0/10 OR all 10 iterations completed
- **Iteration model**: Each task is self-contained - review → improve → test → evaluate → log → commit
- **Early exit**: If a task achieves score ≥ 9.0, mark remaining tasks as skipped and exit
- **CRITICAL: every task MUST include validation tests** as part of evaluation checklist
- **CRITICAL: all Spring Boot tests must pass** before committing each iteration
- **CRITICAL: update activity log immediately** after each iteration with structured data

## Testing Strategy

**Validation tests** (manual evaluation per iteration):

- HTML data preservation (extracted vs cleaned)
- Schema compliance
- Data loss detection (compare source HTML to extracted data)
- Hallucination detection (verify no invented data)
- Ingredient completeness (all ingredients extracted)
- Ingredient deduplication (no duplicates)
- Ingredient categorization (component grouping)
- Instruction correctness (logical, complete, properly ordered)
- Metadata accuracy (title, servings, times)

**Unit tests**:

- Existing Spring Boot test suite must pass before each commit
- Run: `./gradlew :cookbook:test`

**No E2E tests**: This is a prompt improvement task, not UI changes

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add `⚠️ SCORE < 9.0 - continuing` or `✅ SCORE ≥ 9.0 - EXIT LOOP` annotations
- Document iteration scores in activity log
- Update plan if improvement loop needs to be stopped early
- Keep plan in sync with actual iterations completed

## What Goes Where

**Implementation Steps** (`[ ]` checkboxes):

- Tasks achievable within this codebase
- Prompt improvements, builds, tests, evaluations, commits
- Activity log updates

**Post-Completion** (no checkboxes):

- Manual review of final prompt quality
- Comparison of iteration 1 vs final iteration
- Decision on deploying improved prompt to production

## Implementation Steps

<!--
Each task follows identical structure:
1. Read activity log (if exists)
2. Check exit condition (score ≥ 9.0 from previous iteration)
3. If continuing: backup prompt → improve → build → run → test → validate → score → log → commit
4. If exiting: mark remaining tasks as skipped

Test recipe: https://www.gordonramsay.com/gr/recipes/chickenthighswithbacongravy/
Each iteration uses NEW session ID for correlation
-->

### Task 1: Iteration 1 - Baseline Evaluation

- [x] create `docs/prompt-improvement-log.md` with initial structure (iteration, timestamp, session-id, scores,
  analysis, recommendations)
- [x] backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter1.md`
- [x] note: using current prompt.md as-is for baseline (no improvements yet)
- [x] build cookbook: `./gradlew :cookbook:build`
- [x] verify build succeeds and all tests pass
- [x] start Spring Boot with local profile: `./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'` in
  background
- [x] wait for application startup (check for "Started CookbookApplication" in logs OR curl health endpoint)
- [x] generate new UUID for X-Session-Id header, save it as SESSION_ID_1
- [x] run curl command with SESSION_ID_1 against http://localhost:8080/debug/v1/recipes (Gordon Ramsay recipe)
- [x] verify 200 response received
- [x] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [x] read `{SESSION_ID_1}-extracted-html-*.html` file
- [x] read `{SESSION_ID_1}-cleaned-html-*.html` file
- [x] compare extracted vs cleaned HTML - validate no critical data removed (score 0-10)
- [x] read `{SESSION_ID_1}-result-yaml-*.yaml` file
- [x] validate YAML structure is well-formed (score 0-10)
- [x] read `{SESSION_ID_1}-llm-response-*.json` file - check raw Gemini response
- [x] validate schema compliance - check all required fields present (score 0-10)
- [x] validate data loss - compare source HTML to extracted recipe, ensure all ingredients/steps captured (score 0-10)
- [x] validate hallucinations - verify no invented data (ingredients not in source, fabricated instructions) (score
  0-10)
- [x] validate ingredients completeness - all ingredients from source extracted (score 0-10)
- [x] validate ingredients deduplication - no duplicate ingredients in list (score 0-10)
- [x] validate ingredients categorization - component grouping matches HTML sections (score 0-10)
- [x] validate instruction correctness - logical order, complete steps, proper timing/temperature (score 0-10)
- [x] validate metadata accuracy - title, servings, description match source (score 0-10)
- [x] calculate overall score (average of all 10 dimension scores)
- [x] update `docs/prompt-improvement-log.md` with iteration 1 entry (timestamp, SESSION_ID_1, all scores, what was
  good, what was bad, recommendations for iteration 2)
- [x] stop Spring Boot application
- [x] run unit tests: `./gradlew :cookbook:test` - must pass
- [x] commit changes with message: "prompt-improvement: iteration 1 baseline evaluation (score: X.X/10)"
- [x] check exit condition: if overall score ≥ 9.0, mark tasks 2-10 as skipped; otherwise continue to task 2

### Task 2: Iteration 2 - First Improvement

- [x] read `docs/prompt-improvement-log.md` to get iteration 1 scores and recommendations
- [x] check exit condition: if iteration 1 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [x] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter2.md`
- [x] improve `extractor/src/main/resources/prompt.md` based on iteration 1 recommendations (focus on lowest-scoring
  dimensions)
- [x] build cookbook: `./gradlew :cookbook:build`
- [x] verify build succeeds and all tests pass
- [x] start Spring Boot with local profile in background
- [x] wait for application startup
- [x] generate new UUID for X-Session-Id header, save it as SESSION_ID_2
- [x] run curl command with SESSION_ID_2 against http://localhost:8080/debug/v1/recipes
- [x] verify 200 response received
- [x] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [x] read `{SESSION_ID_2}-extracted-html-*.html` file
- [x] read `{SESSION_ID_2}-cleaned-html-*.html` file
- [x] compare extracted vs cleaned HTML - validate no critical data removed (score 0-10)
- [x] read `{SESSION_ID_2}-result-yaml-*.yaml` file
- [x] validate YAML structure is well-formed (score 0-10)
- [x] read `{SESSION_ID_2}-llm-response-*.json` file
- [x] validate schema compliance (score 0-10)
- [x] validate data loss (score 0-10)
- [x] validate hallucinations (score 0-10)
- [x] validate ingredients completeness (score 0-10)
- [x] validate ingredients deduplication (score 0-10)
- [x] validate ingredients categorization (score 0-10)
- [x] validate instruction correctness (score 0-10)
- [x] validate metadata accuracy (score 0-10)
- [x] calculate overall score (average of all 10 dimension scores)
- [x] update `docs/prompt-improvement-log.md` with iteration 2 entry (timestamp, SESSION_ID_2, all scores, comparison to
  iteration 1: improvements/degradations, what was good, what was bad, recommendations for iteration 3)
- [x] stop Spring Boot application
- [x] run unit tests: `./gradlew :cookbook:test` - must pass
- [x] commit changes with message: "prompt-improvement: iteration 2 (score: X.X/10, delta: +/-Y.Y)"
- [x] check exit condition: if overall score ≥ 9.0, mark tasks 3-10 as skipped; otherwise continue to task 3

### Task 3: Iteration 3 - Second Improvement

- [x] read `docs/prompt-improvement-log.md` to get iteration 2 scores and recommendations
- [x] check exit condition: if iteration 2 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [x] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter3.md`
- [x] improve `extractor/src/main/resources/prompt.md` based on iteration 2 recommendations
- [x] build cookbook: `./gradlew :cookbook:build`
- [x] verify build succeeds and all tests pass
- [x] start Spring Boot with local profile in background
- [x] wait for application startup
- [x] generate new UUID for X-Session-Id header, save it as SESSION_ID_3
- [x] run curl command with SESSION_ID_3 against http://localhost:8080/debug/v1/recipes
- [x] verify 200 response received
- [x] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [x] read `{SESSION_ID_3}-extracted-html-*.html` file
- [x] read `{SESSION_ID_3}-cleaned-html-*.html` file
- [x] compare extracted vs cleaned HTML (score 0-10)
- [x] read `{SESSION_ID_3}-result-yaml-*.yaml` file
- [x] validate YAML structure (score 0-10)
- [x] read `{SESSION_ID_3}-llm-response-*.json` file
- [x] validate schema compliance (score 0-10)
- [x] validate data loss (score 0-10)
- [x] validate hallucinations (score 0-10)
- [x] validate ingredients completeness (score 0-10)
- [x] validate ingredients deduplication (score 0-10)
- [x] validate ingredients categorization (score 0-10)
- [x] validate instruction correctness (score 0-10)
- [x] validate metadata accuracy (score 0-10)
- [x] calculate overall score
- [x] update `docs/prompt-improvement-log.md` with iteration 3 entry (timestamp, SESSION_ID_3, all scores, comparison to
  iteration 2, recommendations for iteration 4)
- [x] stop Spring Boot application
- [x] run unit tests: `./gradlew :cookbook:test` - must pass
- [x] commit changes with message: "prompt-improvement: iteration 3 (score: X.X/10, delta: +/-Y.Y)"
- [x] check exit condition: if overall score ≥ 9.0, mark tasks 4-10 as skipped; otherwise continue to task 4

### Task 4: Iteration 4 - Third Improvement

- [x] read `docs/prompt-improvement-log.md` to get iteration 3 scores and recommendations
- [x] check exit condition: if iteration 3 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [x] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter4.md`
- [x] improve `extractor/src/main/resources/prompt.md` based on iteration 3 recommendations
- [x] build cookbook: `./gradlew :cookbook:build`
- [x] verify build succeeds and all tests pass
- [x] start Spring Boot with local profile in background
- [x] wait for application startup
- [x] generate new UUID for X-Session-Id header, save it as SESSION_ID_4
- [x] run curl command with SESSION_ID_4 against http://localhost:8080/debug/v1/recipes
- [x] verify 200 response received
- [x] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [x] read all dump files for SESSION_ID_4
- [x] perform all 10 validation checks (score each 0-10)
- [x] calculate overall score
- [x] update `docs/prompt-improvement-log.md` with iteration 4 entry (timestamp, SESSION_ID_4, scores, comparison,
  recommendations)
- [x] stop Spring Boot application
- [x] run unit tests: `./gradlew :cookbook:test` - must pass
- [x] commit changes with message: "prompt-improvement: iteration 4 (score: X.X/10, delta: +/-Y.Y)"
- [x] check exit condition: if overall score ≥ 9.0, mark tasks 5-10 as skipped; otherwise continue to task 5

### Task 5: Iteration 5 - Fourth Improvement

- [ ] read `docs/prompt-improvement-log.md` to get iteration 4 scores and recommendations
- [ ] check exit condition: if iteration 4 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter5.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 4 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_5
- [ ] run curl command with SESSION_ID_5 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_5
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 5 entry (timestamp, SESSION_ID_5, scores, comparison,
  recommendations)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 5 (score: X.X/10, delta: +/-Y.Y)"
- [ ] check exit condition: if overall score ≥ 9.0, mark tasks 6-10 as skipped; otherwise continue to task 6

### Task 6: Iteration 6 - Fifth Improvement

- [ ] read `docs/prompt-improvement-log.md` to get iteration 5 scores and recommendations
- [ ] check exit condition: if iteration 5 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter6.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 5 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_6
- [ ] run curl command with SESSION_ID_6 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_6
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 6 entry (timestamp, SESSION_ID_6, scores, comparison,
  recommendations)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 6 (score: X.X/10, delta: +/-Y.Y)"
- [ ] check exit condition: if overall score ≥ 9.0, mark tasks 7-10 as skipped; otherwise continue to task 7

### Task 7: Iteration 7 - Sixth Improvement

- [ ] read `docs/prompt-improvement-log.md` to get iteration 6 scores and recommendations
- [ ] check exit condition: if iteration 6 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter7.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 6 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_7
- [ ] run curl command with SESSION_ID_7 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_7
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 7 entry (timestamp, SESSION_ID_7, scores, comparison,
  recommendations)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 7 (score: X.X/10, delta: +/-Y.Y)"
- [ ] check exit condition: if overall score ≥ 9.0, mark tasks 8-10 as skipped; otherwise continue to task 8

### Task 8: Iteration 8 - Seventh Improvement

- [ ] read `docs/prompt-improvement-log.md` to get iteration 7 scores and recommendations
- [ ] check exit condition: if iteration 7 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter8.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 7 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_8
- [ ] run curl command with SESSION_ID_8 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_8
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 8 entry (timestamp, SESSION_ID_8, scores, comparison,
  recommendations)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 8 (score: X.X/10, delta: +/-Y.Y)"
- [ ] check exit condition: if overall score ≥ 9.0, mark tasks 9-10 as skipped; otherwise continue to task 9

### Task 9: Iteration 9 - Eighth Improvement

- [ ] read `docs/prompt-improvement-log.md` to get iteration 8 scores and recommendations
- [ ] check exit condition: if iteration 8 score ≥ 9.0, skip this task and mark remaining tasks skipped
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter9.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 8 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_9
- [ ] run curl command with SESSION_ID_9 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_9
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 9 entry (timestamp, SESSION_ID_9, scores, comparison,
  recommendations)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 9 (score: X.X/10, delta: +/-Y.Y)"
- [ ] check exit condition: if overall score ≥ 9.0, mark task 10 as skipped; otherwise continue to task 10

### Task 10: Iteration 10 - Final Improvement (Last Attempt)

- [ ] read `docs/prompt-improvement-log.md` to get iteration 9 scores and recommendations
- [ ] check exit condition: if iteration 9 score ≥ 9.0, skip this task
- [ ] if continuing: backup current prompt to `extractor/src/main/resources/prompt_v20260201-iter10.md`
- [ ] improve `extractor/src/main/resources/prompt.md` based on iteration 9 recommendations
- [ ] build cookbook: `./gradlew :cookbook:build`
- [ ] verify build succeeds and all tests pass
- [ ] start Spring Boot with local profile in background
- [ ] wait for application startup
- [ ] generate new UUID for X-Session-Id header, save it as SESSION_ID_10
- [ ] run curl command with SESSION_ID_10 against http://localhost:8080/debug/v1/recipes
- [ ] verify 200 response received
- [ ] navigate to `/tmp/sar-srv/dumps/{today-date}/` directory
- [ ] read all dump files for SESSION_ID_10
- [ ] perform all 10 validation checks (score each 0-10)
- [ ] calculate overall score
- [ ] update `docs/prompt-improvement-log.md` with iteration 10 entry (timestamp, SESSION_ID_10, scores, comparison,
  final summary)
- [ ] stop Spring Boot application
- [ ] run unit tests: `./gradlew :cookbook:test` - must pass
- [ ] commit changes with message: "prompt-improvement: iteration 10 FINAL (score: X.X/10, delta: +/-Y.Y)"
- [ ] add final summary to activity log comparing iteration 1 vs iteration 10 (total improvement, best iteration,
  recommendations for deployment)

### Task 11: [Final] Update documentation

- [ ] review `docs/prompt-improvement-log.md` for completeness
- [ ] verify all iteration scores are recorded
- [ ] verify all recommendations are documented
- [ ] add summary section at top of activity log with key findings
- [ ] commit final documentation updates

## Technical Details

**Activity Log Structure** (`docs/prompt-improvement-log.md`):

```markdown
# Prompt Improvement Activity Log

## Summary

- **Total iterations**: X
- **Best score**: X.X/10 (iteration Y)
- **Final score**: X.X/10
- **Overall improvement**: +/-X.X points
- **Exit reason**: [Score ≥ 9.0 | Completed all 10 iterations]

## Iteration Template

### Iteration N

- **Timestamp**: YYYY-MM-DD HH:MM:SS
- **Session ID**: {uuid}
- **Prompt Version**: prompt_v20260201-iterN.md

**Scores** (0-10 scale):

1. HTML Data Preservation: X.X/10
2. YAML Structure: X.X/10
3. Schema Compliance: X.X/10
4. Data Loss: X.X/10 (higher = less loss)
5. Hallucinations: X.X/10 (higher = fewer hallucinations)
6. Ingredients Completeness: X.X/10
7. Ingredients Deduplication: X.X/10
8. Ingredients Categorization: X.X/10
9. Instruction Correctness: X.X/10
10. Metadata Accuracy: X.X/10

**Overall Score**: X.X/10

**What was good**:

- [Bullet points of strengths]

**What was bad**:

- [Bullet points of weaknesses]

**Comparison to previous iteration**:

- [Improvements]: +X.X in dimension Y
- [Degradations]: -X.X in dimension Z

**Recommendations for next iteration**:

1. [Specific prompt improvement suggestion based on lowest scores]
2. [Specific prompt improvement suggestion]
3. [Specific prompt improvement suggestion]
```

**Curl Command Template**:

```bash
SESSION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
echo "Session ID: $SESSION_ID"

curl --location 'http://localhost:8080/debug/v1/recipes' \
--header 'Content-Type: application/json' \
--header "X-Session-Id: $SESSION_ID" \
--data '{
  "url": "https://www.gordonramsay.com/gr/recipes/chickenthighswithbacongravy/",
  "returnFormat": "yaml",
  "cleanHtml": "auto",
  "skipCache": true,
  "dumpRawHtml": false,
  "dumpExtractedHtml": true,
  "dumpCleanedHtml": true,
  "dumpResultJson": true,
  "dumpResultYaml": true,
  "dumpLLMResponse": true,
  "verbose": false
}'
```

**Validation Dimensions** (each scored 0-10):

1. **HTML Data Preservation**: Compare extracted vs cleaned HTML - no valuable ingredients/steps removed
2. **YAML Structure**: Well-formed, parseable, proper indentation
3. **Schema Compliance**: All required fields present, correct types
4. **Data Loss**: All ingredients and steps from source HTML captured
5. **Hallucinations**: No invented data (verify against source HTML)
6. **Ingredients Completeness**: All ingredients extracted (count vs source)
7. **Ingredients Deduplication**: No duplicate entries
8. **Ingredients Categorization**: Proper component grouping (Chicken vs Gravy)
9. **Instruction Correctness**: Logical order, complete, proper timing/temps
10. **Metadata Accuracy**: Title, servings, description match source

**Score Calculation**:

- Each dimension: 0-10 (10 = perfect)
- Overall score: Average of all 10 dimensions
- Exit threshold: 9.0/10 overall

## Post-Completion

*Items requiring manual intervention - no checkboxes, informational only*

**Manual review**:

- Compare baseline (iteration 1) vs final prompt side-by-side
- Review trend of scores across iterations - did it converge?
- Identify which prompt changes had biggest impact
- Decide if improved prompt should be deployed to production

**Production deployment considerations**:

- Test improved prompt against multiple recipes (not just Gordon Ramsay)
- Verify improvements generalize to different recipe structures
- Run regression tests with existing known-good recipes
- Update prompt version in comments/metadata

**Future improvements**:

- Expand test recipe set (Italian, Asian, vegan, baking, etc.)
- Automate scoring with LLM-based evaluation
- Track prompt token count (efficiency metric)
- A/B test prompts in production with sampling
