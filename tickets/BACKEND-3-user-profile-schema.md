# BACKEND-3: Update User Profile Schema

**Priority:** P0 (Blocker)
**Estimated Time:** 2 hours
**Dependencies:** BACKEND-1 (Storage Abstraction)
**Status:** Partially Complete (Schema exists, needs migration)

---

## Contents

- [x] Storage schema implemented (BACKEND-1 created `storage` field with StorageService)
- [ ] Migrate OAuth tokens from UserProfileService to StorageService
- [ ] Deprecate old OAuth token methods in UserProfileService
- [ ] Update endpoints to use new storage flow
- [ ] Add storage status to user profile endpoint
- [ ] Add tests for migration

**Note:** BACKEND-1 already implemented a `storage` schema (simpler than the `storageIntegrations` proposed here). We should adapt this ticket to work with the existing `storage` field rather than creating a new schema.

---

## Objective

Update the Firestore user profile schema to support multiple storage integrations and user preferences. Move OAuth token storage from the root level to `storageIntegrations.googleDrive` to enable multi-provider support.

---

## Context

Current schema stores OAuth tokens at the root level:
```javascript
users/{userId} {
  googleOAuthToken: "encrypted...",
  googleRefreshToken: "encrypted...",
  tokenExpiresAt: timestamp
}
```

New schema organizes storage providers separately:
```javascript
users/{userId} {
  storageIntegrations: {
    googleDrive: {
      connected: true,
      accessToken: "encrypted...",
      refreshToken: "encrypted...",
      expiresAt: timestamp,
      connectedAt: timestamp
    },
    dropbox: null  // Not connected yet
  },
  preferences: {
    defaultStorage: "google-drive"
  }
}
```

---

## Tasks

### Task 3.1: Update UserProfileService - Initialize New Schema

**File:** `src/main/java/net/shamansoft/cookbook/service/UserProfileService.java`

Update the `getOrCreateProfile()` method:

```java
/**
 * Get or create user profile with new schema
 *
 * @param userId Firebase UID
 * @param email User email
 * @return User profile data
 */
public Map<String, Object> getOrCreateProfile(String userId, String email)
        throws ExecutionException, InterruptedException {

    DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);
    DocumentSnapshot doc = docRef.get().get();

    if (doc.exists()) {
        log.debug("User profile exists for: {}", userId);

        // Check if profile needs migration (has old schema)
        if (needsSchemaMigration(doc)) {
            log.info("Migrating user profile schema for: {}", userId);
            migrateProfileSchema(userId, doc);
            // Re-fetch updated profile
            doc = docRef.get().get();
        }

        return doc.getData();

    } else {
        log.info("Creating new user profile for: {}", userId);

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", userId);
        profile.put("email", email);
        profile.put("authProvider", "google");  // Can be "email", "github", etc.
        profile.put("createdAt", Timestamp.now());
        profile.put("updatedAt", Timestamp.now());

        // Initialize empty storage integrations
        profile.put("storageIntegrations", new HashMap<String, Object>());

        // Initialize preferences
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("defaultStorage", null);  // No default yet
        preferences.put("autoSave", true);
        profile.put("preferences", preferences);

        docRef.set(profile).get();
        log.info("Created new profile for user: {}", userId);

        return profile;
    }
}

/**
 * Check if profile needs migration from old schema
 */
private boolean needsSchemaMigration(DocumentSnapshot doc) {
    // Old schema has googleOAuthToken at root level
    return doc.contains("googleOAuthToken") &&
           !doc.contains("storageIntegrations.googleDrive");
}

/**
 * Migrate profile from old schema to new schema
 * This handles backward compatibility for dev environment
 */
private void migrateProfileSchema(String userId, DocumentSnapshot doc)
        throws ExecutionException, InterruptedException {

    log.info("Migrating profile schema for user: {}", userId);

    Map<String, Object> updates = new HashMap<>();

    // Move old OAuth tokens to storageIntegrations.googleDrive
    if (doc.contains("googleOAuthToken")) {
        Map<String, Object> googleDrive = new HashMap<>();
        googleDrive.put("connected", true);
        googleDrive.put("accessToken", doc.get("googleOAuthToken"));

        if (doc.contains("googleRefreshToken")) {
            googleDrive.put("refreshToken", doc.get("googleRefreshToken"));
        }

        if (doc.contains("tokenExpiresAt")) {
            googleDrive.put("expiresAt", doc.get("tokenExpiresAt"));
        }

        googleDrive.put("connectedAt", Timestamp.now());
        googleDrive.put("provider", "google-drive");

        updates.put("storageIntegrations.googleDrive", googleDrive);

        // Delete old fields
        updates.put("googleOAuthToken", FieldValue.delete());
        updates.put("googleRefreshToken", FieldValue.delete());
        updates.put("tokenExpiresAt", FieldValue.delete());
    }

    // Initialize preferences if not exists
    if (!doc.contains("preferences")) {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("defaultStorage", "google-drive");  // Since they had Drive
        preferences.put("autoSave", true);
        updates.put("preferences", preferences);
    }

    // Update schema version (optional, for tracking)
    updates.put("schemaVersion", 2);
    updates.put("updatedAt", Timestamp.now());

    // Apply updates
    firestore.collection(USERS_COLLECTION)
            .document(userId)
            .update(updates)
            .get();

    log.info("Profile schema migrated successfully for user: {}", userId);
}
```

