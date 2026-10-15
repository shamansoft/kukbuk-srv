# Why The Test Wasn't Failing Before Refactoring

## Investigation Summary

After deep analysis, here's what I found:

### Key Facts

1. **Both tests existed in original commit** (aba2597)
   - `shouldCreateRecipeWithStorageConfigured()` - Line 274
   - `shouldPreprocessHtmlBeforeTransform()` - Line 498

2. **Both used the same URL**: `https://example.com/cookies`

3. **Cache cleanup bug existed from the start**: `clearFirestore()` never deleted recipe subcollections

4. **Test order in original file**:
   ```
   Line 274: shouldCreateRecipeWithStorageConfigured()
   Line 342: shouldReturn428WhenStorageNotConfigured()
   Line 385: shouldUseCachedRecipeWhenAvailable()
   Line 452: shouldHandleNotARecipeContent()
   Line 498: shouldPreprocessHtmlBeforeTransform()
   ```

## Critical Question

If the cache bug existed from the start, why did tests pass in commit aba2597 but fail now?

### Hypothesis 1: Test Execution Order Changed ✅ MOST LIKELY

**JUnit 5 Test Ordering:**
- Default: Uses `MethodOrderer.MethodName` (alphabetical by method name)
- When refactoring happens, class is recompiled, potentially changing method order
- File modifications can affect classpath ordering

**Alphabetical order of test names:**
```
1. shouldCreateRecipeWithStorageConfigured()
2. shouldHandleNotARecipeContent()
3. shouldPreprocessHtmlBeforeTransform()  ← This comes AFTER in alpha order
4. shouldReturn428WhenStorageNotConfigured()
5. shouldUseCachedRecipeWhenAvailable()
```

**WAIT!** Let me re-check this carefully...

Actually "shouldPreprocessHtmlBeforeTransform" comes alphabetically AFTER "shouldCreateRecipeWithStorageConfigured", so:

- Test 1: `shouldCreateRecipeWithStorageConfigured()` runs → Calls Gemini → Caches recipe
- Test 2: ...other tests...
- Test 3: `shouldPreprocessHtmlBeforeTransform()` runs → **CACHE HIT!** → No Gemini call

So if alphabetical ordering was used, the tests should have ALWAYS failed! Unless...

### Hypothesis 2: Test Declaration Order Was Used Initially

In the **original commit**, tests were declared in this source file order:
1. shouldCreateRecipeWithStorageConfigured (line 274)
2. shouldReturn428WhenStorageNotConfigured (line 342)
3. shouldUseCachedRecipeWhenAvailable (line 385)
4. shouldHandleNotARecipeContent (line 452)
5. shouldPreprocessHtmlBeforeTransform (line 498) ← LAST

Some JUnit configurations use **declaration order** (order in source file) instead of alphabetical.

If that was the case:
- `shouldPreprocessHtmlBeforeTransform` would run LAST
- But it would still hit the cache from test 1!

This STILL doesn't explain why it passed! Unless...

### Hypothesis 3: Test Didn't Verify Gemini Was Called Originally ✅ CHECKING

Let me check what the original test verified...
