# Multi-Recipe Feature - Implementation Notes

## Cache Redesign: YAML → JSON (2026-02-17)

**Problem**: Multi-recipe support required caching multiple recipes per page, but cache stored YAML strings.

**Solution**: Redesigned cache to store JSON instead of YAML for better performance and multi-recipe support.

### Changes Made

#### 1. Cache Schema Change
- **`StoredRecipe.recipeYaml`** (String) → **`StoredRecipe.recipesJson`** (String)
- JSON format: `[{...recipe1...}, {...recipe2...}]` (array of Recipe objects)
- Uses Jackson `ObjectMapper` for serialization/deserialization

#### 2. `RecipeStoreService` API Redesign
```java
// New typed API
public record CachedRecipes(boolean valid, List<Recipe> recipes) {
    public static CachedRecipes valid(List<Recipe> recipes);
    public static CachedRecipes invalid();
}

// New methods
Optional<CachedRecipes> findCachedRecipes(String contentHash)
void storeValidRecipes(String contentHash, String url, List<Recipe> recipes)
void storeInvalidRecipe(String contentHash, String url)

// Internal serialization
private String serializeRecipes(List<Recipe> recipes)  // → JSON
private List<Recipe> deserializeRecipes(String json)   // ← JSON
```

#### 3. Test Fixes

**Unit Tests**:
- `RecipeServiceCreateRecipeTest`: Updated all cache method calls to new API
- `RecipeServicePerformanceTest`: Added `ObjectMapper` to constructor, changed field names
- `FirestoreRecipeRepositorySimpleTest`: Changed `.recipeYaml()` → `.recipesJson()`
- `RecipeControllerSBTest`: Updated cache method calls

**Integration Tests**:
- `FirestoreRecipeRepositoryIntegrationTest`: Replaced all 7 occurrences of `.recipeYaml(...)` → `.recipesJson("[]")`
- `AddRecipeIT`:
  - Cached recipe test: Changed from YAML string to JSON array format
  - Post-processing test: Updated assertions from YAML format to JSON format

#### 4. `GeminiExtractionResult` Fixes

**Problem**: Jackson 3.x throws `MismatchedInputException: Cannot map null into type double` when `recipe_confidence` field is missing.

**Solution**:
```java
@JsonProperty("recipe_confidence") Double recipeConfidence,  // was: double

public GeminiExtractionResult {
    if (recipeConfidence == null) {
        recipeConfidence = 0.0;  // Default for missing field
    }
    if (recipes == null) {
        recipes = List.of();
    }
}
```

#### 5. WireMock Mock Updates

Updated `AddRecipeIT` mocks to use new `GeminiExtractionResult` format:

**Old format** (single recipe as root):
```json
{"is_recipe": true, "metadata": {...}, "ingredients": [...], "instructions": [...]}
```

**New format** (wrapper with recipes array):
```json
{
  "is_recipe": true,
  "recipe_confidence": 0.95,
  "recipes": [{
    "is_recipe": true,
    "schema_version": "1.0.0",
    "recipe_version": "1.0.0",
    "metadata": {...},
    "ingredients": [...],
    "instructions": [...]
  }]
}
```

**Not-a-recipe format**:
```json
{"is_recipe": false, "recipe_confidence": 0.3, "recipes": []}
```

### Migration Notes

- **Backward compatibility**: Old Firestore documents with `recipeYaml` field return `null` for `getRecipesJson()` → treated as cache miss → fresh extraction
- **No migration needed**: Old cached recipes automatically re-extracted on first access
- **Performance**: JSON serialization/deserialization is faster than YAML

### Files Modified

**Core**:
- `StoredRecipe.java` - Field rename
- `Transformer.java` (Firestore) - Field access
- `RecipeStoreService.java` - Full redesign
- `RecipeService.java` - Updated cache API calls
- `DebugController.java` - Updated cache API calls
- `GeminiExtractionResult.java` - Nullable `Double` with default

**Tests**:
- `RecipeServiceCreateRecipeTest.java`
- `RecipeServicePerformanceTest.java`
- `FirestoreRecipeRepositorySimpleTest.java`
- `RecipeControllerSBTest.java`
- `FirestoreRecipeRepositoryIntegrationTest.java`
- `AddRecipeIT.java`

### Test Results

✅ All unit tests pass
✅ All integration tests pass
✅ Cache serialization/deserialization working correctly
✅ Multi-recipe support ready for use