**Acceptance Criteria:**
- [ ] `getOrCreateProfile()` creates new schema
- [ ] `storageIntegrations` map initialized empty
- [ ] `preferences` object with defaultStorage
- [ ] Migration logic for old schema (optional, for dev)
- [ ] Proper logging

---

### Task 3.2: Add Helper Methods for Storage Management

Add these methods to `UserProfileService.java`:

```java
/**
 * Get user's default storage provider
 *
 * @param userId Firebase UID
 * @return Provider name (e.g., "google-drive") or null if not set
 */
public String getDefaultStorage(String userId) throws ExecutionException, InterruptedException {
    DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .get();

    if (!doc.exists()) {
        return null;
    }

    Map<String, Object> preferences = (Map<String, Object>) doc.get("preferences");
    if (preferences == null) {
        return null;
    }

    return (String) preferences.get("defaultStorage");
}

/**
 * Set user's default storage provider
 *
 * @param userId Firebase UID
 * @param provider Provider name (e.g., "google-drive")
 */
public void setDefaultStorage(String userId, String provider)
        throws ExecutionException, InterruptedException {

    log.info("Setting default storage to {} for user: {}", provider, userId);

    firestore.collection(USERS_COLLECTION)
            .document(userId)
            .update("preferences.defaultStorage", provider, "updatedAt", Timestamp.now())
            .get();
}

/**
 * Check if user has any storage provider configured
 *
 * @param userId Firebase UID
 * @return true if at least one storage provider is connected
 */
public boolean hasStorageConfigured(String userId)
        throws ExecutionException, InterruptedException {

    DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .get();

    if (!doc.exists()) {
        return false;
    }

    Map<String, Object> integrations = (Map<String, Object>) doc.get("storageIntegrations");
    if (integrations == null || integrations.isEmpty()) {
        return false;
    }

    // Check if any integration is connected
    for (Object value : integrations.values()) {
        if (value instanceof Map) {
            Map<String, Object> integration = (Map<String, Object>) value;
            if (Boolean.TRUE.equals(integration.get("connected"))) {
                return true;
            }
        }
    }

    return false;
}

/**
 * Get all connected storage providers for user
 *
 * @param userId Firebase UID
 * @return List of connected provider names
 */
public List<String> getConnectedStorageProviders(String userId)
        throws ExecutionException, InterruptedException {

    DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .get();

    List<String> connected = new ArrayList<>();

    if (!doc.exists()) {
        return connected;
    }

    Map<String, Object> integrations = (Map<String, Object>) doc.get("storageIntegrations");
    if (integrations == null) {
        return connected;
    }

    for (Map.Entry<String, Object> entry : integrations.entrySet()) {
        if (entry.getValue() instanceof Map) {
            Map<String, Object> integration = (Map<String, Object>) entry.getValue();
            if (Boolean.TRUE.equals(integration.get("connected"))) {
                connected.add(entry.getKey());
            }
        }
    }

    return connected;
}
```

