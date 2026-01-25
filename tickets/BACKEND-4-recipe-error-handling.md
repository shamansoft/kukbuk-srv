# BACKEND-4: Update Recipe Endpoint with Storage Error Handling

**Priority:** P0 (Blocker)
**Estimated Time:** 2 hours
**Dependencies:** BACKEND-1, BACKEND-2, BACKEND-3
**Status:** Not Started

---

## Contents

- [x] Exception handlers for storage errors (completed in BACKEND-2)
- [ ] Update CookbookController to use StorageService instead of UserProfileService
- [ ] Remove X-Google-Token header fallback
- [ ] Add proper error handling for storage not configured (HTTP 428)
- [ ] Update storeToDrive() to use StorageService
- [ ] Add integration tests for error cases
- [ ] Remove old token-based logic

**Note:** BACKEND-2 already added exception handlers. We need to update the recipe endpoint to use `StorageService` (from BACKEND-1) instead of the old `UserProfileService` OAuth token flow.

---

## Objective

Update the `/recipe` endpoint to use the storage abstraction layer and properly handle cases where no storage is configured. Return clear, actionable errors that the extension can display to users.

---

## Context

Currently, the `/recipe` endpoint:
1. Gets OAuth token from X-Google-Token header (old way)
2. Calls DriveService directly
3. Returns generic errors

New behavior:
1. Uses StorageFactory to get user's storage provider
2. Handles "no storage configured" with HTTP 428
3. Returns clear, actionable error messages
4. No longer requires X-Google-Token header

---

## Tasks

### Task 4.1: Update CookbookController.createRecipe()

**File:** `src/main/java/net/shamansoft/cookbook/CookbookController.java`

Replace the existing `createRecipe()` method:

```java
@PostMapping(
        path = "/recipe",
        consumes = "application/json",
        produces = "application/json"
)
public RecipeResponse createRecipe(@RequestBody @Valid Request request,
                                   @RequestParam(value = "compression", required = false) String compression,
                                   @RequestAttribute("userId") String userId,
                                   @RequestAttribute("userEmail") String userEmail,
                                   @RequestHeader HttpHeaders httpHeaders)
        throws IOException {

    log.info("Creating recipe for user: {} ({})", userEmail, userId);
    log.info("Processing recipe request - URL: {}, Title: {}, Has HTML: {}, Compression: {}",
            request.url(),
            request.title(),
            request.html() != null && !request.html().isEmpty(),
            compression != null ? compression : "default");

    try {
        // Step 1: Get user's storage provider
        StorageService storage;
        try {
            storage = storageFactory.getDefaultProvider(userId);
            log.info("Using storage provider: {} for user: {}", storage.getProviderName(), userEmail);

        } catch (NoStorageConfiguredException e) {
            // User has no storage configured at all
            log.warn("No storage configured for user: {}", userEmail);
            throw new ResponseStatusException(
                HttpStatus.valueOf(428),  // 428 Precondition Required
                "No storage provider configured. Please connect Google Drive or another storage provider.",
                e
            );
        }

        // Step 2: Check if storage is actually configured
        if (!storage.isConfigured(userId)) {
            log.warn("Storage {} not configured for user: {}", storage.getProviderName(), userEmail);
            throw new ResponseStatusException(
                HttpStatus.valueOf(428),
                String.format("%s not configured. Please connect storage in settings.", storage.getDisplayName())
            );
        }

        // Step 3: Extract HTML content
        String html = extractHtml(request, compression);
        log.debug("Extracted HTML, size: {} bytes", html.length());

        // Step 4: Transform HTML to recipe using Gemini
        RecipeDto recipe = transformer.transform(html);
        log.info("Recipe transformed: {}", recipe.getTitle());

        // Step 5: Convert recipe to YAML
        String recipeYaml = recipeToYaml(recipe);
        log.debug("Recipe converted to YAML, size: {} bytes", recipeYaml.length());

        // Step 6: Save recipe using storage provider
        String fileUrl = storage.saveRecipe(userId, recipeYaml, recipe.getTitle(), request.url());
        log.info("Recipe saved successfully: {}", fileUrl);

        // Step 7: Optionally store in Firestore cache
        if (recipeStoreService.isEnabled()) {
            try {
                recipeStoreService.storeRecipe(userId, request.url(), recipe, recipeYaml);
                log.debug("Recipe stored in Firestore cache");
            } catch (Exception e) {
                // Non-critical - cache failure shouldn't fail the request
                log.warn("Failed to store recipe in cache: {}", e.getMessage());
            }
        }

        // Step 8: Build and return response
        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(recipe.getTitle())
                .ingredients(recipe.getIngredients())
                .instructions(recipe.getInstructions())
                .driveFileUrl(fileUrl)
                .message("Recipe saved successfully")
                .isRecipe(true);

        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            responseBuilder.imageUrl(recipe.getImageUrl());
        }

        if (recipe.getTotalTime() != null) {
            responseBuilder.totalTime(recipe.getTotalTime());
        }

        return responseBuilder.build();

    } catch (ResponseStatusException e) {
        // Re-throw response status exceptions (like 428)
        throw e;

    } catch (StorageNotConfiguredException e) {
        // Specific storage provider not configured
        log.warn("Storage {} not configured for user {}: {}",
                 e.getProvider(), userEmail, e.getMessage());
        throw new ResponseStatusException(
            HttpStatus.valueOf(428),
            e.getMessage(),
            e
        );

    } catch (Exception e) {
        // Generic error handling
        log.error("Failed to create recipe for user {}: {}", userEmail, e.getMessage(), e);
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to save recipe: " + e.getMessage(),
            e
        );
    }
}

/**
 * Convert RecipeDto to YAML string
 */
private String recipeToYaml(RecipeDto recipe) {
    // Use existing YAML serialization logic
    // This might already exist in your codebase
    try {
        return yamlMapper.writeValueAsString(recipe);
    } catch (Exception e) {
        log.error("Failed to convert recipe to YAML: {}", e.getMessage());
        throw new RuntimeException("YAML serialization failed", e);
    }
}
```

