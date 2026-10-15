# Integration Test Failure Analysis

**Date:** 2026-01-24
**Test:** `AddRecipeIT.shouldCreateRecipeWithStorageConfigured()`
**Status:** ❌ FAILED
**Branch:** `parse-html`

---

## Summary

The integration test `shouldCreateRecipeWithStorageConfigured()` is failing because **recipes cached in Firestore from previous tests are not being cleaned up** between test runs. This causes the test to skip Gemini API calls (cache hit), which violates the test's expectation that Gemini should be called.

---

## Test Failure Details

### Error Message
```
com.github.tomakehurst.wiremock.client.VerificationException:
No requests exactly matched. Most similar request was:
  expected:<POST [path regex] /models/gemini-2.5-flash-lite:generateContent.*>
  but was:<POST /files?fields=id>
```

**What it means:**
- Test expects WireMock to have recorded a POST to Gemini API
- WireMock has NO record of any Gemini API call
- The closest match WireMock found was a POST to `/files` (Google Drive API)

### WireMock Recorded Requests
```
WireMock Request [0]: PATCH /files/file-456?uploadType=media&fields=id
WireMock Request [1]: POST /files?fields=id
WireMock Request [2]: GET /files?q=name='chocolate-chip-cookies.yaml'...
```

**Notice:** No Gemini API request was made!

---

## Root Cause Analysis

### 1. Recipe Caching Behavior

The application caches recipes in Firestore to avoid re-processing the same URL:

**Cache Key:** Content hash (SHA-256 of URL + HTML)
**Cache Location:** `users/{userId}/recipes/{recipeId}/`

**Flow:**
```
1. User submits recipe URL + HTML
2. System generates content hash
3. Check Firestore: Does recipe with this hash exist?
   - YES → Return cached recipe (skip Gemini call) ✅
   - NO  → Call Gemini API, validate, cache result
```

### 2. Test Execution Order

Integration tests run in this order (alphabetically by method name):

```
1. shouldHandleNotARecipeContent()          - Different URL, different cache
2. shouldPreprocessHtmlBeforeTransform()    - Uses "https://example.com/cookies" ⚠️
3. shouldReturn428WhenStorageNotConfigured()- Different scenario
4. shouldUseCachedRecipeWhenAvailable()     - Explicitly tests caching
5. shouldCreateRecipeWithStorageConfigured() - Uses "https://example.com/cookies" ❌ CACHE HIT!
```

### 3. The Problem

**Test #2** (`shouldPreprocessHtmlBeforeTransform()`):
- URL: `https://example.com/cookies`
- Calls Gemini API ✅
- Saves recipe to Firestore cache

**Test #5** (`shouldCreateRecipeWithStorageConfigured()`):
- URL: `https://example.com/cookies` (same!)
- Checks Firestore cache → **FOUND!**
- Skips Gemini API call (cache hit)
- Goes straight to Google Drive upload
- Test verification fails: `verify(postRequestedFor(...Gemini...))`

### 4. Why `clearFirestore()` Doesn't Help

**Current implementation** (AddRecipeIT.java:164-171):
```java
private void clearFirestore() throws Exception {
    // Delete the test user document if it exists
    try {
        firestore.collection("users").document("test-user-123").delete().get();
    } catch (Exception e) {
        // Document might not exist, that's OK
    }
}
```

**Problem:**
- Only deletes the user document: `users/test-user-123`
- Does NOT delete subcollections: `users/test-user-123/recipes/*`
- **Firestore subcollections are NOT automatically deleted** when parent is deleted!
- Cached recipes persist across tests 🐛

---

## Evidence from Logs

### Test #2 (shouldPreprocessHtmlBeforeTransform) - First execution
```
[http-nio-auto-1-exec-1] INFO ... - Creating recipe for user: testuser@example.com
[http-nio-auto-1-exec-1] INFO ... - Processing recipe request - URL: https://example.com/cookies
[http-nio-auto-1-exec-1] INFO ... - HTML preprocessing - URL: ..., Strategy: FALLBACK, 1287 → 1287 chars
[http-nio-auto-1-exec-1] INFO ... - Calling Gemini API - Model: gemini-2.5-flash-lite
[http-nio-auto-1-exec-1] INFO ... - Gemini response has 1 part(s)
```
✅ Gemini was called (cache miss)

### Test #5 (shouldCreateRecipeWithStorageConfigured) - Later execution
```
[http-nio-auto-1-exec-3] INFO ... - Creating recipe for user: testuser@example.com
[http-nio-auto-1-exec-3] INFO ... - Processing recipe request - URL: https://example.com/cookies
[http-nio-auto-1-exec-3] INFO ... - File Item[id=file-456, name=chocolate-chip-cookies.yaml] created
```
❌ No Gemini logs! (cache hit)

---

## Why This Wasn't Caught Before

The refactoring **did not change caching logic**, so why is this failing now?

**Two possibilities:**

### Hypothesis 1: Test Execution Order Changed
- The test might have run in a different order before
- JUnit 5 test order is not guaranteed unless specified
- Alphabetical ordering is platform/JDK dependent

