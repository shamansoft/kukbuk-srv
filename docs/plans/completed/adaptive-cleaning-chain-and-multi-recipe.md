# Plan: Adaptive HTML Cleaning Chain and Multi-Recipe Extraction

## Overview

Two interrelated features:

1. **Adaptive HTML Cleaning Chain**: A feedback loop that retries Gemini extraction with progressively less restrictive HTML cleaning strategies when the LLM reports low-confidence non-recipe results.
2. **Multi-Recipe Extraction**: Changing the Gemini response model from a single recipe to a list of recipes, enabling extraction of all recipes found on a multi-recipe page.

These two features are designed together because Feature 2's schema changes (the `GeminiExtractionResult` wrapper) naturally contain the `recipe_confidence` field needed for Feature 1.

---

## 📋 Table of Contents

- [ ] 1. Schema and DTO Layer
  - [ ] 1.1 Create `GeminiExtractionResult` wrapper DTO
  - [ ] 1.2 Create `recipe-schema-2.0.0.json` with `recipe_confidence` and `recipes[]`
  - [ ] 1.3 Update `Transformer.Response` record — add `confidence`, change `recipe` → `List<Recipe> recipes`
- [ ] 2. Gemini Layer Updates
  - [ ] 2.1 Update `GeminiRestTransformer` to deserialize `GeminiExtractionResult`
  - [ ] 2.2 Update `RequestBuilder` to load `recipe-schema-2.0.0.json`
  - [ ] 2.3 Update `prompt.md` — multi-recipe extraction + `recipe_confidence` instructions
- [ ] 3. HtmlCleaner Extension
  - [ ] 3.1 Add `processWithStrategy(html, url, Strategy)` method to `HtmlCleaner`
  - [ ] 3.2 Add `Strategy.ADAPTIVE_ORDER` constant for iteration
- [ ] 4. New `AdaptiveCleaningTransformerService`
  - [ ] 4.1 Create service — strategy iteration loop with confidence threshold
  - [ ] 4.2 Add config: `recipe.adaptive-cleaning.enabled` and `confidence-threshold`
  - [ ] 4.3 Promote to `@Primary`, demote `ValidatingTransformerService`
- [ ] 5. `ValidatingTransformerService` Update
  - [ ] 5.1 Handle `List<Recipe>` instead of single `Recipe`
  - [ ] 5.2 Validate each recipe; collect errors across all failed recipes for feedback
  - [ ] 5.3 Preserve `confidence` field through validation chain
- [ ] 6. `RecipePostProcessor` — verify loop-safe (no change needed)
- [ ] 7. `RecipeService` Update
  - [ ] 7.1 Remove explicit `htmlPreprocessor.process()` call (moved into adaptive service)
  - [ ] 7.2 Upload each recipe to Drive as a separate file
  - [ ] 7.3 Update `RecipeStoreService` caching to handle recipe lists
  - [ ] 7.4 Update `RecipeResponse` DTO — add `recipes[]`, keep old fields for backward compat
- [ ] 8. Configuration
  - [ ] 8.1 Add adaptive-cleaning properties to `application.yaml`
  - [ ] 8.2 Add GraalVM reflection hints for new DTOs if needed
- [ ] 9. Testing
  - [ ] 9.1 Unit tests for `GeminiExtractionResult` deserialization
  - [ ] 9.2 Unit tests for `AdaptiveCleaningTransformerService` strategy iteration
  - [ ] 9.3 Update `ValidatingTransformerServiceTest` for multi-recipe
  - [ ] 9.4 Update `RecipeServiceCreateRecipeTest` for multi-recipe Drive uploads
  - [ ] 9.5 Update `GeminiRestTransformerTest` for new response shape

---

## Detailed Plan

### 1. Schema and DTO Layer

**Objective:** Introduce a `GeminiExtractionResult` wrapper that Gemini returns at the top level, containing `is_recipe`, `recipe_confidence`, and `recipes[]`. Update `Transformer.Response` to carry the new fields downstream.

---

#### 1.1 Create `GeminiExtractionResult`

