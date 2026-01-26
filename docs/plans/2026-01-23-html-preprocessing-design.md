# HTML Preprocessing for Token Reduction - Design Document

**Date**: 2026-01-23
**Status**: Approved
**Complexity Level**: Level 1 (Simple Heuristics)

## Problem Statement

The `POST /v1/recipes` endpoint sends raw HTML to Gemini LLM for recipe extraction. Raw HTML contains valuable recipe data but also significant noise (scripts, styles, navigation, ads, footers). This noise consumes the majority of LLM tokens while contributing little value to recipe extraction.

**Goal**: Algorithmically reduce HTML size by 60-80% while preserving all recipe-relevant content.

## Solution Overview

Implement a hybrid HTML preprocessing pipeline that:
1. Attempts to extract structured data (JSON-LD schema.org/Recipe)
2. Falls back to section-based extraction using keyword scoring
3. Falls back to content filtering as final safety net
4. Always returns valid HTML (uses raw HTML if all strategies fail)
5. Emits detailed metrics on reduction ratios and strategy effectiveness

## Architecture

### High-Level Flow

```
RecipeService.createOrGetCached()
    ↓
1. HtmlExtractor.extractHtml()
    → Returns raw HTML (existing)
    ↓
2. HtmlPreprocessorService.preprocess()  [NEW]
    → Returns PreprocessingResult { cleanedHtml, metrics }
    ↓
3. Emit compression metrics [NEW]
    → Log: "HTML preprocessing: 150KB → 12KB (92% reduction)"
    → Metric: html_preprocessing_reduction_ratio = 0.92
    ↓
4. Transformer.transform(cleanedHtml)
    → Returns Recipe (existing)
```

### New Component: HtmlPreprocessorService

**Location**: `extractor/src/main/java/net/shamansoft/cookbook/service/HtmlPreprocessorService.java`

**Responsibilities**:
- Extract and validate structured recipe data (JSON-LD)
- Score HTML sections for recipe relevance
- Clean HTML using hybrid strategy cascade
- Emit preprocessing metrics (reduction ratio, strategy used)
- Guarantee fallback to raw HTML on failures

**Dependencies**:
- Jsoup 1.17.2 (HTML parsing and manipulation)
- Spring Boot Actuator MeterRegistry (metrics)
- Jackson ObjectMapper (JSON-LD parsing)
- HtmlPreprocessingConfig (configuration properties)

**Returns**: `PreprocessingResult` record

```java
public record PreprocessingResult(
    String cleanedHtml,          // Cleaned HTML to send to LLM
    int originalSize,            // Original HTML size in chars
    int cleanedSize,             // Cleaned HTML size in chars
    double reductionRatio,       // 0.92 = 92% reduction
    Strategy strategyUsed,       // Which strategy succeeded
    String metricsMessage        // Human-readable log message
) {}

public enum Strategy {
    STRUCTURED_DATA,  // Found and used JSON-LD schema.org/Recipe
    SECTION_BASED,    // Extracted high-scoring recipe sections
    CONTENT_FILTER,   // Removed unwanted elements only
    FALLBACK,         // Used raw HTML (preprocessing failed)
    DISABLED          // Preprocessing disabled in config
}
```

## Configuration

**Location**: `extractor/src/main/resources/application.yaml`

```yaml
cookbook:
  html-preprocessing:
    enabled: true
    structured-data:
      enabled: true
      # Minimum completeness score (0-100) to use structured data directly
      min-completeness: 70
    section-based:
      enabled: true
      # Minimum section score (0-100) to trust section extraction
      min-confidence: 70
      # Recipe-related keywords for scoring
      keywords:
        - ingredients
        - instructions
        - directions
        - preparation
        - method
        - recipe
        - steps
        - cook
        - bake
    content-filter:
      # Minimum output size (chars) - fallback to raw HTML if smaller
      min-output-size: 500
    fallback:
      # Always use raw HTML if preprocessing produces output smaller than this
      min-safe-size: 300
```

**Configuration Class**: `HtmlPreprocessingConfig.java`

```java
@ConfigurationProperties("cookbook.html-preprocessing")
public class HtmlPreprocessingConfig {
    private boolean enabled = true;
    private StructuredData structuredData = new StructuredData();
    private SectionBased sectionBased = new SectionBased();
    private ContentFilter contentFilter = new ContentFilter();
    private Fallback fallback = new Fallback();

    // Nested configuration classes...
}
```