**Acceptance Criteria:**
- [ ] Uses StorageFactory instead of TokenService
- [ ] No reference to X-Google-Token header
- [ ] Throws HTTP 428 when storage not configured
- [ ] Clear error messages
- [ ] Proper logging at each step
- [ ] Non-critical errors (cache) don't fail request

---

### Task 4.2: Remove Old Token-Based Logic

**Clean up old code:**

```java
// DELETE these lines (if they exist):
// String googleOAuthToken = tokenService.getAuthToken(httpHeaders);

// DELETE fallback logic:
// try {
//     googleOAuthToken = userProfileService.getValidOAuthToken(userId);
// } catch (Exception e) {
//     googleOAuthToken = tokenService.getAuthToken(httpHeaders);
// }

// DELETE direct DriveService calls:
// driveService.saveRecipe(googleOAuthToken, ...);
```

**Add comment explaining change:**

```java
/**
 * Save recipe endpoint
 *
 * Authentication flow:
 * 1. Firebase ID token required in Authorization header (validates user)
 * 2. Backend retrieves storage OAuth tokens from Firestore (encrypted)
 * 3. Backend uses storage provider to save recipe
 *
 * No OAuth tokens in headers - all managed server-side!
 */
```

**Acceptance Criteria:**
- [ ] No references to X-Google-Token header
- [ ] No calls to `tokenService.getAuthToken()`
- [ ] No direct calls to `driveService` (use StorageService instead)
- [ ] Comment explaining new flow

---

### Task 4.3: Update Exception Handler

**File:** `src/main/java/net/shamansoft/cookbook/CookbookExceptionHandler.java`

Ensure proper exception handling:

```java
/**
 * Handle ResponseStatusException from controllers
 * This includes our HTTP 428 responses
 */
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<Map<String, Object>> handleResponseStatusException(
        ResponseStatusException ex) {

    log.warn("Response status exception: {} - {}", ex.getStatusCode(), ex.getReason());

    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getReason());
    body.put("status", ex.getStatusCode().value());
    body.put("timestamp", System.currentTimeMillis());

    // Add actionable error code for 428
    if (ex.getStatusCode().value() == 428) {
        body.put("errorCode", "STORAGE_NOT_CONFIGURED");
        body.put("action", "connect_storage");
    }

    return ResponseEntity
            .status(ex.getStatusCode())
            .body(body);
}
```

**Acceptance Criteria:**
- [ ] HTTP 428 responses include `errorCode` and `action`
- [ ] Error messages are clear and actionable
- [ ] Timestamp included in response

---

### Task 4.4: Add Integration Test

**File:** `src/test/java/net/shamansoft/cookbook/CookbookControllerIntegrationTest.java`

Add test for storage error handling:

```java
@Test
void createRecipe_noStorageConfigured_returns428() throws Exception {
    // Setup: Create user with no storage
    String userId = "test-user-no-storage";
    String firebaseToken = mockFirebaseToken(userId, "test@example.com");

    // Create user profile without storage
    userProfileService.getOrCreateProfile(userId, "test@example.com");

    // Attempt to save recipe
    mockMvc.perform(post("/recipe")
            .header("Authorization", "Bearer " + firebaseToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "url": "https://example.com/recipe",
                    "title": "Test Recipe",
                    "html": "<html><body>Recipe content</body></html>"
                }
                """))
            .andExpect(status().is(428))
            .andExpect(jsonPath("$.errorCode").value("STORAGE_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.action").value("connect_storage"))
            .andExpect(jsonPath("$.error").exists());
}

@Test
void createRecipe_withStorageConfigured_succeeds() throws Exception {
    // Setup: Create user with Google Drive configured
    String userId = "test-user-with-storage";
    String firebaseToken = mockFirebaseToken(userId, "test@example.com");

    // Create user profile
    userProfileService.getOrCreateProfile(userId, "test@example.com");

    // Connect Google Drive (mock)
    StorageService googleDrive = storageFactory.getProvider("google-drive");
    googleDrive.connect(userId, "mock-access-token", "mock-refresh-token", 3600);

    // Mock DriveService to return success
    when(driveService.saveRecipe(any(), any(), any(), any()))
        .thenReturn("https://drive.google.com/file/123");

    // Save recipe
    mockMvc.perform(post("/recipe")
            .header("Authorization", "Bearer " + firebaseToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "url": "https://example.com/recipe",
                    "title": "Test Recipe",
                    "html": "<html><body>Recipe content</body></html>"
                }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driveFileUrl").exists())
            .andExpect(jsonPath("$.title").value("Test Recipe"));
}
```

**Acceptance Criteria:**
- [ ] Test for no storage returns 428
- [ ] Test for configured storage succeeds
- [ ] Tests verify error codes and messages

---

## Error Response Examples

### No Storage Configured (HTTP 428)

```json
{
  "error": "No storage provider configured. Please connect Google Drive or another storage provider.",
  "errorCode": "STORAGE_NOT_CONFIGURED",
  "action": "connect_storage",
  "status": 428,
  "timestamp": 1705329600000
}
```

### Specific Provider Not Configured (HTTP 428)

```json
{
  "error": "Google Drive not configured. Please connect storage in settings.",
  "errorCode": "STORAGE_NOT_CONFIGURED",
  "action": "connect_storage",
  "status": 428,
  "timestamp": 1705329600000
}
```

### Success Response (HTTP 200)

```json
{
  "title": "Stuffed Lamb with Spinach and Pine Nuts",
  "ingredients": [...],
  "instructions": [...],
  "driveFileUrl": "https://drive.google.com/file/d/abc123/view",
  "message": "Recipe saved successfully",
  "isRecipe": true,
  "imageUrl": "https://example.com/image.jpg",
  "totalTime": "1h 30m"
}
```

---

## Manual Testing

```bash
# 1. Test without storage
FIREBASE_TOKEN="your-token"

curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/recipe",
    "title": "Test Recipe",
    "html": "<html><body>Test</body></html>"
  }'

# Expected: HTTP 428 with errorCode: STORAGE_NOT_CONFIGURED

# 2. Connect storage
curl -X POST http://localhost:8080/api/storage/google-drive/connect \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "ya29...",
    "refreshToken": "1//...",
    "expiresIn": 3600
  }'

# 3. Try again
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.gordonramsay.com/gr/recipes/stuffed-lamb-with-spinach-and-pine-nuts/",
    "title": "Stuffed Lamb",
    "html": "<html><body>Recipe...</body></html>"
  }'

# Expected: HTTP 200 with driveFileUrl
```

---

## Acceptance Criteria Summary

- [ ] Recipe endpoint uses StorageFactory
- [ ] No X-Google-Token header required
- [ ] HTTP 428 returned when storage not configured
- [ ] Clear, actionable error messages
- [ ] Error responses include errorCode and action
- [ ] Success response includes driveFileUrl
- [ ] Integration tests passing
- [ ] Manual testing successful
- [ ] Old token logic removed
- [ ] Comments explain new flow

---

## Dependencies

**Required:**
- BACKEND-1 completed (StorageFactory available)
- BACKEND-2 completed (Storage endpoints work)
- BACKEND-3 completed (User profile schema updated)

**New:**
- None

---

## Breaking Changes

⚠️ **This is a breaking change for the extension!**

The extension must be updated to:
1. NOT send X-Google-Token header
2. Handle HTTP 428 responses
3. Prompt user to connect storage when 428 received

See extension tickets EXT-1 through EXT-5 for coordinated changes.

---

## Migration Notes

**For Development:**
- Old endpoint behavior removed
- Extension must be updated simultaneously
- No backward compatibility needed (no production users)

**If there were production users:**
- Could support both X-Google-Token AND storage provider
- Deprecate X-Google-Token over time
- Gradual migration with dual support

---

## Next Steps

After completing this ticket:
1. Test all error cases manually
2. Verify integration tests pass
3. **Coordinate with extension team** - they need to update!
4. Move to extension tickets (EXT-1 through EXT-5)
5. Test end-to-end flow: auth → connect storage → save recipe

---

## Success Metrics

✅ Recipe saves work when storage configured
✅ Clear errors when storage not configured
✅ No more X-Google-Token header needed
✅ Extension can display helpful error messages
✅ Backend logs clearly show what's happening