**File to create:**
`extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiExtractionResult.java`

This is a Jackson-deserialized DTO representing the raw Gemini response envelope. Currently the `Recipe` Java record has `is_recipe` at its top level. With the new schema, `is_recipe` and `recipe_confidence` move to the wrapper, and per-recipe fields move into each item in `recipes[]`.

```java
// Outer wrapper returned by Gemini
@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiExtractionResult(
    @JsonProperty("is_recipe")       boolean isRecipe,
    @JsonProperty("recipe_confidence") double recipeConfidence,   // 0.0–1.0
    @JsonProperty("internal_reasoning") String internalReasoning,
    @JsonProperty("recipes")         List<RecipeData> recipes
) {
    // Inner record: mirrors Recipe fields but without is_recipe
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecipeData(/* all Recipe fields except is_recipe */) {}
}
```

**Design rationale — separate `RecipeData` vs reusing `Recipe`:**
Creating a separate `RecipeData` inner record keeps the SDK's `Recipe` model clean and unchanged. `GeminiRestTransformer` converts `RecipeData` → `Recipe` after deserialization (injecting `isRecipe=true` from the wrapper). This also prevents coupling the SDK's serialized YAML format to Gemini's wire format.

---

#### 1.2 Create `recipe-schema-2.0.0.json`

**File to create:**
`extractor/src/main/resources/recipe-schema-2.0.0.json`

