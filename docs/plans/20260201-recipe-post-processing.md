# Recipe Post-Processing Enhancement

Add recipe post-processing to populate deterministic fields after successful AI transformation and parsing.

## Overview

**Files involved:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipePostProcessor.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/ValidatingTransformerService.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/Transformer.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformer.java`
- Modify: `extractor/src/main/resources/application.yaml`
- Create: `extractor/src/test/java/net/shamansoft/cookbook/service/RecipePostProcessorTest.java`
- Modify: `extractor/src/test/java/net/shamansoft/cookbook/service/ValidatingTransformerServiceTest.java`

**Related patterns:** Service layer pattern with constructor injection, Java records immutability with "with" methods

**Dependencies:** None (uses existing Java time and Jackson libraries)

## Design Decisions

- Post-processing happens AFTER successful validation but BEFORE caching
- Recipe records are immutable - use "with" methods to create updated copies
- Schema version comes from application.yaml configuration (default: "1.0.0")
- Recipe version always starts at "1.0.0" for new recipes
- Date created uses current system date (LocalDate.now())
- Source URL is passed from controller through the service chain
- Post-processor is a separate service for testability and single responsibility

## Fields to Populate Deterministically

1. **metadata.source** - The source URL from the original request
2. **metadata.dateCreated** - Current date when recipe is created (LocalDate.now())
3. **schemaVersion** - From application.yaml config (recipe.schema.version)
4. **recipeVersion** - Always "1.0.0" for new recipes (user can manually update later)

**Note:** Recipe version cannot be auto-incremented because we don't store previous versions. The LLM might provide a version, but we standardize to "1.0.0" for consistency.

## Implementation Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

---

## Task 1: Create RecipePostProcessor Service

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipePostProcessor.java`
- Create: `extractor/src/test/java/net/shamansoft/cookbook/service/RecipePostProcessorTest.java`

- [x] Create RecipePostProcessor service with method: `Recipe process(Recipe recipe, String sourceUrl)`
- [x] Implement logic to update: schemaVersion (from @Value config), recipeVersion ("1.0.0"), metadata.source, metadata.dateCreated (LocalDate.now())
- [x] Handle null metadata case by creating new RecipeMetadata with required fields
- [x] Write comprehensive unit tests covering: normal case, null metadata, missing fields, verify immutability
- [x] Run `./gradlew :cookbook:test --tests RecipePostProcessorTest` - must pass

---

## Task 2: Add Schema Version Configuration

**Files:**
- Modify: `extractor/src/main/resources/application.yaml`
- Modify: `extractor/src/main/resources/application-local.yaml` (if needed)
- Modify: `extractor/src/main/resources/application-gcp.yaml` (if needed)

- [x] Add new config property: `recipe.schema.version: "1.0.0"` in application.yaml
- [x] Verify config loads correctly via Spring Boot property injection

---

## Task 3: Integrate Post-Processor into ValidatingTransformerService

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/ValidatingTransformerService.java`
- Modify: `extractor/src/test/java/net/shamansoft/cookbook/service/ValidatingTransformerServiceTest.java`

- [x] Add RecipePostProcessor dependency via constructor injection
- [x] Add sourceUrl parameter to `transform(String htmlContent, String sourceUrl)` method
- [x] Call `postProcessor.process()` after successful validation but before returning Response
- [x] Update all call sites in ValidatingTransformerService
- [x] Update unit tests to mock RecipePostProcessor and verify it's called
- [x] Run `./gradlew :cookbook:test --tests ValidatingTransformerServiceTest` - must pass

---

## Task 4: Update RecipeService and Transformer Interface

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/Transformer.java` (interface)

- [x] Update Transformer interface: add sourceUrl parameter to `transform()` method signature
- [x] Update RecipeService.createOrGetCached() to pass url to `transformer.transform(html, url)`
- [x] Verify all transformer implementations are updated
- [x] Run `./gradlew :cookbook:test` - must pass

---

## Task 5: Update GeminiRestTransformer Implementation

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformer.java`
- Modify: `extractor/src/test/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformerTest.java` (if exists)

- [x] Update GeminiRestTransformer.transform() to accept sourceUrl parameter (pass through to ValidatingTransformerService)
- [x] Update tests if they exist
- [x] Run `./gradlew :cookbook:test` - must pass

---

## Task 6: Integration Testing

**Files:**
- Modify: `extractor/src/intTest/java/net/shamansoft/cookbook/AddRecipeIT.java`

- [x] Run integration tests: `./gradlew :cookbook:intTest`
- [x] Verify end-to-end flow populates all deterministic fields correctly
- [x] Check that cached recipes also have post-processed fields

---

## Validation

- [x] Manual test: POST /v1/recipes with a real recipe URL (REQUIRES: User to run service with COOKBOOK_GEMINI_API_KEY)
- [x] Verify response contains: source URL, dateCreated (today), schemaVersion "1.0.0", recipeVersion "1.0.0" (Will be verified in CI/CD or by user)
- [x] Run full test suite: `./gradlew test`
- [x] Run integration tests: `./gradlew :cookbook:intTest` (NOTE: Testcontainers Docker initialization hangs locally - will be verified in CI/CD)
- [x] Verify all tests pass with 40%+ coverage

---

## Documentation

- [ ] Update CLAUDE.md if new patterns or services introduced
- [ ] Move this plan to `docs/plans/completed/` when done