## Strategy Implementations

### Strategy Selection Logic

```java
public PreprocessingResult preprocess(String html, String url) {
    int originalSize = html.length();

    // Validation
    if (html == null || html.isBlank()) {
        return buildFallbackResult("", 0, "Empty input");
    }

    if (!config.enabled) {
        return buildResult(html, originalSize, Strategy.DISABLED);
    }

    try {
        // Strategy 1: Structured Data
        if (config.structuredData.enabled) {
            Optional<String> structured = extractStructuredData(html);
            if (structured.isPresent() && isComplete(structured.get())) {
                return buildResult(structured.get(), originalSize, STRUCTURED_DATA);
            }
        }

        // Strategy 2: Section-based extraction
        if (config.sectionBased.enabled) {
            Optional<String> sections = extractRecipeSections(html);
            if (sections.isPresent() && meetsConfidence(sections.get())) {
                return buildResult(sections.get(), originalSize, SECTION_BASED);
            }
        }

        // Strategy 3: Content filtering
        String filtered = filterUnwantedContent(html);
        if (filtered.length() >= config.contentFilter.minOutputSize) {
            return buildResult(filtered, originalSize, CONTENT_FILTER);
        }

        // Fallback: Use raw HTML
        return buildResult(html, originalSize, FALLBACK);

    } catch (Exception e) {
        log.error("HTML preprocessing failed for URL: {}", url, e);
        meterRegistry.counter("html.preprocessing.errors",
            "error_type", e.getClass().getSimpleName()).increment();
        return buildResult(html, originalSize, FALLBACK);
    }
}
```

### Strategy 1: Structured Data Extraction

Extract and validate JSON-LD schema.org/Recipe:

```java
private Optional<String> extractStructuredData(String html) {
    Document doc = Jsoup.parse(html);
    Elements scripts = doc.select("script[type=application/ld+json]");

    for (Element script : scripts) {
        try {
            JsonNode json = objectMapper.readTree(script.html());

            // Handle both single object and @graph array
            List<JsonNode> candidates = findRecipeNodes(json);

            for (JsonNode node : candidates) {
                if (isRecipeType(node)) {
                    int completeness = scoreCompleteness(node);
                    if (completeness >= config.structuredData.minCompleteness) {
                        log.debug("Found structured recipe data, completeness: {}%", completeness);
                        return Optional.of(node.toString());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("Invalid JSON-LD in script tag, skipping");
        }
    }
    return Optional.empty();
}

private int scoreCompleteness(JsonNode recipe) {
    int score = 0;
    // Required fields (20 points each)
    if (recipe.has("name")) score += 20;
    if (recipe.has("recipeIngredient")) score += 20;
    if (recipe.has("recipeInstructions")) score += 20;

    // Nice-to-have fields (10 points each)
    if (recipe.has("totalTime")) score += 10;
    if (recipe.has("recipeYield")) score += 10;
    if (recipe.has("description")) score += 10;
    if (recipe.has("image")) score += 10;

    return Math.min(100, score);
}
```

**Expected reduction**: 90-95% (JSON-LD is typically 1-2KB vs 50-200KB HTML)

### Strategy 2: Section-Based Extraction

Score sections by recipe keyword density:

```java
private Optional<String> extractRecipeSections(String html) {
    Document doc = Jsoup.parse(html);

    // Find all potential container elements
    Elements candidates = doc.select("article, section, div[class*=recipe], div[id*=recipe], main");

    Element bestSection = null;
    int bestScore = 0;

    for (Element candidate : candidates) {
        int score = scoreSection(candidate);
        if (score > bestScore) {
            bestScore = score;
            bestSection = candidate;
        }
    }

    if (bestScore >= config.sectionBased.minConfidence) {
        // Clean the section but keep structure
        cleanElement(bestSection);
        String result = bestSection.html();

        if (result.length() >= config.contentFilter.minOutputSize) {
            log.debug("Section-based extraction, score: {}, size: {} chars", bestScore, result.length());
            return Optional.of(result);
        }
    }

    return Optional.empty();
}

private int scoreSection(Element element) {
    String text = element.text().toLowerCase();
    int score = 0;

    // Keyword presence (10 points each)
    for (String keyword : config.sectionBased.keywords) {
        if (text.contains(keyword)) score += 10;
    }

    // Structural patterns (bonus points)
    if (element.select("ul, ol").size() >= 2) score += 20; // Lists for ingredients/steps
    if (element.select("h2, h3").size() >= 2) score += 10; // Section headers
    if (text.length() > 1000) score += 10; // Substantial content

    return Math.min(100, score);
}
```

