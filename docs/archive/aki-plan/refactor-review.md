# HtmlCleaner Refactoring Review

**Reviewer:** Claude Code
**Date:** 2026-01-24
**Branch:** `parse-html`
**Refactoring Goal:** Extract strategy pattern from HtmlCleaner monolithic implementation

## Executive Summary

The junior developer successfully extracted the Strategy pattern from HtmlCleaner, creating a clean interface and three concrete implementations. However, **the refactoring introduced critical business logic changes that break existing tests** (4 out of 19 tests failing). The core architecture is sound, but the implementation has several issues that must be fixed before merging.

**Status:** ❌ **NOT READY FOR MERGE** - Business logic broken

---

## Test Results

```
19 tests completed, 4 failed

FAILED TESTS:
✗ HtmlCleanerTest.shouldExtractStructuredDataWhenPresent() - Expected: STRUCTURED_DATA, Got: CONTENT_FILTER
✗ HtmlCleanerTest.shouldHandleRecipeTypeAsArray() - Expected: STRUCTURED_DATA, Got: (unknown)
✗ HtmlCleanerTest.shouldPreferStructuredDataOverSections() - Expected: STRUCTURED_DATA, Got: (unknown)
✗ HtmlCleanerTest.shouldHandleGraphArrayInJsonLd() - Expected: STRUCTURED_DATA, Got: (unknown)
```

**Root Cause:** All failures are related to StructuredDataStrategy returning `Optional.empty()` when it should return structured data.

---

## Critical Issues (Must Fix)

### 1. ❌ **BUSINESS LOGIC ALTERED - Size Check Added Incorrectly**
**Severity:** CRITICAL
**File:** `StructuredDataStrategy.java:45-46`
**Impact:** Breaks all structured data extraction tests

**Problem:**
```java
// NEW CODE (WRONG):
String jsonString = objectMapper.writeValueAsString(node);
if (jsonString.length() >= config.getFallback().getMinSafeSize()) {  // ← EXTRA CHECK NOT IN ORIGINAL
    return Optional.of(jsonString);
}
```

**Original Code:**
```java
// ORIGINAL CODE (CORRECT):
log.debug("Found structured recipe data, completeness: {}%", completeness);
String jsonString = objectMapper.writeValueAsString(node);
return Optional.of(jsonString);  // ← No size check!
```

**Why This Is Wrong:**
- The original code **never** checked `minSafeSize` for structured data
- The size check is only for the final fallback decision in the main `process()` method
- This changes the business logic: small but complete structured recipes are now rejected
- Causes all structured data tests to fail because the JSON is smaller than `minSafeSize` (300 chars)

**Fix Required:**
Remove the size check and return immediately after serialization:
```java
if (completeness >= config.getStructuredData().getMinCompleteness()) {
    String jsonString = objectMapper.writeValueAsString(node);
    return Optional.of(jsonString);  // Return without size check
}
```

---

### 2. ❌ **LOGIC BUG - Incorrect Filter Logic in cleanupAttributes**
**Severity:** CRITICAL
**File:** `HtmlCleaner.java:46`
**Impact:** `data-*` attributes are NOT being removed; event handlers ARE being removed

**Problem:**
```java
var dataAttrs = el.attributes().asList().stream()
    .map(Attribute::getKey)
    .filter(key -> key.startsWith("data-"))
    .filter(key -> key.startsWith("on"))  // ← WRONG! This filters FOR "on*", not "data-*"
    .toList();
```

**Original Code:**
```java
// ORIGINAL CODE (CORRECT):
var dataAttrs = el.attributes().asList().stream()
    .map(Attribute::getKey)
    .filter(key -> key.startsWith("data-"))
    .filter(key -> key.startsWith("on"))  // ← Same bug exists in original!
    .toList();
```

**Analysis:**
- The logic is incorrect: chained `filter()` operations are AND conditions
- An attribute cannot start with BOTH `data-` AND `on` (impossible condition)
- This means **no attributes** match this filter, so none are removed
- Bug exists in BOTH original and refactored code (junior dev copied the bug)

