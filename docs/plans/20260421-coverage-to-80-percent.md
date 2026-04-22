# Plan: Bump Code Coverage to 80%

## Overview

Improve overall test coverage from 77% to 80% (14,395 total instructions) through comprehensive test enhancements across all packages. Focus on both low-coverage areas and closing gaps in moderately-tested components.

## Context

**Current Coverage**: 77% (3,224 missed instructions out of 14,395)

**Coverage by Package** (sorted by priority):
- `debug`: 0% (2 classes, 948 instructions) — development-only endpoint
- `repository`: 2% (3 classes, 771 instructions) — Firestore repository layer  
- `config`: 48% (5 classes, 231 instructions) — Spring configuration, setup code
- `security`: 54% (2 classes, 183 instructions) — Firebase auth filter, token encryption
- `client`: 77% (20 classes, 353 instructions) — Google Drive API client
- `service.gemini`: 96% (6 classes, 91 instructions) — Gemini AI integration
- `service`: 91% (28 classes, 484 instructions) — Recipe business logic
- `entitlement`: 91% (15 classes, 91 instructions) — Quota/tier enforcement
- `controller`: 90% (5 classes, 48 instructions) — REST endpoints
- All others: 88-100% — Well-tested

**Gap Analysis**: 
- Largest gaps: `debug` (948), `repository` (771), `client` (353), `service` (484)
- Easiest wins: `config` (need ~40% of 231 = 92 instructions), `security` (need ~46% of 183 = 84 instructions)

## Development Approach

**Testing Approach**: TDD (test-first)
- For each component: write test cases covering all branches/paths FIRST
- Then verify implementation satisfies the test cases
- Run tests after each task completes — all tests must pass before moving to next
- Focus on:
  - Happy path scenarios
  - Error cases and edge conditions  
  - Null/empty input handling
  - Timeout and circuit breaker logic (especially in entitlement)

**Test Organization**:
- New tests in same directories as existing tests: `extractor/src/test/java/`
- Follow existing naming: `ClassNameTest.java` for unit tests
- Use parameterized tests (JUnit 5 `@ParameterizedTest`) for table-driven test cases
- Mock Firestore, HTTP clients, Spring context as needed

**Coverage Target**: 80% minimum, aim for 82% to provide buffer

## Implementation Steps

### Task 1: Debug endpoint coverage (0% → 50%+)
- [x] Analyze `net.shamansoft.cookbook.debug` package structure — identify 2 classes
- [x] Write test for debug controller (happy path + error cases)
- [x] Write test for debug DTOs / request/response models
- [x] Run tests, verify both classes covered
- [x] Run full test suite — must pass before next task

### Task 2: Repository layer coverage (2% → 40%+)
- [ ] Write tests for `FirestoreRecipeRepository` CRUD operations
  - [ ] Write tests: create, read, update, delete paths
  - [ ] Write tests: pagination, filtering, empty result cases
- [ ] Write tests for `FirestoreUserProfileRepository` operations
  - [ ] Write tests: load profile, update profile, create on first access
- [ ] Write tests for `FirestoreYouTubeJobRepository` job tracking
- [ ] Run tests, verify significant improvement in repository coverage
- [ ] Run full test suite — must pass before next task

### Task 3: Config package coverage (48% → 75%+)
- [ ] Write tests for `FirebaseConfigTest` edge cases
  - [ ] Config loading success
  - [ ] Missing credentials fallback
  - [ ] Invalid project ID handling
- [ ] Write tests for `EntitlementPlanConfig` validation + initialization
  - [ ] Valid config with all tier×operation pairs
  - [ ] Missing tier entry → startup fails
  - [ ] Missing operation entry → startup fails
- [ ] Write tests for `StringToCompressionConverter` (already has good coverage, verify)
- [ ] Run tests, verify config package coverage improved
- [ ] Run full test suite — must pass before next task

### Task 4: Security package coverage (54% → 80%+)
- [ ] Write tests for `FirebaseAuthFilter` JWT claim extraction
  - [ ] Valid JWT with tier claim → `userTier` attribute set
  - [ ] Valid JWT without tier claim → `userTier` absent (falls back to FREE)
  - [ ] Invalid JWT → request rejected
  - [ ] Malformed tier claim → logged as WARN, attribute not set