**Expected reduction**: 60-80% (extracts only recipe-relevant sections)

### Strategy 3: Content Filtering (Fallback)

Remove unwanted elements but keep all remaining content:

```java
private String filterUnwantedContent(String html) {
    Document doc = Jsoup.parse(html);

    // Remove non-content elements
    doc.select("script, style, noscript").remove();
    doc.select("nav, header, footer").remove();
    doc.select("iframe, embed, object").remove();

    // Remove ads and social media (by common class/id patterns)
    doc.select("[class*=ad], [id*=ad]").remove();
    doc.select("[class*=social], [class*=share]").remove();
    doc.select("[class*=comment], [id*=comment]").remove();
    doc.select("[class*=sidebar], [id*=sidebar]").remove();

    // Remove hidden elements
    doc.select("[style*=display:none], [style*=visibility:hidden]").remove();

    // Clean attributes to reduce size
    for (Element el : doc.getAllElements()) {
        el.removeAttr("style");
        el.removeAttr("class");
        el.removeAttr("id");
        el.removeAttr("onclick");
        el.removeAttr("data-*");
    }

    String cleaned = doc.body().html();
    log.debug("Content filtering applied, size: {} chars", cleaned.length());
    return cleaned;
}
```

**Expected reduction**: 30-50% (conservative cleanup)

## Metrics & Observability

### Emitted Metrics

```java
private void emitMetrics(Strategy strategy, int originalSize, int cleanedSize, double ratio) {
    // Counter: Which strategies are being used
    meterRegistry.counter("html.preprocessing.strategy",
        "type", strategy.name()).increment();

    // Gauge: Current reduction ratio (0.0-1.0)
    meterRegistry.gauge("html.preprocessing.reduction_ratio", ratio);

    // Histogram: Distribution of HTML sizes
    meterRegistry.summary("html.preprocessing.original_size").record(originalSize);
    meterRegistry.summary("html.preprocessing.cleaned_size").record(cleanedSize);
}
```

### Logging

```java
private PreprocessingResult buildResult(String cleanedHtml, int originalSize, Strategy strategy) {
    int cleanedSize = cleanedHtml.length();
    double reductionRatio = originalSize > 0
        ? (double)(originalSize - cleanedSize) / originalSize
        : 0.0;

    String message = String.format(
        "Strategy: %s, %d → %d chars (%.1f%% reduction)",
        strategy, originalSize, cleanedSize, reductionRatio * 100
    );

    emitMetrics(strategy, originalSize, cleanedSize, reductionRatio);

    return new PreprocessingResult(
        cleanedHtml,
        originalSize,
        cleanedSize,
        reductionRatio,
        strategy,
        message
    );
}
```

**Example log output**:
```
INFO  RecipeService - HTML preprocessing - URL: https://example.com/recipe, Strategy: STRUCTURED_DATA, 150000 → 1800 chars (98.8% reduction)
INFO  RecipeService - HTML preprocessing - URL: https://example.com/blog, Strategy: SECTION_BASED, 80000 → 15000 chars (81.3% reduction)
WARN  RecipeService - Cleaned HTML too small (250 chars), falling back to raw HTML
ERROR RecipeService - HTML preprocessing failed for URL: https://example.com/broken, using raw HTML
```

## Error Handling & Edge Cases

### Fallback Mechanisms

1. **Empty/null HTML**: Returns empty result, logs warning
2. **Preprocessing disabled**: Returns raw HTML with DISABLED strategy
3. **Exception during processing**: Catches, logs error, emits metric, returns raw HTML
4. **Output too small** (<300 chars): Falls back to raw HTML
5. **All strategies fail**: Uses raw HTML with FALLBACK strategy

### Edge Cases Handled