**Expected Behavior:**
Remove both `data-*` attributes AND `on*` event handlers

**Fix Required:**
Change to OR condition:
```java
var unwantedAttrs = el.attributes().asList().stream()
    .map(Attribute::getKey)
    .filter(key -> key.startsWith("data-") || key.startsWith("on"))  // Use OR
    .toList();
unwantedAttrs.forEach(el::removeAttr);
```

**Note:** This is a pre-existing bug that was copied during refactoring. Should be fixed as part of this PR.

---

### 3. ⚠️ **MISSING FUNCTIONALITY - Log Statement Removed**
**Severity:** MODERATE
**File:** `StructuredDataStrategy.java:42-43`
**Impact:** Loss of debugging information

**Problem:**
The original code logged the completeness score when structured data was found:
```java
// ORIGINAL:
log.debug("Found structured recipe data, completeness: {}%", completeness);
```

This helpful debug log is **missing** in the refactored `StructuredDataStrategy`.

**Why This Matters:**
- Debugging: Hard to troubleshoot why certain structured data is accepted/rejected
- Monitoring: No visibility into data quality in production logs
- Original author intentionally added this log for a reason

**Fix Required:**
Add back the log statement in `StructuredDataStrategy.java` after line 42:
```java
if (completeness >= config.getStructuredData().getMinCompleteness()) {
    log.debug("Found structured recipe data, completeness: {}%", completeness);  // Add this
    String jsonString = objectMapper.writeValueAsString(node);
    return Optional.of(jsonString);
}
```

**Additional Issue:**
The class needs `@Slf4j` annotation or a logger field:
```java
@Slf4j  // Add this to StructuredDataStrategy
@Component
public class StructuredDataStrategy implements CleanupStrategy {
```

---

### 4. ⚠️ **MISSING FUNCTIONALITY - Log Statement in Section Strategy**
**Severity:** MODERATE
**File:** `SectionBasedStrategy.java:41-43`
**Impact:** Loss of debugging information

**Problem:**
Original code logged section score and size:
```java
// ORIGINAL:
log.debug("Section-based extraction, score: {}, size: {} chars", bestScore, result.length());
```

Missing in `SectionBasedStrategy`.

**Fix Required:**
```java
if (bestSection != null && bestScore >= config.getSectionBased().getMinConfidence()) {
    HtmlCleaner.cleanElement(bestSection);
    String result = bestSection.html();
    if (result.length() >= config.getContentFilter().getMinOutputSize()) {
        log.debug("Section-based extraction, score: {}, size: {} chars", bestScore, result.length());  // Add this
        return Optional.of(result);
    }
}
```

Add `@Slf4j` to `SectionBasedStrategy`.

---

### 5. ⚠️ **MISSING FUNCTIONALITY - Log Statement in Content Filter**
**Severity:** MODERATE
**File:** `ContentFilterStrategy.java:33-34`
**Impact:** Loss of debugging information

**Problem:**
Original code logged cleaned size:
```java
// ORIGINAL:
log.debug("Content filtering applied, size: {} chars", cleaned.length());
```

Missing in `ContentFilterStrategy`.

**Fix Required:**
```java
String cleaned = doc.body().html();
log.debug("Content filtering applied, size: {} chars", cleaned.length());  // Add this
if (cleaned.length() >= config.getContentFilter().getMinOutputSize()) {
    return Optional.of(cleaned);
}
```

Add `@Slf4j` to `ContentFilterStrategy`.

---

## Moderate Issues (Should Fix)

### 6. ⚠️ **REMOVED FUNCTIONALITY - meetsConfidence Method Deleted**
**Severity:** LOW
**File:** Original `HtmlCleaner.java` (line 288-292)
**Impact:** Dead code removed (actually good), but worth noting

**Original Code:**
```java
private boolean meetsConfidence(String sectionHtml) {
    // Already checked in extractRecipeSections via bestScore comparison
    return true;
}
```

**Analysis:**
- This method always returned `true` (useless)
- Comment indicates it was redundant
- Junior dev correctly removed it during refactoring
- **This is actually correct** - the method served no purpose

