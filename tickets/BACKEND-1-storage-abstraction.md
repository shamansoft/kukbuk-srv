# BACKEND-1: Google Drive Storage Integration with Firestore

**Priority:** P0 (Blocker)
**Estimated Time:** 2 hours
**Dependencies:** None
**Status:** Not Started

---

## Objective

Store and retrieve Google Drive OAuth credentials and default folder configuration from Firestore. Use a future-proof schema that can support multiple storage providers later (Dropbox, OneDrive) without requiring data migration.

---

## Context

**Immediate Need:**
- Store encrypted Google Drive OAuth tokens in Firestore
- Retrieve tokens when saving recipes
- Store default Google Drive folder ID
- Handle "not connected" scenarios gracefully

**Future-Proofing:**
- Schema designed to support multiple providers (though code only implements Google Drive for now)
- When Dropbox is added in 6+ months, no Firestore migration needed
- Avoid over-engineering with interfaces/factories until we have multiple providers

---

## Firestore Schema

```javascript
users/{userId}/
  storage:
    type: "googleDrive"           // camelCase, future: "dropbox", "oneDrive"
    connected: true
    accessToken: "encrypted..."    // Encrypted by TokenEncryptionService
    refreshToken: "encrypted..."   // Encrypted, may be null
    expiresAt: Timestamp
    connectedAt: Timestamp
    defaultFolderId: "1ABC..."     // Google Drive folder ID
```

**Benefits:**
- ✅ Single `storage` object (not per-provider nested objects)
- ✅ `type` field makes it easy to add providers later
- ✅ No data migration needed when adding Dropbox
- ✅ Simple to query and update

---

## Tasks

### Task 1.1: Create StorageInfo DTO

**File:** `src/main/java/net/shamansoft/cookbook/dto/StorageInfo.java`

```java
package net.shamansoft.cookbook.dto;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Data;

/**
 * Storage connection information stored in Firestore
 */
@Data
@Builder
public class StorageInfo {
    private String type;              // "googleDrive", "dropbox", etc.
    private boolean connected;
    private String accessToken;       // Encrypted
    private String refreshToken;      // Encrypted, may be null
    private Timestamp expiresAt;
    private Timestamp connectedAt;
    private String defaultFolderId;   // For Google Drive
}
```

**Acceptance Criteria:**
- [x] DTO created with all fields
- [x] Lombok annotations
- [x] JavaDoc

---

### Task 1.2: Create StorageNotConnectedException

**File:** `src/main/java/net/shamansoft/cookbook/exception/StorageNotConnectedException.java`

```java
package net.shamansoft.cookbook.exception;

/**
 * Thrown when user tries to save a recipe but hasn't connected storage
 */
public class StorageNotConnectedException extends RuntimeException {

    public StorageNotConnectedException(String message) {
        super(message);
    }

    public StorageNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Acceptance Criteria:**
- [x] Exception created
- [x] Constructors with message and cause
- [x] Clear JavaDoc

---

### Task 1.3: Create StorageService

**File:** `src/main/java/net/shamansoft/cookbook/service/StorageService.java`

```java
package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing storage provider configuration in Firestore
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;

    private static final String USERS_COLLECTION = "users";
    private static final String STORAGE_FIELD = "storage";

    /**
     * Store Google Drive connection information
     *
     * @param userId Firebase user ID
     * @param accessToken OAuth access token (will be encrypted)
     * @param refreshToken OAuth refresh token (will be encrypted, may be null)
     * @param expiresIn Token expiration in seconds
     * @param defaultFolderId Google Drive folder ID (optional)
     */
    public void connectGoogleDrive(String userId, String accessToken, String refreshToken,
                                   long expiresIn, String defaultFolderId) throws Exception {
        log.info("Connecting Google Drive storage for user: {}", userId);

        String encryptedAccess = tokenEncryptionService.encrypt(accessToken);
        String encryptedRefresh = refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null;

        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 + expiresIn, 0
        );

        Map<String, Object> storage = new HashMap<>();
        storage.put("type", "googleDrive");
        storage.put("connected", true);
        storage.put("accessToken", encryptedAccess);
        if (encryptedRefresh != null) {
            storage.put("refreshToken", encryptedRefresh);
        }
        storage.put("expiresAt", expiresAt);
        storage.put("connectedAt", Timestamp.now());
        if (defaultFolderId != null) {
            storage.put("defaultFolderId", defaultFolderId);
        }

        firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(STORAGE_FIELD, storage)
                .get();

        log.info("Google Drive connected successfully for user: {}", userId);
    }

    /**
     * Get storage information for a user
     *
     * @param userId Firebase user ID
     * @return StorageInfo object
     * @throws StorageNotConnectedException if no storage connected
     */
    public StorageInfo getStorageInfo(String userId) throws Exception {
        log.debug("Getting storage info for user: {}", userId);

        DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .get();

        if (!doc.exists()) {
            throw new StorageNotConnectedException("User profile not found: " + userId);
        }

        Map<String, Object> storage = (Map<String, Object>) doc.get(STORAGE_FIELD);

        if (storage == null || !Boolean.TRUE.equals(storage.get("connected"))) {
            throw new StorageNotConnectedException(
                "No storage connected. Please connect Google Drive first."
            );
        }

        // Decrypt tokens
        String encryptedAccess = (String) storage.get("accessToken");
        String encryptedRefresh = (String) storage.get("refreshToken");

        if (encryptedAccess == null) {
            throw new StorageNotConnectedException("Storage connected but no access token found");
        }

        String accessToken = tokenEncryptionService.decrypt(encryptedAccess);
        String refreshToken = encryptedRefresh != null ?
            tokenEncryptionService.decrypt(encryptedRefresh) : null;

        return StorageInfo.builder()
                .type((String) storage.get("type"))
                .connected(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt((Timestamp) storage.get("expiresAt"))
                .connectedAt((Timestamp) storage.get("connectedAt"))
                .defaultFolderId((String) storage.get("defaultFolderId"))
                .build();
    }

    /**
     * Check if user has storage connected
     *
     * @param userId Firebase user ID
     * @return true if storage is connected
     */
    public boolean isStorageConnected(String userId) throws Exception {
        try {
            getStorageInfo(userId);
            return true;
        } catch (StorageNotConnectedException e) {
            return false;
        }
    }

    /**
     * Disconnect storage for a user
     *
     * @param userId Firebase user ID
     */
    public void disconnectStorage(String userId) throws Exception {
        log.info("Disconnecting storage for user: {}", userId);

        firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(STORAGE_FIELD, null)
                .get();

        log.info("Storage disconnected for user: {}", userId);
    }

    /**
     * Update default folder ID for Google Drive
     *
     * @param userId Firebase user ID
     * @param folderId Google Drive folder ID
     */
    public void updateDefaultFolder(String userId, String folderId) throws Exception {
        log.info("Updating default folder for user {}: {}", userId, folderId);

        firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(STORAGE_FIELD + ".defaultFolderId", folderId)
                .get();

        log.info("Default folder updated successfully");
    }
}
```

**Acceptance Criteria:**
- [x] Service created with all methods
- [x] Token encryption/decryption
- [x] Proper error handling
- [x] Logging on all operations
- [x] JavaDoc on all public methods

---

### Task 1.4: Update DriveService to Use StorageService

**File:** `src/main/java/net/shamansoft/cookbook/service/DriveService.java`

Update `DriveService` to use `StorageService` for getting credentials:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DriveService {

    private final StorageService storageService;
    // ... other dependencies

    /**
     * Save recipe to Google Drive
     */
    public String saveRecipeForUser(String userId, String content, String title, String sourceUrl)
            throws Exception {
        log.info("Saving recipe to Google Drive for user: {}", userId);

        // Get storage info (throws StorageNotConnectedException if not connected)
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (!"googleDrive".equals(storage.getType())) {
            throw new IllegalStateException("Expected Google Drive storage, got: " + storage.getType());
        }

        // Use existing saveRecipe method with token
        String fileUrl = saveRecipe(
            storage.getAccessToken(),
            content,
            title,
            sourceUrl
        );

        log.info("Recipe saved to Google Drive: {}", fileUrl);
        return fileUrl;
    }

    // Keep existing saveRecipe(accessToken, ...) method for direct token usage
    public String saveRecipe(String accessToken, String content, String title, String sourceUrl)
            throws Exception {
        // ... existing implementation
    }
}
```