**Acceptance Criteria:**
- [ ] `getDefaultStorage()` returns default provider or null
- [ ] `setDefaultStorage()` updates preferences
- [ ] `hasStorageConfigured()` checks for any connected provider
- [ ] `getConnectedStorageProviders()` returns list
- [ ] Null-safe implementations

---

### Task 3.3: Remove Old OAuth Token Methods

**IMPORTANT:** Don't delete the old methods yet - they might be referenced in other code. Instead, mark them as deprecated:

```java
/**
 * @deprecated Use GoogleDriveStorageProvider.connect() instead
 */
@Deprecated
public void storeOAuthTokens(String userId, String accessToken,
                             String refreshToken, long expiresIn) throws Exception {
    log.warn("DEPRECATED: storeOAuthTokens() called. Use GoogleDriveStorageProvider.connect() instead.");
    // Keep implementation for now, but log warning
    // Will be removed after migration complete
}

/**
 * @deprecated Use GoogleDriveStorageProvider.getAccessToken() instead
 */
@Deprecated
public String getValidOAuthToken(String userId) throws Exception {
    log.warn("DEPRECATED: getValidOAuthToken() called. Use GoogleDriveStorageProvider instead.");
    // Keep implementation for now
}
```

**Acceptance Criteria:**
- [ ] Old methods marked @Deprecated
- [ ] Warning logs added
- [ ] Methods still functional (for transition period)
- [ ] Comments explain replacement

---

### Task 3.4: Update User Controller

**File:** `src/main/java/net/shamansoft/cookbook/controller/UserController.java` (if exists, or create it)

If you have a UserController, update it. If not, this can be skipped.

```java
/**
 * Get current user's profile
 */
@GetMapping("/api/user/profile")
public ResponseEntity<Map<String, Object>> getUserProfile(
        @RequestAttribute("userId") String userId,
        @RequestAttribute("userEmail") String userEmail) {

    log.info("Getting profile for user: {}", userId);

    try {
        Map<String, Object> profile = userProfileService.getOrCreateProfile(userId, userEmail);

        // Don't return sensitive data (encrypted tokens)
        Map<String, Object> safeProfile = new HashMap<>();
        safeProfile.put("userId", profile.get("userId"));
        safeProfile.put("email", profile.get("email"));
        safeProfile.put("authProvider", profile.get("authProvider"));
        safeProfile.put("createdAt", profile.get("createdAt"));

        // Include storage status (but not tokens)
        boolean hasStorage = userProfileService.hasStorageConfigured(userId);
        safeProfile.put("hasStorageConfigured", hasStorage);

        List<String> connectedProviders = userProfileService.getConnectedStorageProviders(userId);
        safeProfile.put("connectedStorageProviders", connectedProviders);

        String defaultStorage = userProfileService.getDefaultStorage(userId);
        safeProfile.put("defaultStorage", defaultStorage);

        return ResponseEntity.ok(safeProfile);

    } catch (Exception e) {
        log.error("Failed to get user profile: {}", e.getMessage());
        return ResponseEntity.internalServerError().build();
    }
}
```

**Acceptance Criteria:**
- [ ] Profile endpoint doesn't expose encrypted tokens
- [ ] Returns storage connection status
- [ ] Returns list of connected providers
- [ ] Returns default storage preference

---

## Firestore Schema Documentation

### New Schema Structure