**Action:** None needed. This is good cleanup.

---

### 7. ⚠️ **REMOVED FUNCTIONALITY - isComplete Method Deleted**
**Severity:** LOW
**File:** Original `HtmlCleaner.java` (line 203-208)
**Impact:** Unused method removed

**Original Code:**
```java
private boolean isComplete(String structuredData) {
    // For now, just check minimum safe size
    return structuredData != null &&
            structuredData.length() >= config.getFallback().getMinSafeSize();
}
```

**Analysis:**
- Method was **never called** in the original code
- Dead code that should be removed
- Junior dev correctly identified and removed it

**Action:** None needed. Good cleanup.

---

### 8. ℹ️ **VISIBILITY CHANGE - cleanElement Method**
**Severity:** LOW
**File:** `HtmlCleaner.java:103`
**Impact:** Changed from `private` to `public static`

**Problem:**
```java
// ORIGINAL:
private void cleanElement(Element element)

// REFACTORED:
public static void cleanElement(Element element)
```

**Why This Changed:**
- `SectionBasedStrategy` needs to call `cleanElement()`
- Made public static to allow access from strategy classes

**Analysis:**
- This is a reasonable design decision
- Alternative would be to duplicate the logic or use composition
- Making it static is fine since it has no instance dependencies
- Making it public is necessary for the strategy pattern

**Concerns:**
- Exposes internal helper method as public API
- Could be called by external code unexpectedly
- Increases coupling between HtmlCleaner and strategies

**Better Alternative:**
Create a package-private utility class or keep as package-private method:
```java
// Option 1: Package-private (remove 'public')
static void cleanElement(Element element)

// Option 2: Protected (if strategies might be in different packages)
protected static void cleanElement(Element element)
```

**Action:** Consider reducing visibility to package-private.

---

### 9. ℹ️ **VISIBILITY CHANGE - cleanupAttributes Method**
**Severity:** LOW
**File:** `HtmlCleaner.java:37`
**Impact:** Changed from `private static` to `public static`

**Problem:**
Same as #8 - made public to allow `ContentFilterStrategy` to call it.

**Fix Required:**
Same as #8 - reduce to package-private:
```java
static void cleanupAttributes(Element doc)  // Remove 'public'
```

---

## Architecture & Design Quality

### ✅ **GOOD: Strategy Pattern Implementation**

The core refactoring is well-executed:

1. **Clean Interface:**
   ```java
   public interface CleanupStrategy {
       Optional<String> clean(String html);
       HtmlCleaner.Strategy getStrategy();
   }
   ```
   - Simple, focused contract
   - Uses `Optional` appropriately
   - Returns strategy enum for metrics

2. **Separation of Concerns:**
   - Each strategy is self-contained
   - Dependencies are clearly defined via constructor injection
   - Spring `@Component` annotation enables auto-discovery

3. **Maintains Testability:**
   - Strategies can be tested independently (see `StructuredDataStrategyTest.java`)
   - HtmlCleaner can be tested with mock strategies
   - Configuration is still injectable

4. **Open/Closed Principle:**
   - Easy to add new strategies without modifying HtmlCleaner
   - Strategy order is controlled by Spring bean ordering or explicit list

---

### ✅ **GOOD: Dependency Injection**

The refactored `HtmlCleaner` correctly uses Spring DI:
```java
@Service
@RequiredArgsConstructor
public class HtmlCleaner {
    private final HtmlCleanupConfig config;
    private final MeterRegistry meterRegistry;
    private final java.util.List<CleanupStrategy> strategies;  // Auto-injected list
```

Spring will automatically inject all `@Component` beans implementing `CleanupStrategy` in order.

---

### ⚠️ **CONCERN: Strategy Ordering**

**Problem:**
The order of strategies is **critical** (should be: Structured Data → Section-Based → Content Filter), but the current code relies on Spring's auto-wiring order, which is **not guaranteed**.