### Hypothesis 2: Recent Test Additions/Changes
- Check git history for recent changes to this test file
- New tests might have been added that use the same URL

### Hypothesis 3: Existing Bug, Just Surfaced
- This bug may have existed before
- The refactoring might have changed timing/ordering just enough to expose it
- Previous test runs might have been "lucky" with ordering

---

## Git Diff Analysis

Checking what changed in `AddRecipeIT.java`:

```bash
git diff HEAD~1 extractor/src/intTest/java/net/shamansoft/cookbook/AddRecipeIT.java
```

### Finding: NEW TEST ADDED

A new test was added: `shouldPreprocessHtmlBeforeTransform()`

**Location:** Line 545+ (new)
**Purpose:** Test HTML preprocessing to verify token reduction
**URL used:** `"https://example.com/cookies"` ⚠️

**This is the same URL as the existing test `shouldCreateRecipeWithStorageConfigured()`!**

### Test Execution Flow

```
Test 1: shouldHandleNotARecipeContent()
   └─> Different URL, no conflict

Test 2: shouldPreprocessHtmlBeforeTransform() [NEW]
   └─> URL: https://example.com/cookies
   └─> Calls Gemini API ✅
   └─> Saves to Firestore cache ⚠️

Test 3: shouldReturn428WhenStorageNotConfigured()
   └─> Different scenario

Test 4: shouldUseCachedRecipeWhenAvailable()
   └─> Different URL

Test 5: shouldCreateRecipeWithStorageConfigured() [EXISTING]
   └─> URL: https://example.com/cookies
   └─> Finds cached recipe from Test 2 🐛
   └─> Skips Gemini call
   └─> Test fails verification ❌
```

---

## Root Cause Confirmed

✅ **The refactoring itself didn't break anything**
✅ **The new test `shouldPreprocessHtmlBeforeTransform()` was added**
✅ **Two tests now use the same URL without cache cleanup**
❌ **`clearFirestore()` doesn't delete recipe subcollections**

---

## Solution Options

### Option 1: Fix clearFirestore() to Delete Subcollections ✅ RECOMMENDED

**Change:**
```java
private void clearFirestore() throws Exception {
    // Delete the test user document AND all subcollections
    try {
        // Delete all recipes in the subcollection first
        firestore.collection("users")
                .document("test-user-123")
                .collection("recipes")
                .listDocuments()
                .forEach(docRef -> {
                    try {
                        docRef.delete().get();
                    } catch (Exception e) {
                        // Ignore
                    }
                });

        // Then delete the user document
        firestore.collection("users").document("test-user-123").delete().get();
    } catch (Exception e) {
        // Document might not exist, that's OK
    }
}
```

**Pros:**
- ✅ Ensures complete isolation between tests
- ✅ Matches test expectations (each test starts fresh)
- ✅ Prevents future similar issues
- ✅ No test logic changes needed

**Cons:**
- ⚠️ Slightly slower (extra Firestore operations)
- ⚠️ Need to recursively delete all subcollections

---

### Option 2: Use Different URLs for Each Test

**Change:**
```java
// In shouldPreprocessHtmlBeforeTransform()
Request request = new Request(largeHtml, "Cookie Recipe",
    "https://example.com/cookies-preprocessed");  // Different URL

// In shouldCreateRecipeWithStorageConfigured()
Request request = new Request(sampleHtml, "Chocolate Chip Cookies",
    "https://example.com/cookies");  // Keep original
```

**Pros:**
- ✅ Quick fix
- ✅ Tests are more independent

**Cons:**
- ❌ Doesn't fix the root cause (cache not being cleared)
- ❌ Other tests might have same issue
- ❌ Fragile - easy to accidentally reuse URLs

---

### Option 3: Order Tests with @TestMethodOrder