```javascript
// Collection: users
{
  // User identity
  userId: "G6T4MWki19c3dPqe29d4Pikqfps1",
  email: "shamansoft@gmail.com",
  displayName: "Alex",
  authProvider: "google",  // or "email", "github"

  // Timestamps
  createdAt: Timestamp,
  updatedAt: Timestamp,

  // Storage integrations (encrypted tokens)
  storageIntegrations: {
    googleDrive: {
      connected: true,
      provider: "google-drive",
      accessToken: "CiQAW7z...encrypted...",  // Encrypted by Cloud KMS
      refreshToken: "CiQAW7z...encrypted...", // Encrypted by Cloud KMS
      expiresAt: Timestamp,
      connectedAt: Timestamp,
      metadata: {
        folderName: "My Recipes",
        folderId: "abc123"
      }
    },
    dropbox: null,  // Not connected
    onedrive: null  // Not connected
  },

  // User preferences
  preferences: {
    defaultStorage: "google-drive",  // Which storage to use by default
    autoSave: true,
    language: "en"
  },

  // Optional: Schema version for future migrations
  schemaVersion: 2
}
```

**Security Rules:**
```javascript
// Firestore rules
match /users/{userId} {
  // Users can only read/write their own profile
  allow read, write: if request.auth != null && request.auth.uid == userId;

  // Prevent clients from reading encrypted tokens directly
  // (Backend decrypts them server-side)
}
```

---

## Testing Requirements

### Unit Tests

**File:** `src/test/java/net/shamansoft/cookbook/service/UserProfileServiceTest.java`

Test cases:
- [ ] `getOrCreateProfile()` creates profile with new schema
- [ ] `getOrCreateProfile()` returns existing profile
- [ ] `needsSchemaMigration()` detects old schema
- [ ] `migrateProfileSchema()` moves tokens correctly
- [ ] `getDefaultStorage()` returns null when not set
- [ ] `setDefaultStorage()` updates preference
- [ ] `hasStorageConfigured()` returns false for new users
- [ ] `hasStorageConfigured()` returns true when storage connected
- [ ] `getConnectedStorageProviders()` returns correct list

### Integration Test

Create a test that:
1. Creates new user → verify new schema
2. Adds Google Drive integration → verify storageIntegrations
3. Sets default storage → verify preferences
4. Queries hasStorageConfigured → verify true
5. Removes integration → verify false

---

## Manual Verification

```bash
# 1. Check Firestore Console
# - Navigate to Firestore in GCP console
# - Look at users collection
# - Verify schema matches new structure

# 2. Create a new user
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <firebase-token>"

# 3. Check response
# Should have:
# - storageIntegrations: {}
# - preferences.defaultStorage: null
# - hasStorageConfigured: false
```

---

## Acceptance Criteria Summary

- [ ] `getOrCreateProfile()` creates new schema
- [ ] Helper methods added for storage management
- [ ] Old methods marked deprecated
- [ ] Schema migration logic (optional)
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Firestore schema documented
- [ ] No sensitive data in API responses
- [ ] Backward compatible (doesn't break existing code)

---

## Dependencies

**Required:**
- Firestore configured and accessible
- TokenEncryptionService available (for encrypting tokens)

**New:**
- None

---

## Notes

- **Schema version**: Optional field `schemaVersion: 2` helps track migrations
- **Backward compatibility**: Old methods deprecated but not removed yet
- **Security**: Never return encrypted tokens in API responses - backend decrypts server-side
- **Future-proof**: Easy to add new storage providers (dropbox, onedrive, etc.)
- **Dev mode**: Migration logic handles existing test users, but not required for production (no users yet)

---

## Rollback Plan

If issues occur:
1. Old schema still works (old methods not deleted)
2. Can revert code changes
3. Firestore data not destructively modified
4. Migration is additive (doesn't delete old fields until confirmed working)

---

## Next Steps

After completing this ticket:
1. Verify schema in Firestore console
2. Test with Postman/cURL
3. Move to **BACKEND-4**: Update Recipe Endpoint to use StorageFactory