**Current Test Code:**
```java
// In HtmlCleanerTest.java:72-76
var strategies = java.util.List.of(
    new StructuredDataStrategy(config, objectMapper),
    new SectionBasedStrategy(config),
    new ContentFilterStrategy(config)
);
```

Test manually creates ordered list, but production code relies on Spring auto-discovery.

**Risk:**
- If Spring changes bean ordering, strategies might execute in wrong order
- Could lead to degraded performance (less efficient strategies run first)
- Subtle bugs that only appear in production

**Fix Required:**
Use `@Order` annotation to guarantee ordering:
```java
@Order(1)
@Component
public class StructuredDataStrategy implements CleanupStrategy { ... }

@Order(2)
@Component
public class SectionBasedStrategy implements CleanupStrategy { ... }

@Order(3)
@Component
public class ContentFilterStrategy implements CleanupStrategy { ... }
```

---

## Code Quality Issues

### 10. ℹ️ **INCONSISTENT EXCEPTION HANDLING**

All strategy implementations use empty catch blocks:
```java
} catch (Exception ignored) {
}
```

**Analysis:**
- Matches original code style (original also swallowed exceptions)
- Allows graceful fallback to next strategy
- **BUT** loses valuable debugging information

**Original Code:**
```java
} catch (Exception e) {
    log.debug("Invalid JSON-LD in script tag, skipping", e);
}
```

The original code **did log** some exceptions! The refactored strategies silently swallow all errors.

**Fix Required:**
Add logging to all strategy catch blocks:
```java
// StructuredDataStrategy.java:51
} catch (Exception e) {
    log.debug("Invalid JSON-LD in script tag, skipping", e);
}

// StructuredDataStrategy.java:54
} catch (Exception e) {
    log.debug("Error parsing HTML for structured data", e);
}

// SectionBasedStrategy.java:47
} catch (Exception e) {
    log.debug("Error during section-based extraction", e);
}

// ContentFilterStrategy.java:37
} catch (Exception e) {
    log.debug("Error during content filtering", e);
}
```

---

### 11. ℹ️ **UNUSED IMPORTS**

**File:** `HtmlCleaner.java`

```java
import com.fasterxml.jackson.databind.JsonNode;  // ← Not used anymore
import com.fasterxml.jackson.databind.ObjectMapper;  // ← Not used anymore
import java.util.ArrayList;  // ← Not used anymore
import java.util.List;  // ← Not used anymore (except in FQN)
```

These were moved to `StructuredDataStrategy` but not removed from `HtmlCleaner`.

**Fix Required:**
Remove unused imports.

---

### 12. ℹ️ **MISSING DEPENDENCY**

**File:** `HtmlCleaner.java:35`

The code uses fully qualified name for the strategy list:
```java
private final java.util.List<net.shamansoft.cookbook.html.strategy.CleanupStrategy> strategies;
```

**Why:**
Avoiding import conflicts with `java.util.List` and keeping code working.

**Better Approach:**
Add proper import and use simple name:
```java
import net.shamansoft.cookbook.html.strategy.CleanupStrategy;
...
private final List<CleanupStrategy> strategies;
```

---

## Testing Gaps

### 13. ⚠️ **INCOMPLETE UNIT TEST COVERAGE**

Only `StructuredDataStrategyTest.java` exists with **1 test**.

**Missing Tests:**
- `SectionBasedStrategyTest.java` - 0 tests
- `ContentFilterStrategyTest.java` - 0 tests

**Risk:**
- Strategy implementations not independently verified
- Changes to strategies could break behavior without detection
- Integration tests in `HtmlCleanerTest` are not sufficient

**Fix Required:**
Create comprehensive unit tests for all strategies:
```
SectionBasedStrategyTest.java:
- shouldExtractSectionWithHighScore()
- shouldReturnEmptyWhenScoreTooLow()
- shouldReturnEmptyWhenOutputTooSmall()
- shouldScoreMultipleKeywords()
- shouldBonusForLists()
- shouldHandleEmptyHtml()

ContentFilterStrategyTest.java:
- shouldRemoveUnwantedElements()
- shouldRemoveHiddenElements()
- shouldCleanAttributes()
- shouldReturnEmptyWhenOutputTooSmall()
- shouldHandleEmptyHtml()
```