- [ ] Write tests for `TokenEncryptionService` KMS integration
  - [ ] Encrypt/decrypt round-trip
  - [ ] KMS unavailable → handled gracefully
  - [ ] Empty/null token handling
- [ ] Run tests, verify security package coverage boosted
- [ ] Run full test suite — must pass before next task

### Task 5: Client package coverage (77% → 85%+)
- [ ] Analyze `GoogleDrive` and `GoogleDriveClient` classes for untested paths
- [ ] Write tests for Google Drive API error handling
  - [ ] Rate limiting (429 status) → retry logic
  - [ ] Authentication failures (401/403)
  - [ ] Network timeouts
- [ ] Write tests for OAuth token refresh edge cases
  - [ ] Expired token → refresh triggered
  - [ ] Refresh token missing/invalid
- [ ] Run tests, verify client package improvement
- [ ] Run full test suite — must pass before next task

### Task 6: Service package coverage (91% → 95%+)
- [ ] Identify uncovered branches in `RecipeService`
  - [ ] HTML extraction error cases
  - [ ] Drive upload failures
  - [ ] Timeout scenarios
- [ ] Write tests for YouTube service edge cases (if `YouTubeService` exists)
- [ ] Write tests for `RecipeStoreService` timeout scenarios
- [ ] Run tests, verify service package at 95%+
- [ ] Run full test suite — must pass before next task

### Task 7: Comprehensive branch coverage (Gemini, entitlement, validation)
- [ ] Analyze `GeminiRestTransformer` branch coverage
  - [ ] Success path (is_recipe=true)
  - [ ] Not-a-recipe path (is_recipe=false)
  - [ ] Validation error feedback loops
  - [ ] Timeout/error responses
- [ ] Ensure `EntitlementAspect` covers all outcome branches (ALLOWED_PAID, CIRCUIT_OPEN, etc.)
- [ ] Verify `FolderNameValidator` has edge cases (already at 100%, spot-check)
- [ ] Run tests, verify high coverage maintained
- [ ] Run full test suite — must pass before next task

### Task 8: Verification and final push
- [ ] Run full coverage report: `./gradlew :cookbook:jacocoTestReport`
- [ ] Verify overall coverage ≥ 80% (target 82%)
- [ ] Identify any remaining gaps < 80% in any package
- [ ] If gaps exist: Write targeted tests to close them
- [ ] Run full test suite + linter — all passing
- [ ] Commit test improvements with message: "test: improve coverage to 80%"

## Testing Strategy

**Unit Tests**: 
- Mock all external dependencies (Firestore, HTTP clients, Spring context)
- Focus on method-level logic and branch coverage
- Use `@ExtendWith(MockitoExtension.class)` for clean mocking
- Parameterized tests for multiple input scenarios

**Integration Tests** (if needed):
- Use Testcontainers for Firestore emulation if feasible
- Otherwise mock at repo layer and test service logic in isolation

**Coverage Goals**:
- **Target**: 80% overall (14,716+ instructions covered)
- **Minimum for each package**: 
  - `debug` → 50%+ (development-only, lower priority)
  - `repository` → 40%+ (complex, integration-heavy)
  - `config` → 75%+ (configuration validation critical)
  - `security` → 80%+ (security-critical path)
  - All others → maintain current level or improve

## Post-Completion

**Manual validation**:
- Run `./gradlew :cookbook:test :cookbook:jacocoTestReport` to confirm 80%+ coverage
- Review coverage report at `extractor/build/reports/jacoco/test/html/index.html`
- Verify no regressions in existing tests

**Deployment**:
- Merge PR with improved tests
- CI/CD enforces 40% coverage minimum (we'll be well above)
- Coverage badge (if configured) will update automatically

---

## Notes

- **Debug package**: Ignore if development-only (check if used in prod). Can remain low coverage if not deployed.
- **Repository gaps**: May be integration-test heavy; consider if unit tests or intTests are more appropriate.
- **Brand-new tests**: Follow existing test patterns (e.g., `RecipeServiceCreateRecipeTest` naming, `@SpringBootTest` for full context tests).
- **CI/CD**: Current minimum is 40%; this work gets us to 80%+, providing safety buffer for future changes.