**Acceptance Criteria:**
- [x] New `saveRecipeForUser()` method uses `StorageService`
- [x] Existing `saveRecipe()` method unchanged (for backward compatibility)
- [x] Proper error handling
- [x] Logging

---

## Testing Requirements

### Unit Tests

**File:** `src/test/java/net/shamansoft/cookbook/service/StorageServiceTest.java`

Test cases:
- [x] connectGoogleDrive() stores encrypted tokens
- [x] getStorageInfo() returns decrypted data
- [x] getStorageInfo() throws when not connected
- [x] isStorageConnected() returns true when connected
- [x] isStorageConnected() returns false when not connected
- [x] disconnectStorage() removes storage object
- [x] updateDefaultFolder() updates folder ID
- [x] Token encryption/decryption works correctly

**File:** `src/test/java/net/shamansoft/cookbook/service/DriveServiceTest.java`

Update existing tests and add:
- [x] saveRecipeForUser() uses StorageService
- [x] saveRecipeForUser() throws when not connected
- [x] saveRecipeForUser() validates storage type

**Target Coverage:** 80%+

---

## Integration Tests

**File:** `src/test/java/net/shamansoft/cookbook/service/StorageServiceIntegrationTest.java`

Create integration test that:
1. Creates user profile in Firestore
2. Connects Google Drive with test tokens
3. Verifies tokens are encrypted in Firestore
4. Retrieves storage info and verifies decryption
5. Updates default folder
6. Disconnects storage
7. Verifies storage object removed from Firestore

---

## Acceptance Criteria Summary

- [x] StorageInfo DTO created
- [x] StorageNotConnectedException created
- [x] StorageService fully implemented
- [x] DriveService updated to use StorageService
- [x] Unit tests written and passing (80%+ coverage)
- [x] Integration tests passing
- [x] JavaDoc on all public methods
- [x] Logging on all important operations
- [x] Proper error handling

---

## Dependencies

**Required:**
- Existing `Firestore` bean
- Existing `TokenEncryptionService`
- Existing `DriveService`

**New:**
- None

---

## Notes

**Why this approach:**
- ✅ Solves immediate problem (store/get Google Drive credentials)
- ✅ Future-proof schema (ready for Dropbox without migration)
- ✅ No over-engineering (no interfaces/factories until needed)
- ✅ Simple to understand and maintain
- ✅ Easy to test

**When adding Dropbox later:**
1. Schema already supports it (`type: "dropbox"`)
2. Add `connectDropbox()` method to `StorageService`
3. Create `DropboxService` (similar to `DriveService`)
4. Router method in controller to check `type` and route accordingly
5. No Firestore migration needed

**Security:**
- Tokens encrypted at rest using `TokenEncryptionService`
- Only decrypted when needed for API calls
- Firestore security rules should restrict access to user's own storage

---

## Next Steps

After completing this ticket:
1. **BACKEND-2**: Create Storage Controller & Endpoints (connect/disconnect/status)
2. **BACKEND-3**: Update Recipe Endpoint to use new storage flow
3. **BACKEND-4**: Add Chrome extension integration