---

## Documentation

### 14. ℹ️ **MISSING JAVADOC**

All three strategy classes lack class-level Javadoc.

**Fix Required:**
```java
/**
 * Extracts JSON-LD schema.org Recipe structured data from HTML.
 * <p>
 * This strategy looks for {@code <script type="application/ld+json">} tags
 * containing Recipe objects. It validates completeness based on required
 * fields (name, ingredients, instructions) and returns the JSON if it
 * meets the minimum threshold.
 * <p>
 * This is the highest-priority strategy as structured data is the most
 * reliable source for recipe extraction.
 *
 * @see HtmlCleanupConfig.StructuredData
 */
@Component
@Slf4j
@Order(1)
public class StructuredDataStrategy implements CleanupStrategy {
```

Similar Javadoc needed for `SectionBasedStrategy` and `ContentFilterStrategy`.

---

## TODO

### Strategy Enum Location
- Move Strategy enum to html.strategy package.

### Add FallbackStrategy for consistency

**Problems with Current Approach:**

1. **Inconsistent** - Fallback is special-cased outside the strategy pattern
2. **Duplicated** - Two places return `Strategy.FALLBACK` (normal path + exception path)
3. **Not Testable** - Fallback behavior isn't independently testable as a strategy
4. **Violates Pattern** - Not all code paths go through strategies

**Benefits of FallbackStrategy:**

1. **Consistency** - All results come from strategies, no special cases
2. **Simplicity** - Main loop always exits via strategy return
3. **Testability** - Fallback behavior is independently testable
4. **Extensibility** - Could enhance fallback (e.g., basic HTML cleanup)
5. **Single Responsibility** - HtmlCleaner just orchestrates, doesn't implement fallback

**Proposed Implementation:**

```java
package net.shamansoft.cookbook.html.strategy;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fallback strategy that returns the original HTML unchanged.
 * <p>
 * This strategy always succeeds and should be ordered last in the
 * strategy chain. It ensures that HTML preprocessing never fails
 * completely - if all other strategies fail to extract content,
 * the original HTML is used.
 * <p>
 * This strategy cannot fail and will never return Optional.empty().
 */
@Component
@Slf4j
@Order(Integer.MAX_VALUE)  // Always execute last
public class FallbackStrategy implements CleanupStrategy {

    @Override
    public Optional<String> clean(String html) {
        log.debug("Using fallback strategy - returning original HTML unchanged");
        // Always succeeds, never throws, never returns empty
        return Optional.ofNullable(html);
    }

    @Override
    public HtmlCleaner.Strategy getStrategy() {
        return HtmlCleaner.Strategy.FALLBACK;
    }
}
```

**Update Strategy Orders:**

```java
@Order(1)  // Highest priority
@Component
public class StructuredDataStrategy implements CleanupStrategy { ... }

@Order(2)
@Component
public class SectionBasedStrategy implements CleanupStrategy { ... }

@Order(3)
@Component
public class ContentFilterStrategy implements CleanupStrategy { ... }

@Order(Integer.MAX_VALUE)  // Always last
@Component
public class FallbackStrategy implements CleanupStrategy { ... }
```

**Simplified HtmlCleaner.process():**

```java
public Results process(String html, String url) {
    int originalSize = html != null ? html.length() : 0;

    // Early validation
    if (html == null || html.isBlank()) {
        log.warn("Empty HTML input for URL: {}", url);
        return buildResult("", 0, Strategy.FALLBACK);
    }

    if (!config.isEnabled()) {
        return buildResult(html, originalSize, Strategy.DISABLED);
    }

    // Iterate strategies - FallbackStrategy is last and always succeeds
    for (var strategy : strategies) {
        try {
            Optional<String> out = strategy.clean(html);
            if (out.isPresent()) {
                var s = strategy.getStrategy();
                log.debug("Using {} strategy for URL: {}", s, url);
                return buildResult(out.get(), originalSize, s);
            }
        } catch (Exception e) {
            log.debug("Strategy {} failed, continuing to next",
                     strategy.getClass().getSimpleName(), e);
        }
    }

    // Should never reach here if FallbackStrategy is properly configured
    // This is a safety net for misconfiguration
    log.error("No strategies returned a result (FallbackStrategy missing?)");
    return buildResult(html, originalSize, Strategy.FALLBACK);
}
```

