# Gemini Structured Output Migration Plan

Migration from YAML-based prompting to Gemini's native JSON Schema structured output for recipe extraction.

## Current Status

**Progress:** 9 of 13 tasks complete (69%)

**Phases Complete:** 
- ✅ Phase 1: Foundation (SDK & Core Parsing)
- ✅ Phase 2: Gemini Integration
- ✅ Phase 3: Validation & Orchestration
- ✅ Phase 4: Controllers & Storage

**Remaining:**
- ⏳ Phase 5: Testing & Quality (in progress)
- ⏳ Phase 6: Cleanup & Documentation

## Table of Contents

- [x] [1. Update Recipe JSON Schema for Gemini Compatibility](#1-update-recipe-json-schema-for-gemini-compatibility)
- [x] [2. Create JSON-to-Recipe Parser](#2-create-json-to-recipe-parser)
- [x] [3. Update Transformer Interface and Response Types](#3-update-transformer-interface-and-response-types)
- [x] [4. Update RequestBuilder for Structured Output](#4-update-requestbuilder-for-structured-output)
- [x] [5. Refactor GeminiRestTransformer](#5-refactor-geminiresttransformer)
- [x] [6. Refactor RecipeValidationService](#6-refactor-recipevalidationservice)
- [x] [7. Refactor ValidatingTransformerService](#7-refactor-validatingtransformerservice)
- [x] [8. Update Controllers and Storage Integration](#8-update-controllers-and-storage-integration)
- [x] [9. Update Prompt Template](#9-update-prompt-template)
- [ ] [10. Update Unit Tests](#10-update-unit-tests)
- [ ] [11. Update Integration Tests](#11-update-integration-tests)
- [ ] [12. Remove Deprecated Code](#12-remove-deprecated-code)
- [ ] [13. Documentation Updates](#13-documentation-updates)

---

## 1. Update Recipe JSON Schema for Gemini Compatibility

**File:** `extractor/src/main/resources/recipe-schema-1.0.0.json`

**Goal:** Ensure JSON schema is compatible with Gemini's structured output subset.

**Tasks:**
- [x] Verify all `type` values are supported (string, number, integer, boolean, object, array, null)
- [x] Review nullable fields - convert to type arrays like `["string", "null"]` where needed
- [x] Ensure `enum` constraints are properly defined
- [x] Verify `format` constraints (date, date-time) are supported
- [x] Check array schemas use `items` correctly
- [x] Validate object schemas have proper `properties` and `required` arrays
- [x] Add/improve `description` fields for better AI guidance
- [x] Test schema complexity (nesting depth, total size) - simplify if needed
- [x] Consider adding a special field for non-recipe detection: `is_recipe: boolean`

**Testing:**
- [x] Manually test schema with Gemini API curl commands (use `extractor/scripts/test-gemini-schema.sh`)
- [x] Verify Gemini accepts the schema without errors

**Notes:**
- ✅ Updated `recipe-schema-1.0.0.json` with Gemini-compatible changes (single schema file for both API and validation)
- ✅ Added `is_recipe` boolean field at root level for non-recipe detection
- ✅ Removed `additionalProperties` (NOT supported by Gemini API)
- ✅ Removed type arrays like `["string", "null"]` (NOT supported - causes API errors)
- ✅ Optional fields omitted from `required` array instead of using nullable types
- ✅ Removed `default` values (not guaranteed by Gemini)
- ✅ Replaced `const` with `enum` for media types
- ✅ Improved descriptions for better AI guidance (mention "Can be null" in descriptions)
- ✅ Changed `format: "url"` to `format: "uri"`
- ✅ Removed `$schema` and `$id` (not needed for Gemini request)
- ✅ Created test script: `extractor/scripts/test-gemini-schema.sh`
- ✅ Created documentation: `extractor/src/main/resources/SCHEMA_CHANGES.md`
- ✅ Tested successfully with live Gemini API - schema accepted and generates valid JSON

---

## 2. Create JSON-to-Recipe Parser

**New File:** `recipe-sdk/src/main/java/net/shamansoft/recipe/parser/JsonRecipeParser.java`

**Goal:** Parse Gemini's JSON response directly to Recipe Java records.

**Tasks:**
- [x] Create `JsonRecipeParser` class in recipe-sdk module
- [x] Use Jackson ObjectMapper with proper configuration
- [x] Handle Jackson deserialization to Record types
- [x] Implement `Recipe parse(String json)` method
- [x] Implement `Recipe parse(JsonNode jsonNode)` method (for direct node parsing)
- [x] Throw `RecipeParseException` on parsing failures
- [x] Add proper error messages for debugging
- [x] Configure Jackson for strict field validation (fail on unknown properties)
- [x] Handle null/optional fields correctly
- [ ] Add GraalVM reflection hints in `META-INF/native-image/` (if needed - will test during native compilation)

**Example Structure:**
```java
public class JsonRecipeParser {
    private final ObjectMapper mapper;
    
    public JsonRecipeParser() {
        this.mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .registerModule(new JavaTimeModule());
    }
    
    public Recipe parse(String json) throws RecipeParseException {
        // Implementation
    }
    
    public Recipe parse(JsonNode jsonNode) throws RecipeParseException {
        // Implementation
    }
}
```

**Testing:**
- [x] Unit tests with valid JSON samples
- [x] Tests for malformed JSON
- [x] Tests for invalid field types (Jackson doesn't validate @NotNull during deserialization)
- [x] Tests for extra unknown fields
- [x] Tests with all optional fields populated
- [x] Tests with minimal required fields only
- [x] Tests with JsonNode parsing
- [x] Tests with null values in optional fields
- [x] Tests with isRecipe=true and isRecipe=false

**Notes:**
- ✅ Created `JsonRecipeParser` in `recipe-sdk/src/main/java/net/shamansoft/recipe/parser/`
- ✅ Added `isRecipe` field to `Recipe` model
- ✅ Updated all existing tests to include `isRecipe` parameter
- ✅ Created comprehensive test suite: `JsonRecipeParserTest` with 11 tests
- ✅ All recipe-sdk tests pass

---

## 3. Update Transformer Interface and Response Types

**Files:** 
- `extractor/src/main/java/net/shamansoft/cookbook/service/Transformer.java`
- New: `extractor/src/main/java/net/shamansoft/cookbook/service/TransformerResponse.java`

**Goal:** Change transformer contract to work with Recipe objects instead of YAML strings.

**Tasks:**
- [x] Update `Transformer.Response` to use Recipe instead of String value
- [x] Add proper null handling for non-recipe cases
- [x] Add static factory methods `notRecipe()` and `recipe(Recipe)`
- [x] Update JavaDoc to reflect new contract
- [x] Add validation in `recipe()` factory method to prevent null recipes

**Example Structure:**
```java
public interface Transformer {
    TransformerResponse transform(String htmlContent);
    
    record TransformerResponse(boolean isRecipe, Recipe recipe) {
        public static TransformerResponse notRecipe() {
            return new TransformerResponse(false, null);
        }
        
        public static TransformerResponse recipe(Recipe recipe) {
            return new TransformerResponse(true, recipe);
        }
    }
}
```

**Testing:**
- [x] Compile-time verification that all implementations are updated (will fail until Tasks 5-7 are done)
- [ ] Update all callers to use new response type (Tasks 5-7)

**Notes:**
- ✅ Updated `Transformer.Response` record to use `Recipe` instead of `String value`
- ✅ Added static factory methods for cleaner API: `Response.notRecipe()` and `Response.recipe(Recipe)`
- ✅ Added null validation to prevent invalid states
- ⚠️ This is a breaking change - all implementations need updating (Tasks 5-7)

---

## 4. Update RequestBuilder for Structured Output

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/RequestBuilder.java`

**Goal:** Modify Gemini API requests to include `responseJsonSchema` configuration.

**Tasks:**
- [x] Read recipe JSON schema from classpath (`recipe-schema-1.0.0.json`)
- [x] Parse schema into Object for inclusion in request
- [x] Add fields to `GeminiRequest.GenerationConfig`:
  - [x] `responseMimeType: "application/json"`
  - [x] `responseSchema`: parsed JSON schema object
- [x] Update `buildBodyString()` to include schema configuration
- [x] Update `buildBodyStringWithFeedback()` to include schema + error context
- [x] Update feedback messages to reference JSON instead of YAML
- [x] Keep other request parameters (model, safety settings, etc.)
- [x] Parse schema at initialization using ObjectMapper

**Example Schema Integration:**
```java
private JsonNode loadRecipeSchema() {
    // Load from classpath: recipe-schema-1.0.0.json
    // Parse to JsonNode
    // Cache for reuse
}

public String buildBodyString(String htmlContent) {
    JsonNode schema = loadRecipeSchema();
    // Build request with:
    // "generationConfig": {
    //   "responseMimeType": "application/json",
    //   "responseSchema": { ...schema... }
    // }
}
```

**Testing:**
- [x] Schema loading tested during init (will test with actual Gemini calls in Task 5)
- [ ] Test request body structure matches Gemini API requirements (integration test in Task 5)
- [ ] Verify JSON structure is valid (will verify during Task 5)
- [ ] Test with different HTML inputs (integration tests)

**Notes:**
- ✅ Updated `GeminiRequest.GenerationConfig` to include `responseMimeType` and `responseSchema`
- ✅ Changed `jsonSchema` field to `parsedJsonSchema` (Object type) for proper JSON structure
- ✅ Parse schema using ObjectMapper.readValue() at initialization
- ✅ Updated feedback prompt to reference JSON instead of YAML
- ✅ Renamed method from `buildRequestBody()` to `buildRequestBodyWithSchema()` for clarity
- ✅ Schema is now sent as structured object in Gemini request

---

## 5. Refactor GeminiRestTransformer

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformer.java`

**Goal:** Parse JSON responses directly to Recipe objects.

**Tasks:**
- [x] Inject `JsonRecipeParser` dependency
- [x] Change return type to `TransformerResponse` (with Recipe object)
- [x] Remove `CleanupService` dependency (no longer needed for YAML cleanup)
- [x] Parse Gemini JSON response to Recipe object using `JsonRecipeParser`
- [x] Handle `is_recipe: false` detection (from JSON field, not YAML string matching)
- [x] Update error handling for JSON parsing failures
- [x] Keep existing logging for finish reasons, safety ratings, etc.
- [x] Update `transformWithFeedback()` signature to accept Recipe + validation errors
- [x] Remove `cleanup()` method
- [x] Handle multi-part responses (concatenate JSON if needed)
- [x] Validate JSON before parsing

**Example Structure:**
```java
@Service("geminiTransformer")
public class GeminiRestTransformer implements Transformer {
    private final WebClient geminiWebClient;
    private final RequestBuilder requestBuilder;
    private final JsonRecipeParser jsonParser;
    
    @Override
    public TransformerResponse transform(String htmlContent) {
        // Call Gemini API with structured output
        JsonNode response = callGeminiApi(...);
        
        // Extract JSON from response
        String jsonContent = extractJsonFromResponse(response);
        
        // Parse to Recipe
        Recipe recipe = jsonParser.parse(jsonContent);
        
        // Check is_recipe field or validate recipe completeness
        if (!recipe.isRecipe()) {
            return TransformerResponse.notRecipe();
        }
        
        return TransformerResponse.recipe(recipe);
    }
    
    public TransformerResponse transformWithFeedback(
            String htmlContent, 
            Recipe previousRecipe, 
            String validationError) {
        // Implementation with feedback
    }
}
```

**Status:** ✅ **COMPLETE**

**Testing:**
- [ ] Unit tests with mocked WebClient responses
- [ ] Test valid recipe JSON parsing
- [ ] Test non-recipe detection
- [ ] Test malformed JSON handling
- [ ] Test validation error feedback loop
- [ ] Test multi-part response handling

---

## 6. Refactor RecipeValidationService

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeValidationService.java`

**Goal:** Validate Recipe objects directly instead of parsing YAML.

**Tasks:**
- [x] Change `validate(String yaml)` to `validate(Recipe recipe)`
- [x] Remove `YamlRecipeParser` dependency (no longer needed)
- [x] Keep `RecipeSerializer` for final YAML conversion (if needed)
- [x] Keep Bean Validation with `Validator`
- [x] Update `ValidationResult` to contain Recipe object instead of normalized YAML
- [x] Simplify error handling (no YAML parse errors, only validation errors)
- [x] Add method to convert Recipe to YAML: `String toYaml(Recipe recipe)`
- [x] Format validation errors in a structured way for Gemini feedback
- [x] Keep logging for validation failures
- [x] Remove `buildParseErrorMessage()` (no longer needed)
- [x] Remove `buildSerializeErrorMessage()` or update for new flow

**Example Structure:**
```java
@Service
public class RecipeValidationService {
    private final RecipeSerializer serializer;
    private final Validator validator;
    
    public ValidationResult validate(Recipe recipe) {
        // Validate using Bean Validation
        Set<ConstraintViolation<Recipe>> violations = validator.validate(recipe);
        
        if (!violations.isEmpty()) {
            String errors = formatValidationErrors(violations);
            return ValidationResult.failure(errors);
        }
        
        return ValidationResult.success(recipe);
    }
    
    public String toYaml(Recipe recipe) throws RecipeSerializeException {
        return serializer.serialize(recipe);
    }
    
    private String formatValidationErrors(Set<ConstraintViolation<Recipe>> violations) {
        // Format for Gemini feedback
        // Example: "Field 'metadata.title' is required but missing"
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final Recipe recipe;
        private final String errorMessage;
        
        // success(Recipe) and failure(String) factory methods
    }
}
```

**Status:** ✅ **COMPLETE**

**Testing:**
- [ ] Unit tests with valid Recipe objects
- [ ] Tests with missing required fields
- [ ] Tests with invalid field values
- [ ] Tests with constraint violations
- [ ] Test YAML serialization from Recipe objects
- [ ] Test error message formatting

---

## 7. Refactor ValidatingTransformerService

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/ValidatingTransformerService.java`

**Goal:** Orchestrate validation and retry with Recipe objects.

**Tasks:**
- [x] Update return type to `TransformerResponse`
- [x] Work with Recipe objects throughout the validation flow
- [x] Update retry loop to pass Recipe objects and validation errors
- [x] Format validation errors into structured feedback for Gemini
- [x] Keep retry count logic (`recipe.llm.retry` property)
- [x] Update logging to show Recipe validation status
- [x] Handle non-recipe responses without validation
- [x] Only convert to YAML if explicitly needed (for storage)
- [x] Keep short-circuit for `maxRetries == 0`
- [x] Update error messages for Recipe validation context

**Example Structure:**
```java
@Service
@Primary
public class ValidatingTransformerService implements Transformer {
    private final GeminiRestTransformer geminiTransformer;
    private final RecipeValidationService validationService;
    
    @Override
    public TransformerResponse transform(String htmlContent) {
        TransformerResponse response = geminiTransformer.transform(htmlContent);
        
        if (!response.isRecipe()) {
            return response;
        }
        
        if (maxRetries == 0) {
            return response;
        }
        
        return validateWithRetry(htmlContent, response);
    }
    
    private TransformerResponse validateWithRetry(
            String htmlContent, 
            TransformerResponse initialResponse) {
        
        Recipe currentRecipe = initialResponse.recipe();
        ValidationResult result = validationService.validate(currentRecipe);
        
        if (result.isValid()) {
            return TransformerResponse.recipe(result.getRecipe());
        }
        
        // Retry loop with feedback
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            TransformerResponse retryResponse = geminiTransformer.transformWithFeedback(
                htmlContent,
                currentRecipe,
                result.getErrorMessage()
            );
            
            // ... validation and retry logic
        }
        
        return TransformerResponse.notRecipe(); // or return failed recipe
    }
}
```

**Status:** ✅ **COMPLETE**

**Testing:**
- [ ] Unit tests with successful validation
- [ ] Tests with validation failures and retries
- [ ] Tests with max retries exhausted
- [ ] Tests with retry disabled (maxRetries=0)
- [ ] Tests with non-recipe content
- [ ] Mock GeminiRestTransformer and RecipeValidationService

---

## 8. Update Controllers and Storage Integration

**Files:**
- `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java` (main service layer)
- `extractor/src/main/java/net/shamansoft/cookbook/controller/RecipeController.java`
- `extractor/src/main/java/net/shamansoft/cookbook/repository/FirestoreRecipeRepository.java`

**Goal:** Handle Recipe objects and convert to YAML only at storage time.

**Tasks:**
- [x] Update `RecipeService` to work with `TransformerResponse` returning Recipe objects
- [x] Extract Recipe object from response
- [x] Convert Recipe to YAML using `RecipeValidationService.toYaml()` for storage
- [x] Update error handling for Recipe validation failures
- [x] Update cached recipe handling to parse YAML back to Recipe
- [x] Keep Google Drive storage (still YAML format)
- [x] Keep Firestore storage format (YAML in document)
- [x] Update logging to show Recipe metadata (title, language, etc.)
- [x] Add helper method `convertRecipeToYaml()` for serialization
- [x] Handle RecipeSerializeException appropriately

**Example in Controller:**
```java
@PostMapping("/recipe")
public ResponseEntity<?> extractRecipe(@RequestBody RecipeRequest request) {
    TransformerResponse response = validatingTransformer.transform(htmlContent);
    
    if (!response.isRecipe()) {
        return ResponseEntity.ok(new ErrorResponse("Not a recipe"));
    }
    
    Recipe recipe = response.recipe();
    
    // Convert to YAML for storage
    String yaml = validationService.toYaml(recipe);
    
    // Store to Firestore
    recipeRepository.save(userId, yaml);
    
    return ResponseEntity.ok(new RecipeResponse(recipe));
}
```

**Status:** ✅ **COMPLETE**

**Testing:**
- [ ] Integration tests for recipe extraction endpoint
- [ ] Tests for storage integration
- [ ] Tests for non-recipe handling
- [ ] Tests for validation failures
- [ ] End-to-end tests with real Gemini API (if applicable)

---

## 9. Update Prompt Template

**File:** `extractor/src/main/resources/prompt.md`

**Goal:** Remove YAML-specific instructions and focus on structured data extraction.

**Tasks:**
- [x] Remove all YAML formatting instructions
- [x] Remove instructions about markdown code fences
- [x] Focus on data extraction accuracy
- [x] Emphasize field requirements from JSON schema
- [x] Update examples to reference JSON structure (not YAML)
- [x] Keep language detection instructions
- [x] Keep instructions for `is_recipe` field
- [x] Update ingredient extraction guidelines
- [x] Update instruction formatting guidelines
- [x] Simplify prompt since schema now enforces structure
- [x] Keep example YAML for reference (note: output is JSON)
- [x] Add guidance for handling ambiguous or missing data
- [x] Add validation feedback section for retry attempts

**Example Updates:**
```markdown
You are an AI specialized in extracting cooking recipes from HTML.

**Task:** Extract recipe information from HTML content into structured JSON.

**Critical Rules:**
- The JSON schema is enforced - return valid JSON matching the schema
- Set `is_recipe: false` if content is not a cooking recipe
- Detect content language and set `metadata.language` field
- Extract all available information accurately
- Use `null` for truly missing fields (not empty strings)
- For ambiguous servings, estimate reasonably (e.g., 4)

**Field Extraction Guidelines:**
... (keep specific extraction rules but remove YAML formatting)

**JSON Schema:**
The structure is defined by the provided schema. All fields marked as "required" MUST be present.

**HTML Content:**
...
```

**Status:** ✅ **COMPLETE**

**Testing:**
- [ ] Review prompt with product team
- [ ] Test with sample recipes
- [ ] Verify Gemini follows new instructions
- [ ] Compare output quality vs. old YAML approach

---

## 10. Update Unit Tests

**Test Files:**
- `extractor/src/test/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformerTest.java`
- `extractor/src/test/java/net/shamansoft/cookbook/service/RecipeValidationServiceTest.java`
- `extractor/src/test/java/net/shamansoft/cookbook/service/ValidatingTransformerServiceTest.java`
- New: `recipe-sdk/src/test/java/net/shamansoft/recipe/parser/JsonRecipeParserTest.java`

**Tasks:**
- [ ] Create `JsonRecipeParserTest` for new JSON parser
- [ ] Update `GeminiRestTransformerTest` to mock JSON responses
- [ ] Update test fixtures to use JSON instead of YAML
- [ ] Update `RecipeValidationServiceTest` to pass Recipe objects
- [ ] Update `ValidatingTransformerServiceTest` for new flow
- [ ] Create test JSON samples in `src/test/resources/`
- [ ] Test valid recipe JSON parsing
- [ ] Test invalid JSON handling
- [ ] Test non-recipe detection
- [ ] Test validation error feedback
- [ ] Test retry logic with Recipe objects
- [ ] Update mock responses to return proper JSON structure
- [ ] Verify all edge cases are covered
- [ ] Ensure test coverage remains above 40% threshold

**Example Test Updates:**
```java
@Test
void testTransformValidRecipe() {
    // Mock Gemini response with JSON
    JsonNode mockResponse = createMockGeminiJsonResponse(validRecipeJson);
    when(geminiWebClient.post()...).thenReturn(mockResponse);
    
    TransformerResponse response = transformer.transform(htmlContent);
    
    assertTrue(response.isRecipe());
    assertNotNull(response.recipe());
    assertEquals("Chocolate Chip Cookies", response.recipe().metadata().title());
}
```

**Testing:**
- [ ] Run all unit tests: `./gradlew :cookbook:test`
- [ ] Verify coverage: `./gradlew :cookbook:checkCoverage`
- [ ] Fix any broken tests
- [ ] Add new tests for JSON parsing edge cases

---

## 11. Update Integration Tests

**Test Files:**
- `extractor/src/intTest/java/net/shamansoft/cookbook/integration/CookbookControllerIntegrationTest.java`
- WireMock stub configurations

**Tasks:**
- [ ] Update WireMock stubs to return JSON responses (not YAML)
- [ ] Create JSON response fixtures for integration tests
- [ ] Update test assertions to work with Recipe objects
- [ ] Test full flow: HTML → Gemini JSON → Recipe → Validation → YAML storage
- [ ] Test non-recipe scenarios
- [ ] Test validation failure and retry scenarios
- [ ] Update mock Gemini API responses
- [ ] Test error handling paths
- [ ] Verify Testcontainers setup still works
- [ ] Update authentication tests if needed

**Example WireMock Stub:**
```java
stubFor(post(urlPathMatching("/models/.*/generateContent"))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(loadGeminiJsonResponse("valid-recipe.json"))));
```

**Testing:**
- [ ] Run integration tests: `./gradlew :cookbook:intTest`
- [ ] Verify all scenarios pass
- [ ] Check Docker/Testcontainers work correctly
- [ ] Test with WireMock container

---

## 12. Remove Deprecated Code

**Files to Clean Up:**
- `extractor/src/main/java/net/shamansoft/cookbook/service/CleanupService.java`
- Old Response types (if replaced)

**Tasks:**
- [ ] Remove `CleanupService` class (no longer needed for YAML cleanup)
- [ ] Remove `removeYamlSign()` and related methods
- [ ] Remove old `Transformer.Response` if replaced
- [ ] Search codebase for unused YAML-related utilities
- [ ] Remove deprecated imports
- [ ] Clean up any YAML parsing from non-SDK locations
- [ ] Update dependency cleanup (if any YAML-specific libs)
- [ ] Remove example YAML files if no longer needed
- [ ] Clean up test fixtures for old YAML flow

**Verification:**
- [ ] Build project: `./gradlew build`
- [ ] Ensure no compilation errors
- [ ] Search for "cleanup" references
- [ ] Search for "yaml" references (keep only SDK and storage)

---

## 13. Documentation Updates

**Files:**
- `extractor/README.md`
- `docs/structrured-output-gemini.md`
- `CLAUDE.md`
- `README.md` (root)

**Tasks:**
- [ ] Update `extractor/README.md` with new architecture
- [ ] Document JSON Schema structured output approach
- [ ] Update data flow diagrams (HTML → JSON → Recipe → YAML)
- [ ] Document validation and retry mechanism
- [ ] Update API documentation
- [ ] Update `CLAUDE.md` with new component descriptions
- [ ] Add migration notes and rationale
- [ ] Document JSON Schema compatibility requirements
- [ ] Update troubleshooting guide
- [ ] Add examples of Gemini JSON responses
- [ ] Document testing approach for structured output
- [ ] Update deployment notes if needed
- [ ] Add performance comparison notes (if available)

**Example Documentation:**
```markdown
## Recipe Extraction Flow

1. **HTML Input** → Controller receives HTML content
2. **Gemini API** → Calls Gemini with JSON Schema structured output
3. **JSON Response** → Gemini returns structured JSON matching schema
4. **Parse to Recipe** → JSON parsed to Java Record objects
5. **Validation** → Bean Validation checks required fields
6. **Retry on Failure** → Sends validation errors back to Gemini
7. **YAML Conversion** → Final validated Recipe converted to YAML
8. **Storage** → YAML stored in Firestore/Google Drive

### Benefits of Structured Output
- Guaranteed valid JSON structure
- Reduced parsing errors
- Faster response processing
- Better retry feedback mechanism
- Type-safe Recipe objects
```

**Review:**
- [ ] Technical review by team
- [ ] Update examples and code snippets
- [ ] Verify all links work
- [ ] Update version numbers if needed

---

## Migration Checklist Summary

### Phase 1: Foundation (SDK & Core Parsing) ✅ **COMPLETE**
- [x] Task 1: Update JSON Schema
- [x] Task 2: Create JSON Parser
- [x] Task 3: Update Transformer Interface

### Phase 2: Gemini Integration ✅ **COMPLETE**
- [x] Task 4: Update RequestBuilder
- [x] Task 5: Refactor GeminiRestTransformer
- [x] Task 9: Update Prompt Template

### Phase 3: Validation & Orchestration ✅ **COMPLETE**
- [x] Task 6: Refactor RecipeValidationService
- [x] Task 7: Refactor ValidatingTransformerService

### Phase 4: Controllers & Storage ✅ **COMPLETE**
- [x] Task 8: Update Controllers

### Phase 5: Testing & Quality
- [ ] Task 10: Update Unit Tests
- [ ] Task 11: Update Integration Tests
- [ ] Run full test suite
- [ ] Run security scan: `./gradlew :cookbook:dependencyCheck`
- [ ] Check coverage: `./gradlew :cookbook:checkCoverage`

### Phase 6: Cleanup & Documentation
- [ ] Task 12: Remove Deprecated Code
- [ ] Task 13: Documentation Updates
- [ ] Code review
- [ ] PR creation

### Phase 7: Deployment
- [ ] Merge to main (triggers CI/CD)
- [ ] Monitor deployment
- [ ] Verify production behavior
- [ ] Monitor error rates and performance

---

## Risk Mitigation

### Backward Compatibility
- Consider feature flag for gradual rollout
- Keep YAML storage format (only internal representation changes)
- Test thoroughly with existing recipes

### Gemini API Limitations
- JSON Schema subset support - verify compatibility early
- Test with complex recipes to ensure no truncation
- Monitor token usage and costs
- Have rollback plan if structured output doesn't work well

### Performance Considerations
- JSON parsing should be faster than YAML
- Measure response times before/after
- Monitor memory usage with Record objects

### Testing Strategy
- Test with diverse recipe sources (different languages, formats)
- Validate edge cases (missing fields, malformed HTML)
- Load testing if needed
- A/B testing in production (if possible)

---

## Success Criteria

- [ ] All tests pass (unit + integration)
- [ ] Code coverage ≥ 40%
- [ ] No security vulnerabilities
- [ ] Documentation complete
- [ ] Successfully extracts recipes from test samples
- [ ] Validation retry mechanism works
- [ ] YAML storage format unchanged
- [ ] Deployment successful to Cloud Run
- [ ] Production monitoring shows no errors
- [ ] Performance equal or better than YAML approach

---

## Rollback Plan

If issues arise in production:
1. Revert to previous version via GitHub
2. Redeploy previous version: `gh workflow run deploy.yml --ref <previous-tag>`
3. Investigate issues in development environment
4. Fix and re-test before redeployment

---

## Timeline Estimate

- Phase 1: 2-3 days (Foundation)
- Phase 2: 2-3 days (Gemini Integration)
- Phase 3: 1-2 days (Validation)
- Phase 4: 1 day (Controllers)
- Phase 5: 2-3 days (Testing)
- Phase 6: 1 day (Cleanup)
- Phase 7: 1 day (Deployment & Monitoring)

**Total: 10-15 days** (assuming single developer, full-time)

---

## Notes

- This is a significant refactoring touching multiple layers
- Consider pair programming for critical components
- Incremental commits recommended
- Test early and often with real Gemini API
- Keep communication open with stakeholders
- Monitor costs during development (Gemini API usage)