**Change:**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddRecipeIT {

    @Test
    @Order(1)
    void shouldCreateRecipeWithStorageConfigured() { ... }

    @Test
    @Order(2)
    void shouldPreprocessHtmlBeforeTransform() { ... }
}
```

**Pros:**
- ✅ Explicit test ordering
- ✅ Deterministic execution

**Cons:**
- ❌ Doesn't fix the root cause
- ❌ Tests become order-dependent (bad practice)
- ❌ Hidden coupling between tests

---

## Recommended Fix

**Use Option 1** - Fix `clearFirestore()` to properly delete subcollections

### Implementation

**File:** `extractor/src/intTest/java/net/shamansoft/cookbook/AddRecipeIT.java`
**Line:** 164-171

**Current code:**
```java
private void clearFirestore() throws Exception {
    // Delete the test user document if it exists
    try {
        firestore.collection("users").document("test-user-123").delete().get();
    } catch (Exception e) {
        // Document might not exist, that's OK
    }
}
```

**Fixed code:**
```java
private void clearFirestore() throws Exception {
    // Delete the test user document AND all subcollections
    String userId = "test-user-123";

    try {
        // First, delete all documents in the recipes subcollection
        var recipesCollection = firestore.collection("users")
                .document(userId)
                .collection("recipes");

        // Get all recipe documents and delete them
        var recipeDocs = recipesCollection.listDocuments();
        for (var recipeDoc : recipeDocs) {
            try {
                recipeDoc.delete().get();
            } catch (Exception e) {
                // Ignore individual delete failures
            }
        }

        // Then delete the user document itself
        firestore.collection("users").document(userId).delete().get();
    } catch (Exception e) {
        // Document might not exist, that's OK
    }
}
```

**Why this works:**
- Deletes all cached recipes before each test
- Ensures Gemini API is called when expected
- Makes tests truly independent
- Prevents future cache-related test failures

---

## Testing the Fix

### Before Fix
```bash
./gradlew :cookbook:intTest
```
**Result:** 30/31 tests pass, `shouldCreateRecipeWithStorageConfigured()` fails

### After Fix
```bash
./gradlew :cookbook:intTest
```
**Expected Result:** 31/31 tests pass ✅

### Verification Steps

1. Apply the fix to `clearFirestore()` method
2. Run integration tests: `./gradlew :cookbook:intTest`
3. Check test output:
   - All 31 tests should pass
   - WireMock should record Gemini API calls in both tests
4. Check logs for both tests:
   - `shouldPreprocessHtmlBeforeTransform()` → "Calling Gemini API" ✅
   - `shouldCreateRecipeWithStorageConfigured()` → "Calling Gemini API" ✅

---

## Additional Recommendations

### 1. Add Clear Logging in Tests

Help debug future cache issues:

```java
@BeforeEach
void setUp() throws Exception {
    WireMock.configureFor("localhost", wiremockContainer.getMappedPort(8080));
    WireMock.reset();

    // Clear Firestore data between tests
    log.info("Clearing Firestore before test");
    clearFirestore();
    log.info("Firestore cleared successfully");

    setupGeminiMock();
    setupGoogleDriveMocks();
}
```

### 2. Add Test for Cache Cleanup

Ensure `clearFirestore()` actually works:

```java
@Test
@DisplayName("Should properly clear Firestore cache between tests")
void shouldClearFirestoreCache() throws Exception {
    // Given: Recipe is cached
    setupStorageInfoInFirestore("test-user-123", "valid-drive-token");
    String testUrl = "https://example.com/test-cache-clear";
    String contentHash = contentHashService.generateContentHash(testUrl);

    StoredRecipe cached = StoredRecipe.builder()
            .contentHash(contentHash)
            .sourceUrl(testUrl)
            .recipeYaml("test yaml")
            .isValid(true)
            .createdAt(Instant.now())
            .lastUpdatedAt(Instant.now())
            .userId("test-user-123")
            .build();

    recipeRepository.save(cached).join();

    // Verify it exists
    Optional<StoredRecipe> found = recipeRepository.findByContentHash(contentHash).join();
    assertThat(found).isPresent();

    // When: Clear Firestore
    clearFirestore();

    // Then: Recipe should be gone
    found = recipeRepository.findByContentHash(contentHash).join();
    assertThat(found).isEmpty();
}
```

### 3. Consider Helper Method for Unique URLs

Prevent URL collisions:

```java
private String uniqueUrl(String testName) {
    return "https://example.com/" + testName + "-" + System.currentTimeMillis();
}

// Usage:
Request request = new Request(html, "Title", uniqueUrl("shouldCreateRecipe"));
```

---

## Summary

| Issue | Root Cause | Fix | Priority |
|-------|-----------|-----|----------|
| Integration test failing | Cache not cleared between tests | Fix `clearFirestore()` to delete subcollections | 🔴 HIGH |
| Two tests use same URL | New test added with same URL | Side effect of incomplete cache cleanup | ℹ️ INFO |
| Future test brittleness | No cache cleanup verification | Add test for cache clearing | 🟡 MEDIUM |

**Next Steps:**
1. ✅ Apply the fix to `clearFirestore()` method
2. ✅ Run integration tests to verify
3. ✅ Commit the fix with clear message
4. ✅ Update review documentation

---

---

## Why Wasn't This Failing Before? 🔍

### Answer: **IT WAS FAILING BEFORE!**

I checked out the original commit (aba2597) where HtmlCleaner was first introduced and ran the integration tests:

```bash
$ git checkout aba2597
$ ./gradlew :cookbook:intTest --tests "AddRecipeIT"

Result:
AddRecipeIT > Should successfully create recipe when storage is configured FAILED
BUILD FAILED
```

**The test has been failing since HtmlCleaner was originally added!**

### Why Did You Think It Was Passing?

Possible reasons:
1. **Integration tests not run locally** - Only unit tests were run (`./gradlew :cookbook:test`)
2. **CI/CD doesn't run intTest** - Check if `.github/workflows/*.yml` includes `intTest`
3. **Test failure was ignored** - Maybe there was a known issue
4. **Different environment** - The test might pass/fail inconsistently based on test execution order

### Verification

Check if CI runs integration tests:
```bash
$ grep -r "intTest" .github/workflows/
```

If no match, integration tests are not part of the CI pipeline, which would explain why this wasn't caught!

---

**Analysis by:** Claude Code
**Date:** 2026-01-24
**Status:** Ready for fix

**Additional Finding:** The refactoring DID NOT introduce this bug - it was pre-existing!