**Testing FallbackStrategy:**

```java
class FallbackStrategyTest {

    private FallbackStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FallbackStrategy();
    }

    @Test
    void shouldReturnOriginalHtml() {
        String html = "<html><body>test</body></html>";
        Optional<String> result = strategy.clean(html);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(html);
    }

    @Test
    void shouldHandleNullHtml() {
        Optional<String> result = strategy.clean(null);

        assertThat(result).isEmpty();  // ofNullable returns empty for null
    }

    @Test
    void shouldReturnFallbackStrategyType() {
        assertThat(strategy.getStrategy()).isEqualTo(HtmlCleaner.Strategy.FALLBACK);
    }

    @Test
    void shouldNeverThrowException() {
        assertThatCode(() -> {
            strategy.clean("");
            strategy.clean(null);
            strategy.clean("x".repeat(1_000_000));
        }).doesNotThrowAnyException();
    }
}
```

---

## Summary of Required Actions

### Critical (Must Fix Before Merge)
1. ✅ **Remove incorrect size check** in `StructuredDataStrategy.java:45`
2. ✅ **Fix filter logic bug** in `HtmlCleaner.cleanupAttributes()` (pre-existing bug)
3. ✅ **Add missing log statements** to all three strategies
4. ✅ **Add `@Slf4j`** to all three strategies
5. ✅ **Add `@Order` annotations** to guarantee strategy execution order

### Recommended (Should Fix)
6. ✅ **Implement FallbackStrategy** to eliminate special-case fallback logic
7. ✅ **Reduce visibility** of `cleanElement()` and `cleanupAttributes()` to package-private
8. ✅ **Add exception logging** to all catch blocks in strategies
9. ✅ **Remove unused imports** from `HtmlCleaner.java`
10. ✅ **Fix import** for `CleanupStrategy` in `HtmlCleaner.java`
11. ✅ **Strategy enum** Move to `html.strategy` package

### Nice to Have
12. ✅ **Add comprehensive unit tests** for all strategies (including FallbackStrategy)
13. ✅ **Add class-level Javadoc** to all strategy classes
14. ✅ **Expand `StructuredDataStrategyTest`** with more test cases

---

## Positive Feedback

Despite the issues, the refactoring demonstrates good understanding of:
- ✅ Strategy pattern principles
- ✅ Separation of concerns
- ✅ Spring dependency injection
- ✅ Test-driven development (started with `StructuredDataStrategyTest`)
- ✅ Clean code structure

The junior developer correctly identified:
- ✅ Dead code (`meetsConfidence`, `isComplete`) and removed it
- ✅ Need for interface abstraction
- ✅ Value of independent testability

---

## Conclusion

**Recommendation:** **REJECT** current implementation, request fixes for critical issues.

**Next Steps for Junior Developer:**

**Phase 1: Critical Fixes (2 hours)**
1. Fix the 4 failing tests by removing incorrect size check from `StructuredDataStrategy`
2. Fix the `cleanupAttributes` filter bug (change AND to OR)
3. Add back all missing log statements to all three strategies
4. Add `@Slf4j` annotations to all three strategies
5. Add `@Order` annotations for deterministic strategy ordering (1, 2, 3)
6. Run full test suite: `./gradlew :cookbook:test`
7. Verify all 19 tests pass ✅

**Phase 2: Design Improvements (1 hour)**
8. Implement `FallbackStrategy` with `@Order(Integer.MAX_VALUE)`
9. Update `HtmlCleaner.process()` to remove fallback special case
10. Add unit tests for `FallbackStrategy`
11. Reduce visibility of `cleanElement()` and `cleanupAttributes()` to package-private
12. Add exception logging to all strategy catch blocks
13. Clean up imports in `HtmlCleaner.java`
14. Run tests again to verify all still pass ✅