New structure (Gemini's response schema):

```json
{
  "type": "object",
  "required": ["is_recipe", "recipe_confidence"],
  "properties": {
    "internal_reasoning": {
      "type": "string",
      "description": "Up to 50 words of reasoning"
    },
    "is_recipe": { "type": "boolean" },
    "recipe_confidence": {
      "type": "number",
      "minimum": 0.0,
      "maximum": 1.0,
      "description": "Confidence 0.0–1.0. High value + is_recipe=false signals over-cleaned HTML."
    },
    "recipes": {
      "type": "array",
      "items": { "...": "same as current recipe schema minus is_recipe at item level" }
    }
  }
}
```

The **existing `recipe-schema-1.0.0.json` is kept unchanged** for Bean Validation of stored recipes. Only `RequestBuilder` changes which schema it sends to Gemini.

**Key schema design rules:**
- `recipes` is empty when `is_recipe: false`
- `recipes` has 1+ items when `is_recipe: true`
- Item-level `is_recipe` field removed (it lives only on the wrapper now)
- `recipe_confidence` is always present; LLM must always set it

---

#### 1.3 Update `Transformer.Response`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/Transformer.java`

Current:
```java
record Response(boolean isRecipe, Recipe recipe, String rawLlmResponse)
```

New:
```java
record Response(boolean isRecipe, double confidence, List<Recipe> recipes, String rawLlmResponse)
```

Updated factory methods:
```java
static Response notRecipe()                             // confidence=0.0, recipes=[]
static Response notRecipe(double confidence)            // NEW — used when LLM says "maybe"
static Response recipes(List<Recipe> recipes)           // confidence=1.0, replaces recipe()
static Response recipe(Recipe r)                        // DEPRECATED — delegates to recipes(List.of(r))
static Response withRaw(boolean isRecipe, double confidence, List<Recipe> recipes, String raw)
```

**Callsites that must be updated:**
- `ValidatingTransformerService` — `.recipe()` → `.recipes()`, add `.confidence()` propagation
- `RecipeService.createOrGetCached()` — `.recipe()` → `.recipes()`
- `GeminiRestTransformer.transformInternal()` — build multi-recipe result

---

### 2. Gemini Layer Updates

**Objective:** Send the new schema to Gemini and parse `GeminiExtractionResult` instead of `Recipe`.

---

#### 2.1 Update `GeminiRestTransformer`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformer.java`

Change Gemini client call:
```java
// Before
GeminiResponse<Recipe> response = geminiClient.request(request, Recipe.class);

// After
GeminiResponse<GeminiExtractionResult> response =
    geminiClient.request(request, GeminiExtractionResult.class);
```

Post-deserialization mapping in `transformInternal()`:
1. Extract `isRecipe` and `recipeConfidence` from the wrapper
2. Convert each `RecipeData` item to a `Recipe` record (inject `isRecipe=true`, set `schemaVersion`/`recipeVersion` defaults matching current logic)
3. Return `Transformer.Response.withRaw(isRecipe, confidence, recipes, rawJson)`

For `transformWithFeedback()`, accept `List<Recipe> previousRecipes` and send the first failing recipe's data as feedback (V1 simplification). A future enhancement can send consolidated feedback for all failures.

---

#### 2.2 Update `RequestBuilder`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/gemini/RequestBuilder.java`

Change the schema resource path:
```java
// Before
String jsonSchemaString = resourceLoader.loadTextFile("classpath:recipe-schema-1.0.0.json");

// After
String jsonSchemaString = resourceLoader.loadTextFile("classpath:recipe-schema-2.0.0.json");
```

No other changes to request construction logic.

---

#### 2.3 Update `prompt.md`

**File to modify:**
`extractor/src/main/resources/prompt.md`

**Change 1 — Multi-recipe detection** (add near the top, in the extraction rules section):

> **MULTIPLE RECIPES:** A page may contain more than one complete recipe.
> - Scan the entire page for recipe titles, ingredient lists, and instruction sets.
> - If you find multiple distinct recipes, populate the `recipes` array with ALL of them.
> - If only one recipe exists, `recipes` contains a single item.
> - Do NOT merge separate recipes into one; preserve each as its own entry.

**Change 2 — `recipe_confidence` instructions** (add near the "non-recipe" section):

> **RECIPE CONFIDENCE SCORING (`recipe_confidence`):**
> Always set this field (0.0–1.0) to reflect certainty that the page contains a recipe:
> - `0.9–1.0`: Clearly one or more complete recipes with ingredients and instructions
> - `0.7–0.89`: Likely a recipe; minor ambiguity (e.g., partial instructions)
> - `0.5–0.69`: Uncertain — HTML appears incomplete, navigation-heavy, or unusually short;
>               a retry with richer HTML context may succeed. Set `is_recipe: false`.
> - `0.3–0.49`: Unlikely to be a recipe; insufficient evidence
> - `0.0–0.29`: Clearly not a recipe (blog post, article, product page, etc.)
>
> **When `is_recipe: false` AND `recipe_confidence >= 0.5`:** This signals that the HTML
> may have been over-cleaned and the original page might contain a recipe. Return the
> confidence score accurately — this enables an automatic retry with more HTML content.

**Change 3 — Non-recipe response template** (update existing):

Update the non-recipe JSON example to include `recipe_confidence` and the `recipes` wrapper:
```json
{
  "is_recipe": false,
  "recipe_confidence": 0.1,
  "internal_reasoning": "This appears to be a product listing page, not a recipe.",
  "recipes": []
}
```

---

### 3. HtmlCleaner Extension

**Objective:** Expose targeted strategy application so the adaptive loop can apply specific strategies individually, not just the full cascade.

---

#### 3.1 Add `processWithStrategy()`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/html/HtmlCleaner.java`

New public method:
```java
/**
 * Applies a specific named strategy to the HTML, bypassing the cascade.
 * Used by AdaptiveCleaningTransformerService for sequential strategy retries.
 * If the strategy produces no output, returns raw HTML tagged as FALLBACK.
 */
public Results processWithStrategy(String html, String url, Strategy targetStrategy) {
    int originalSize = html != null ? html.length() : 0;
    if (html == null || html.isBlank()) {
        return buildResult("", 0, Strategy.FALLBACK);
    }
    if (!config.isEnabled() || targetStrategy == Strategy.FALLBACK) {
        return buildResult(html, originalSize, Strategy.FALLBACK);
    }
    for (HtmlCleaningStrategy strategy : strategies) {
        if (strategy.getStrategy() == targetStrategy) {
            try {
                Optional<String> out = strategy.clean(html);
                if (out.isPresent() && out.get().length() >= config.getFallback().getMinSafeSize()) {
                    return buildResult(out.get(), originalSize, targetStrategy);
                }
            } catch (Exception e) {
                log.debug("Strategy {} failed for URL: {}", targetStrategy, url, e);
            }
            break;
        }
    }
    log.debug("Strategy {} produced no output for {}, using raw HTML", targetStrategy, url);
    return buildResult(html, originalSize, Strategy.FALLBACK);
}
```

#### 3.2 Add `Strategy.ADAPTIVE_ORDER`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/html/HtmlCleaner.java` (or the `Strategy` enum file if separate)

```java
public enum Strategy {
    STRUCTURED_DATA, SECTION_BASED, CONTENT_FILTER, FALLBACK, DISABLED;

    /**
     * Ordered from most restrictive to least restrictive.
     * Used by AdaptiveCleaningTransformerService to step through strategies
     * when the LLM signals the HTML may be over-cleaned.
     */
    public static final List<Strategy> ADAPTIVE_ORDER =
        List.of(STRUCTURED_DATA, SECTION_BASED, CONTENT_FILTER, FALLBACK);
}
```

---

### 4. New `AdaptiveCleaningTransformerService`

**Objective:** Outer loop service wrapping `ValidatingTransformerService` + `HtmlCleaner`. Iterates cleaning strategies based on LLM confidence feedback.

---

#### 4.1 Create `AdaptiveCleaningTransformerService`

**File to create:**
`extractor/src/main/java/net/shamansoft/cookbook/service/AdaptiveCleaningTransformerService.java`

```java
@Service
@Primary  // Replaces ValidatingTransformerService as @Primary
@Slf4j
@RequiredArgsConstructor
public class AdaptiveCleaningTransformerService implements Transformer {

    private final ValidatingTransformerService innerTransformer;
    private final HtmlCleaner htmlCleaner;

    @Value("${recipe.adaptive-cleaning.enabled:true}")
    private boolean enabled;

    @Value("${recipe.adaptive-cleaning.confidence-threshold:0.5}")
    private double confidenceThreshold;

    /**
     * Accepts RAW (uncleaned) HTML. Performs cleaning internally.
     * RecipeService should pass raw HTML, not pre-cleaned HTML.
     */
    @Override
    public Response transform(String rawHtml, String sourceUrl) {
        // Step 1: initial clean using cascade (existing behavior)
        HtmlCleaner.Results initial = htmlCleaner.process(rawHtml, sourceUrl);
        log.debug("Initial strategy: {} for URL: {}", initial.strategyUsed(), sourceUrl);

        Response result = innerTransformer.transform(initial.cleanedHtml(), sourceUrl);

        if (!enabled || result.isRecipe()) {
            return result;
        }

        // Step 2: adaptive retry loop if LLM is uncertain
        Strategy currentStrategy = initial.strategyUsed();
        List<Strategy> order = Strategy.ADAPTIVE_ORDER;
        int startIdx = order.indexOf(currentStrategy) + 1; // start AFTER current strategy

        for (int i = startIdx; i < order.size(); i++) {
            Strategy nextStrategy = order.get(i);

            if (result.confidence() < confidenceThreshold) {
                log.info("Confidence {:.2f} below threshold {:.2f} at strategy {} — stopping",
                    result.confidence(), confidenceThreshold, currentStrategy);
                return result;
            }

            log.info("Confidence {:.2f} >= threshold — retrying with strategy {} for URL: {}",
                result.confidence(), nextStrategy, sourceUrl);

            HtmlCleaner.Results retryClean = htmlCleaner.processWithStrategy(rawHtml, sourceUrl, nextStrategy);
            result = innerTransformer.transform(retryClean.cleanedHtml(), sourceUrl);
            currentStrategy = nextStrategy;

            if (result.isRecipe()) {
                log.info("Recipe found after {} strategy for URL: {}", nextStrategy, sourceUrl);
                return result;
            }
        }

        // All strategies exhausted
        log.info("All cleaning strategies exhausted for URL: {} — returning notRecipe", sourceUrl);
        return result; // last result with its confidence
    }
}
```

**Critical design note:** The `transform()` method receives **raw HTML** (not pre-cleaned). This requires updating `RecipeService` to stop calling `htmlPreprocessor.process()` separately and instead pass raw HTML directly to `transformer.transform()`. The `AdaptiveCleaningTransformerService` now owns the cleaning step.

---

#### 4.2 Configuration

See Section 8.1 for `application.yaml` changes.

---

#### 4.3 Promote to `@Primary`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/ValidatingTransformerService.java`

Remove the `@Primary` annotation from `ValidatingTransformerService`. It becomes a regular `@Service` used internally by `AdaptiveCleaningTransformerService`.

---

### 5. `ValidatingTransformerService` Update

**Objective:** Handle `List<Recipe>` throughout; preserve confidence field.

---

#### 5.1 Handle `List<Recipe>`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/ValidatingTransformerService.java`

Key loop changes:
```java
// validate each recipe
List<Recipe> valid = new ArrayList<>();
List<String> errors = new ArrayList<>();
for (Recipe recipe : response.recipes()) {
    ValidationResult vr = validator.validate(recipe);
    if (vr.isValid()) {
        valid.add(vr.recipe());
    } else {
        errors.add(vr.errorMessage());
    }
}
```

If `valid.size() == response.recipes().size()` → all valid, post-process and return.
If `valid.isEmpty()` after all retries → return `notRecipe(response.confidence())`.
If partial success (some valid, some not) → return valid subset, log warnings for failures.

**Partial failure policy:** Return valid recipes; do not block all results because one recipe failed validation.

---

#### 5.2 Retry feedback with multiple recipes

For the retry case, concatenate errors from all failing recipes into one feedback string. Send the first failing recipe as the "previous recipe" reference (existing feedback mechanism). Mark this as a V1 simplification; V2 can provide per-recipe feedback.

---

#### 5.3 Preserve confidence

The `confidence` value from the initial `GeminiRestTransformer` response must be carried through to the returned `Transformer.Response`. Even after validation retries, use the confidence from the *current* LLM response (retries may change confidence).

---

### 6. `RecipePostProcessor`

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/RecipePostProcessor.java`

**No changes needed.** The `process(Recipe recipe, String sourceUrl)` method is already per-recipe. Callers (in `ValidatingTransformerService`) will stream-map the list:

```java
List<Recipe> processed = recipes.stream()
    .map(r -> postProcessor.process(r, sourceUrl))
    .toList();
```

---

### 7. `RecipeService` Update

---

#### 7.1 Remove explicit `htmlPreprocessor.process()` call

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`

Remove the `htmlPreprocessor.process()` call from `createOrGetCached()`. The `AdaptiveCleaningTransformerService.transform()` now receives raw HTML and handles cleaning internally.

```java
// Before
String html = htmlExtractor.extractHtml(url, sourceHtml, compression);
HtmlCleaner.Results cleaned = htmlPreprocessor.process(html, url);
log.info("...", cleaned.metricsMessage());
var response = transformer.transform(cleaned.cleanedHtml(), url);

// After
String html = htmlExtractor.extractHtml(url, sourceHtml, compression);
log.info("Extracted HTML - URL: {}, length: {} chars", url, html.length());
var response = transformer.transform(html, url); // adaptive service does cleaning internally
```

**Note:** `htmlPreprocessor` field can be removed from `RecipeService` if it's no longer used elsewhere. Verify before deleting.

---

#### 7.2 Multiple Drive uploads

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`

```java
if (response.isRecipe()) {
    List<RecipeItemResult> uploadedItems = new ArrayList<>();
    for (Recipe recipe : response.recipes()) {
        String fileName = googleDriveService.generateFileName(
            recipe.metadata() != null ? recipe.metadata().title() : requestTitle);
        String yamlContent = recipeValidator.toYaml(recipe);
        DriveService.UploadResult upload = googleDriveService.uploadRecipeYaml(
            storage.accessToken(), storage.folderId(), fileName, yamlContent);
        uploadedItems.add(new RecipeItemResult(
            recipe.metadata() != null ? recipe.metadata().title() : requestTitle,
            upload.fileId(),
            upload.fileUrl()
        ));
    }
    return RecipeResponse.builder()
        .url(url)
        .title(uploadedItems.get(0).title())         // first recipe for backward compat
        .driveFileId(uploadedItems.get(0).driveFileId())  // backward compat
        .driveFileUrl(uploadedItems.get(0).driveFileUrl()) // backward compat
        .isRecipe(true)
        .recipes(uploadedItems)
        .build();
}
```

---

#### 7.3 `RecipeStoreService` caching for multi-recipe

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeStoreService.java`

Add `storeValidRecipes(String contentHash, String url, List<String> recipeYamls)` overload.

For storage, serialize the list as a JSON array of YAML strings:
```java
// Store: ["yaml1...", "yaml2..."]
String storedContent = objectMapper.writeValueAsString(recipeYamls);
```

When reading back, detect format:
- If `storedContent` starts with `[` → JSON array of YAMLs (multi-recipe)
- Otherwise → single YAML string (legacy format, backward compat)

Add `recipeCount` to `StoredRecipe` for quick lookup:
```java
record StoredRecipe(
    String contentHash,
    String sourceUrl,
    String recipeYaml,    // JSON array or single YAML
    int recipeCount,      // NEW: 0 = invalid, 1 = single, N = multi
    boolean isValid,
    Instant createdAt,
    Instant lastUpdatedAt,
    long version
)
```

---

#### 7.4 Update `RecipeResponse` and `RecipeItemResult`

**File to modify:**
`extractor/src/main/java/net/shamansoft/cookbook/dto/RecipeResponse.java`

**File to create:**
`extractor/src/main/java/net/shamansoft/cookbook/dto/RecipeItemResult.java`

```java
// New DTO for individual recipe in multi-recipe response
public record RecipeItemResult(
    String title,
    String driveFileId,
    String driveFileUrl
) {}
```

Updated `RecipeResponse`:
```java
@Builder
public record RecipeResponse(
    String url,
    String title,           // first recipe title (backward compat)
    String driveFileId,     // first recipe fileId (backward compat)
    String driveFileUrl,    // first recipe fileUrl (backward compat)
    Boolean isRecipe,
    List<RecipeItemResult> recipes  // NEW: all uploaded recipes
) {}
```

**API response for clients (backward compatible):**
```json
{
  "url": "...",
  "title": "...",
  "driveFileId": "...",
  "driveFileUrl": "...",
  "isRecipe": true,
  "recipes": [
    { "title": "...", "driveFileId": "...", "driveFileUrl": "..." },
    { "title": "...", "driveFileId": "...", "driveFileUrl": "..." }
  ]
}
```

---

### 8. Configuration

---

#### 8.1 Add adaptive-cleaning properties

**File to modify:**
`extractor/src/main/resources/application.yaml`

```yaml
recipe:
  # ... existing config ...
  adaptive-cleaning:
    # Enable/disable adaptive HTML cleaning with confidence feedback loop.
    # When true: if LLM returns is_recipe=false with confidence >= threshold,
    # retry with progressively less restrictive HTML cleaning strategies.
    enabled: ${RECIPE_ADAPTIVE_CLEANING_ENABLED:true}
    # Confidence threshold that triggers a retry with less restrictive cleaning.
    # Range: 0.0–1.0. Default 0.5 (halfway uncertain).
    confidence-threshold: ${RECIPE_ADAPTIVE_CLEANING_THRESHOLD:0.5}
```

---

#### 8.2 GraalVM reflection hints

**File to check/modify:**
`extractor/src/main/resources/META-INF/native-image/reflect-config.json` (if it exists)

Add entries for new DTOs that are deserialized from JSON:
- `net.shamansoft.cookbook.service.gemini.GeminiExtractionResult`
- `net.shamansoft.cookbook.service.gemini.GeminiExtractionResult.RecipeData`
- `net.shamansoft.cookbook.dto.RecipeItemResult`

---

### 9. Testing

---

#### 9.1 `GeminiExtractionResultTest`

**File to create:**
`extractor/src/test/java/net/shamansoft/cookbook/service/gemini/GeminiExtractionResultTest.java`

Test cases:
- Deserialize multi-recipe JSON → `recipes` list has 2 entries
- Deserialize non-recipe JSON (`is_recipe: false`, `recipes: []`) → `isRecipe=false`, list empty
- Deserialize with `recipe_confidence: 0.7` → confidence field set correctly
- Missing optional fields → no Jackson exception (verify `@JsonIgnoreProperties`)

---

#### 9.2 `AdaptiveCleaningTransformerServiceTest`

**File to create:**
`extractor/src/test/java/net/shamansoft/cookbook/service/AdaptiveCleaningTransformerServiceTest.java`

Test cases:
| Scenario | Initial Strategy | LLM Response | Expected |
|---|---|---|---|
| First attempt succeeds | STRUCTURED_DATA | `isRecipe=true` | Return success, no retry |
| Low confidence not-recipe | STRUCTURED_DATA | `isRecipe=false, confidence=0.2` | Return `notRecipe` immediately, no retry |
| High confidence not-recipe, retry succeeds | STRUCTURED_DATA | Attempt 1: `isRecipe=false, conf=0.7`; Attempt 2: `isRecipe=true` | Call `processWithStrategy(SECTION_BASED)`, return recipes |
| All strategies exhausted | FALLBACK | `isRecipe=false, conf=0.6` | Return `notRecipe` (no more strategies) |
| Start at SECTION_BASED, retry with CONTENT_FILTER | SECTION_BASED | `isRecipe=false, conf=0.6` | Skips STRUCTURED_DATA, goes directly to CONTENT_FILTER |
| `adaptiveCleaningEnabled=false` | any | `isRecipe=false` | No retry regardless of confidence |
| Confidence drops below threshold mid-chain | STRUCTURED_DATA | Attempt 1: `conf=0.7`; Attempt 2: `conf=0.3` | Stop after attempt 2 |

---

#### 9.3 Update `ValidatingTransformerServiceTest`

**File to modify:**
`extractor/src/test/java/net/shamansoft/cookbook/service/ValidatingTransformerServiceTest.java`

- Update all `Transformer.Response.recipe(r)` to `Transformer.Response.recipes(List.of(r))`
- Add test: 2 recipes returned, both valid → both post-processed, both in result
- Add test: 2 recipes, one valid + one invalid → valid subset returned, warning logged
- Add test: 2 recipes, both invalid → retry with consolidated feedback
- Add test: all retries exhausted → `notRecipe` with last confidence value returned

---

#### 9.4 Update `RecipeServiceCreateRecipeTest`

**File to modify:**
`extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceCreateRecipeTest.java`

- Test: single recipe → `recipes` list has 1 entry; backward-compat fields populated
- Test: two recipes → `recipes` list has 2 entries; 2 Drive upload calls made
- Test: not-recipe → `recipes` list empty; `isRecipe=false`
- Test: cached multi-recipe → parsed from stored JSON array, no transformer call
- Test: `RecipeStoreService.storeValidRecipes()` called with YAML list

---

#### 9.5 Update `GeminiRestTransformerTest`

**File to modify:**
`extractor/src/test/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformerTest.java`

- Test deserialization of `GeminiExtractionResult` wrapper
- Test `RecipeData` → `Recipe` conversion with `isRecipe=true` injected
- Test multi-recipe response produces `Response.recipes(...)` with 2 entries
- Test `recipe_confidence` is propagated to `Transformer.Response.confidence()`

---

## Implementation Sequencing and Dependencies

```
1.1 GeminiExtractionResult DTO          3.1/3.2 HtmlCleaner extension
         ↓                                         ↓
1.2 recipe-schema-2.0.0.json                       ↓
         ↓                                         ↓
1.3 Transformer.Response update                    ↓
         ↓                          _______________↓
2.1 GeminiRestTransformer      4.1 AdaptiveCleaningTransformerService
         ↓                              ↓
2.2 RequestBuilder             5.x ValidatingTransformerService
         ↓                              ↓
2.3 prompt.md                  7.x RecipeService + RecipeResponse
                                        ↓
                                   9.x Tests
```

**Suggested implementation order:**
1. `Transformer.Response` update (1.3) — foundational, unblocks all downstream
2. `GeminiExtractionResult` DTO (1.1) + `recipe-schema-2.0.0.json` (1.2) in parallel
3. `HtmlCleaner` extension (3.1, 3.2) — independent
4. `GeminiRestTransformer` update (2.1) + `RequestBuilder` (2.2) + `prompt.md` (2.3) in parallel
5. `ValidatingTransformerService` update (5.x)
6. `AdaptiveCleaningTransformerService` (4.x) — needs #3 and #5
7. `RecipeService` + `RecipeResponse` + `RecipeItemResult` (7.x) — needs #6
8. Configuration (8.x) — needed by #6
9. Tests (9.x) — final

---

## Architectural Decisions

| Decision | Options Considered | Chosen | Rationale |
|---|---|---|---|
| `GeminiExtractionResult` vs extending `Recipe` | Reuse `Recipe` / New wrapper DTO | New `GeminiExtractionResult` + inner `RecipeData` | Keeps SDK's `Recipe` clean; decouples wire format from storage format |
| New schema vs updating 1.0.0 | Update in-place / Create 2.0.0 | New `recipe-schema-2.0.0.json` | Avoids breaking stored recipe Bean Validation |
| `AdaptiveCleaningTransformerService` interface | Implements `Transformer` / New `RawTransformer` interface | Implements `Transformer` (receives raw HTML) | Minimal change to `RecipeService`; single transformer abstraction |
| Multi-recipe storage format | Multi-doc YAML / JSON array of YAML strings / Multiple rows | JSON array of YAML strings in single row | Minimal schema change; backward compat with single-recipe format (starts with `[` detection) |
| API backward compat | Breaking change / Additive | Additive (`recipes[]` added; old fields kept) | Existing Chrome extension clients continue to work |
| Partial validation failure | All-or-nothing / Best-effort subset | Best-effort (return valid subset) | One bad recipe on a page shouldn't block all valid ones |
| Confidence threshold scope | Per-strategy / Global | Global config property | Simpler; can add per-strategy overrides later if needed |

---

## Potential Challenges

1. **`@Primary` conflict:** `ValidatingTransformerService` currently has `@Primary`. When `AdaptiveCleaningTransformerService` takes over, verify no test `@Autowired` or `@MockitoBean` expects `ValidatingTransformerService` as primary.

2. **`Recipe.isRecipe` field injection:** `Recipe` is an SDK record with `isRecipe` as first parameter. Converting `RecipeData` → `Recipe` requires setting `isRecipe=true`. The record constructor is immutable — add a static factory `Recipe.fromRecipeData(RecipeData data)` or construct inline.

3. **`recipe.llm.retry` interaction:** When `recipe.llm.retry=0`, `ValidatingTransformerService` skips validation. The confidence-based adaptive cleaning loop still works because confidence comes from the LLM response, not validation. These are orthogonal.

4. **Firestore `StoredRecipe` schema migration:** Adding `recipeCount` field to Firestore documents requires either a migration script or null-safe reading (treat missing `recipeCount` as `1`). Prefer null-safe reading for zero-downtime deployment.

5. **GraalVM reflection for `GeminiExtractionResult`:** Jackson deserializes this class at runtime. In native image, reflection is pre-registered. Verify the existing `reflect-config.json` setup and add new entries.

6. **`processWithStrategy()` when strategy not found:** If `HtmlCleaner` strategies list doesn't contain a strategy (e.g., it's disabled via config), `processWithStrategy()` falls back to raw HTML (FALLBACK). This is correct — we still give LLM a chance with raw HTML.

7. **`htmlPreprocessor` removal from `RecipeService`:** After moving the cleaning step into `AdaptiveCleaningTransformerService`, the `htmlPreprocessor` field in `RecipeService` becomes unused. Remove it to avoid confusion. Verify `HtmlCleaner` is not injected elsewhere in `RecipeService`.