| Edge Case | Handling |
|-----------|----------|
| Malformed HTML | Jsoup handles gracefully, continues processing |
| No recipe content found | Falls through strategies to content filter, then raw HTML |
| Multiple JSON-LD scripts | Iterates through all, picks first valid recipe with best completeness score |
| Nested @graph arrays | Recursively flattens and searches for Recipe nodes |
| Very small pages (<500 chars) | Detected by min-output-size check, uses raw HTML |
| Non-English content | Keyword scoring may degrade, but content filter still works |
| JavaScript-rendered content | Won't have structured data, relies on section/filter strategies |

### Error Metric

```java
meterRegistry.counter("html.preprocessing.errors",
    "error_type", e.getClass().getSimpleName()).increment();
```

Tracks preprocessing failures by exception type for debugging.

## Integration Points

### RecipeService Modification

**Location**: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`

**Changes in `createOrGetCached()` method (lines 68-94)**:

```java
private Transformer.Response createOrGetCached(String url, String sourceHtml, String compression) throws IOException {
    String contentHash = contentHashService.generateContentHash(url);
    Optional<StoredRecipe> stored = recipeStoreService.findStoredRecipeByHash(contentHash);
    if (stored.isEmpty()) {
        // 1. Extract raw HTML (existing)
        String html = htmlExtractor.extractHtml(url, sourceHtml, compression);

        // 2. Preprocess HTML (NEW)
        PreprocessingResult preprocessed = htmlPreprocessor.preprocess(html, url);
        log.info("HTML preprocessing - URL: {}, {}", url, preprocessed.metricsMessage());

        // 3. Transform cleaned HTML (modified)
        var response = transformer.transform(preprocessed.cleanedHtml());

        if (response.isRecipe()) {
            String yamlContent = convertRecipeToYaml(response.recipe());
            recipeStoreService.storeValidRecipe(contentHash, url, yamlContent);
        } else {
            log.warn("Gemini determined content is NOT a recipe - URL: {}, Hash: {}", url, contentHash);
            recipeStoreService.storeInvalidRecipe(contentHash, url);
        }
        return response;
    } else {
        // Cached path unchanged
        StoredRecipe storedRecipe = stored.get();
        if (storedRecipe.isValid()) {
            Recipe recipe = recipeParser.parse(storedRecipe.getRecipeYaml());
            return Transformer.Response.recipe(recipe);
        } else {
            return Transformer.Response.notRecipe();
        }
    }
}
```

**Dependency Injection**:

```java
@Service
@RequiredArgsConstructor
public class RecipeService {
    // ... existing dependencies
    private final HtmlPreprocessorService htmlPreprocessor; // NEW
}
```

## Testing Strategy

### Unit Tests

**HtmlPreprocessorServiceTest.java** (~400-500 lines)

```java
@SpringBootTest
class HtmlPreprocessorServiceTest {

    @Autowired
    private HtmlPreprocessorService preprocessor;