**Phase 3: Polish (optional, 1 hour)**
15. Add comprehensive unit tests for `SectionBasedStrategy` and `ContentFilterStrategy`
16. Add class-level Javadoc to all strategy classes
17. Final test run and code review

**Estimated Fix Time:** 2-4 hours (depending on scope)

**Review Again After:** Above critical fixes are applied and all tests pass.

---

## Design Recommendations Summary

Based on the analysis in "Design Questions & Recommendations" section above:

| Question | Answer | Rationale |
|----------|--------|-----------|
| **Keep Strategy enum?** | ✅ **YES** | Provides type safety, semantic naming for metrics/API, and clear contract. The coupling is acceptable for internal components. |
| **Implement FallbackStrategy?** | ✅ **YES** | Eliminates special-case logic, improves consistency, makes fallback behavior testable, and simplifies main processing loop. |

**Impact of Implementing FallbackStrategy:**

- **Code Removed:** Special-case fallback logic in `HtmlCleaner.process()`
- **Code Added:** ~50 lines for `FallbackStrategy.java` + ~40 lines for tests
- **Complexity:** Reduced (simpler main loop, no special cases)
- **Consistency:** Improved (all code paths use strategies)
- **Maintainability:** Improved (fallback logic is explicit and testable)

---

## Appendix: File Change Summary

### New Files (Current)
- `extractor/src/main/java/net/shamansoft/cookbook/html/strategy/CleanupStrategy.java` (12 lines)
- `extractor/src/main/java/net/shamansoft/cookbook/html/strategy/StructuredDataStrategy.java` (97 lines)
- `extractor/src/main/java/net/shamansoft/cookbook/html/strategy/SectionBasedStrategy.java` (69 lines)
- `extractor/src/main/java/net/shamansoft/cookbook/html/strategy/ContentFilterStrategy.java` (47 lines)
- `extractor/src/test/java/net/shamansoft/cookbook/html/strategy/StructuredDataStrategyTest.java` (42 lines)

### New Files (Recommended to Add)
- `extractor/src/main/java/net/shamansoft/cookbook/html/strategy/FallbackStrategy.java` (~50 lines)
- `extractor/src/test/java/net/shamansoft/cookbook/html/strategy/FallbackStrategyTest.java` (~40 lines)
- `extractor/src/test/java/net/shamansoft/cookbook/html/strategy/SectionBasedStrategyTest.java` (~100 lines)
- `extractor/src/test/java/net/shamansoft/cookbook/html/strategy/ContentFilterStrategyTest.java` (~100 lines)

### Modified Files
- `extractor/src/main/java/net/shamansoft/cookbook/html/HtmlCleaner.java` (182 lines, was 396 lines)
  - Removed: 214 lines of strategy implementation code
  - Added: Strategy list injection, delegation loop
  - Changed: Method visibility (cleanElement, cleanupAttributes)

- `extractor/src/test/java/net/shamansoft/cookbook/html/HtmlCleanerTest.java` (538 lines)
  - Modified setup to manually inject strategies in tests

### Lines of Code

**Current Refactoring:**
- **Before:** 396 lines (HtmlCleaner.java monolithic)
- **After:** 182 (HtmlCleaner) + 97 + 69 + 47 + 12 = **407 lines**
- **Net Change:** +11 lines (+2.8%)

**With Recommended FallbackStrategy:**
- **After:** 182 + 97 + 69 + 47 + 50 + 12 = **457 lines**
- **Net Change:** +61 lines (+15.4%)

**With All Recommended Tests:**
- **Production Code:** 457 lines
- **Test Code:** 42 + 40 + 100 + 100 = **282 lines** (tests)
- **Total:** **739 lines**
- **Test Coverage:** 282/457 = 61.7% test-to-code ratio

**Analysis:**
- Code is ~15% longer but significantly more maintainable
- Each strategy is independently testable
- Clear separation of concerns
- Easier to add new strategies or modify existing ones
- Test coverage is comprehensive