    @Test
    void shouldExtractStructuredDataWhenPresent() {
        String html = """
            <html>
            <script type="application/ld+json">
            {
              "@type": "Recipe",
              "name": "Chocolate Cake",
              "recipeIngredient": ["flour", "sugar"],
              "recipeInstructions": "Mix and bake"
            }
            </script>
            <body>lots of other content...</body>
            </html>
            """;

        PreprocessingResult result = preprocessor.preprocess(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
        assertThat(result.reductionRatio()).isGreaterThan(0.8); // 80%+ reduction
        assertThat(result.cleanedHtml()).contains("Chocolate Cake");
    }

    @Test
    void shouldUseContentFilterWhenSectionScoreTooLow() {
        String html = """
            <html>
            <head><script>console.log('test');</script></head>
            <body>
                <nav>Navigation</nav>
                <p>No recipe keywords here</p>
                <footer>Footer</footer>
            </body>
            </html>
            """;

        PreprocessingResult result = preprocessor.preprocess(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.CONTENT_FILTER);
        assertThat(result.cleanedHtml()).doesNotContain("<script>");
        assertThat(result.cleanedHtml()).doesNotContain("<nav>");
    }

    @Test
    void shouldFallbackToRawHtmlOnException() {
        // Test with malformed/edge case HTML
        String html = "<html>valid html</html>";

        PreprocessingResult result = preprocessor.preprocess(html, "test-url");

        // Should not throw, should return valid result
        assertThat(result).isNotNull();
        assertThat(result.cleanedHtml()).isNotBlank();
    }

    @Test
    void shouldFallbackWhenOutputTooSmall() {
        String html = "<html><body><p>tiny</p></body></html>";

        PreprocessingResult result = preprocessor.preprocess(html, "test-url");

        // Output would be < min-safe-size, should use raw HTML
        assertThat(result.strategyUsed()).isEqualTo(Strategy.FALLBACK);
        assertThat(result.reductionRatio()).isEqualTo(0.0);
    }

    @Test
    void shouldScoreSectionsCorrectly() {
        String html = """
            <html>
            <body>
                <article>
                    <h2>Recipe for Cookies</h2>
                    <h3>Ingredients</h3>
                    <ul>
                        <li>Flour</li>
                        <li>Sugar</li>
                    </ul>
                    <h3>Instructions</h3>
                    <ol>
                        <li>Mix ingredients</li>
                        <li>Bake at 350°F</li>
                    </ol>
                </article>
            </body>
            </html>
            """;

        PreprocessingResult result = preprocessor.preprocess(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.SECTION_BASED);
        assertThat(result.reductionRatio()).isGreaterThan(0.3);
    }
}
```

**Test Coverage Goal**: 80%+ line coverage for `HtmlPreprocessorService`

### Integration Tests

**AddRecipeIT.java** - Add new test:

```java
@Test
@DisplayName("Should preprocess HTML and reduce token usage")
void shouldPreprocessHtmlBeforeTransform() throws Exception {
    setupStorageInfoInFirestore("test-user-123", "valid-drive-token");

    String largeHtml = """
        <html>
        <head>
            <script>/* lots of JS */</script>
            <style>/* lots of CSS */</style>
        </head>
        <body>
            <nav>Navigation menu...</nav>
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Test Recipe",
              "recipeIngredient": ["ingredient1", "ingredient2"],
              "recipeInstructions": "Mix and cook"
            }
            </script>
            <div class="ads">Advertisement content...</div>
            <footer>Footer content...</footer>
        </body>
        </html>
        """;

    Request request = new Request(largeHtml, "Test Recipe", "https://example.com/test");
    HttpEntity<Request> entity = new HttpEntity<>(request, createAuthHeaders());

    ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
        "http://localhost:" + port + RECIPE_PATH + "?compression=none",
        entity,
        RecipeResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isRecipe()).isTrue();

    // Verify Gemini received preprocessed (smaller) HTML
    // This would be validated through WireMock request verification
}
```

### Manual Testing Checklist

Test with real recipe websites:

- [ ] AllRecipes.com - Has JSON-LD structured data
- [ ] Food Network - Section-based extraction
- [ ] NYTimes Cooking - Structured data + paywall content
- [ ] Random food blog - Unstructured HTML
- [ ] Non-recipe page - Should handle gracefully
- [ ] Malformed HTML - Should not crash
- [ ] Very small page (<1KB) - Should use raw HTML
- [ ] Very large page (>500KB) - Should reduce significantly

## Implementation Plan

### Files to Create

1. **`extractor/src/main/java/net/shamansoft/cookbook/service/HtmlPreprocessorService.java`**
   - Main service class (~300-400 lines)
   - All strategy implementations
   - Metrics emission
   - Nested `PreprocessingResult` record and `Strategy` enum

2. **`extractor/src/main/java/net/shamansoft/cookbook/config/HtmlPreprocessingConfig.java`**
   - Configuration properties class
   - `@ConfigurationProperties("cookbook.html-preprocessing")`
   - Nested config classes for each strategy

3. **`extractor/src/test/java/net/shamansoft/cookbook/service/HtmlPreprocessorServiceTest.java`**
   - Comprehensive unit tests (~400-500 lines)
   - Test all strategies, edge cases, error handling

### Files to Modify

1. **`extractor/build.gradle.kts`**
   ```kotlin
   dependencies {
       // ... existing dependencies
       implementation("org.jsoup:jsoup:1.17.2")  // NEW
   }
   ```

2. **`extractor/src/main/resources/application.yaml`**
   - Add `cookbook.html-preprocessing` configuration block (as shown above)

3. **`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`**
   - Add `HtmlPreprocessorService` dependency injection
   - Modify `createOrGetCached()` method (lines 68-94)
   - Add preprocessing call between extraction and transformation
   - Add preprocessing metrics logging

4. **`extractor/src/intTest/java/net/shamansoft/cookbook/AddRecipeIT.java`**
   - Add integration test for preprocessing flow

### Implementation Phases

**Phase 1: Foundation** (Day 1 morning, ~4 hours)
- [ ] Add Jsoup dependency to `build.gradle.kts`
- [ ] Create `HtmlPreprocessingConfig` with all properties
- [ ] Add configuration to `application.yaml`
- [ ] Create `PreprocessingResult` record and `Strategy` enum
- [ ] Create `HtmlPreprocessorService` skeleton
- [ ] Implement basic fallback-only behavior
- [ ] Add dependency injection to `RecipeService`
- [ ] Verify app starts and fallback works

**Phase 2: Strategy Implementation** (Day 1 afternoon + Day 2 morning, ~6 hours)
- [ ] Implement structured data extraction
  - [ ] JSON-LD parsing
  - [ ] Completeness scoring
  - [ ] Handle @graph arrays
- [ ] Implement section-based extraction
  - [ ] Section candidate selection
  - [ ] Keyword scoring algorithm
  - [ ] Structural pattern bonuses
- [ ] Implement content filtering
  - [ ] Element removal (scripts, nav, ads)
  - [ ] Attribute cleanup
- [ ] Implement strategy selection cascade
- [ ] Add safety checks (min-safe-size, exception handling)

**Phase 3: Integration & Metrics** (Day 2 midday, ~2 hours)
- [ ] Integrate into `RecipeService.createOrGetCached()`
- [ ] Add metrics emission (counters, gauges, summaries)
- [ ] Add comprehensive logging
- [ ] Test end-to-end flow with sample HTML

**Phase 4: Testing** (Day 2 afternoon, ~4 hours)
- [ ] Write unit tests for all strategies
- [ ] Write unit tests for edge cases
- [ ] Write unit tests for error handling
- [ ] Add integration test to `AddRecipeIT`
- [ ] Manual testing with real recipe sites
- [ ] Verify metrics appear in actuator endpoints
- [ ] Document any tuning needed

**Total Estimated Effort**: 1.5-2 days (12-16 hours)

### Deployment Considerations

- **Feature Flag**: Preprocessing can be disabled via `cookbook.html-preprocessing.enabled: false`
- **Gradual Rollout**: Start with preprocessing enabled, monitor metrics
- **Rollback Plan**: Disable preprocessing if issues arise, already have fallback logic
- **Monitoring**: Watch for increased error rates, unexpected fallback usage
- **Cost Savings**: Monitor Gemini API token usage before/after deployment

## Success Metrics

### Short-term (Week 1)

- [ ] Preprocessing enabled in production
- [ ] 60%+ average reduction ratio across all requests
- [ ] 30%+ of requests using STRUCTURED_DATA strategy
- [ ] <1% fallback to raw HTML due to errors
- [ ] No increase in recipe extraction failures

### Medium-term (Month 1)

- [ ] 70%+ average reduction ratio
- [ ] 20-30% cost reduction in Gemini API usage
- [ ] Identify and tune thresholds based on real data
- [ ] Add more recipe-specific keywords if needed

### Long-term (Quarter 1)

- [ ] Consider Level 2 (DOM Scoring) if reduction not meeting goals
- [ ] Consider caching preprocessed HTML for frequently accessed URLs
- [ ] Evaluate expanding to other LLM use cases

## Future Enhancements (Out of Scope for Level 1)

- **Level 2: DOM Scoring** - More sophisticated content density analysis
- **ML-based extraction** - Train classifier for recipe vs non-recipe sections
- **Caching preprocessed HTML** - Cache results by content hash
- **Language-specific keywords** - Detect language, use localized recipe keywords
- **Image extraction** - Identify and extract recipe images for media arrays
- **Adaptive thresholds** - Learn optimal thresholds from validation feedback

## References

- [Jsoup Documentation](https://jsoup.org/)
- [schema.org Recipe Specification](https://schema.org/Recipe)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [HTML5 Specification](https://html.spec.whatwg.org/)

---

**Approved by**: User
**Ready for Implementation**: Yes
