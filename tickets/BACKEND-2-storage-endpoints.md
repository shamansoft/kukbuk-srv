# BACKEND-2: Create Storage Controller & Endpoints

**Priority:** P0 (Blocker)
**Estimated Time:** 3 hours
**Dependencies:** BACKEND-1 (Storage Abstraction)
**Status:** Not Started

---

## Objective

Create REST API endpoints for managing storage integrations. Users will call these endpoints from the extension to connect/disconnect storage providers and check connection status.

---

## Context

Now that we have storage abstraction (BACKEND-1), we need REST endpoints to:
1. Connect Google Drive (store OAuth tokens)
2. Disconnect Google Drive (remove tokens)
3. Get connection status for a provider
4. List all connected providers

These endpoints are separate from auth - they're called AFTER user is authenticated.

---

## Tasks

### Task 2.1: Create DTOs

**File:** `src/main/java/net/shamansoft/cookbook/dto/StorageConnectionRequest.java`

```java
package net.shamansoft.cookbook.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to connect a storage provider
 */
@Data
public class StorageConnectionRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    private String refreshToken;  // Optional - may be null for some providers

    @Min(value = 0, message = "Expires in must be non-negative")
    private long expiresIn = 3600;  // Default 1 hour
}
```

**File:** `src/main/java/net/shamansoft/cookbook/dto/StorageConnectionResponse.java`

```java
package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response after connecting/disconnecting storage
 */
@Data
@Builder
public class StorageConnectionResponse {
    private String status;   // "success" or "error"
    private String message;
    private String provider;
    private boolean connected;
}
```

**File:** `src/main/java/net/shamansoft/cookbook/dto/StorageIntegrationsResponse.java`

```java
package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Response listing all storage integrations
 */
@Data
@Builder
public class StorageIntegrationsResponse {
    private Map<String, StorageStatus> integrations;
    private String defaultProvider;
}
```

**Acceptance Criteria:**
- [ ] All DTOs created
- [ ] Validation annotations on request
- [ ] Lombok annotations for builder/data
- [ ] Clear field names and types

---

### Task 2.2: Create Storage Controller

**File:** `src/main/java/net/shamansoft/cookbook/controller/StorageController.java`

```java
package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageConnectionRequest;
import net.shamansoft.cookbook.dto.StorageConnectionResponse;
import net.shamansoft.cookbook.dto.StorageIntegrationsResponse;
import net.shamansoft.cookbook.dto.StorageStatus;
import net.shamansoft.cookbook.service.storage.StorageFactory;
import net.shamansoft.cookbook.service.storage.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for storage provider integrations
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
public class StorageController {

    private final StorageFactory storageFactory;

    /**
     * Get all storage integrations for the current user
     *
     * GET /api/storage/integrations
     */
    @GetMapping("/integrations")
    public ResponseEntity<StorageIntegrationsResponse> getIntegrations(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Getting storage integrations for user: {}", userEmail);

        try {
            Map<String, StorageStatus> integrations = new HashMap<>();

            // Get status for all providers
            Map<String, Boolean> configured = storageFactory.getConfiguredProviders(userId);

            for (Map.Entry<String, Boolean> entry : configured.entrySet()) {
                String providerName = entry.getKey();
                StorageService provider = storageFactory.getProvider(providerName);

                if (entry.getValue()) {
                    // Get detailed status
                    StorageStatus status = provider.getStatus(userId);
                    integrations.put(providerName, status);
                } else {
                    // Not configured
                    StorageStatus status = new StorageStatus();
                    status.setProvider(providerName);
                    status.setDisplayName(provider.getDisplayName());
                    status.setConnected(false);
                    integrations.put(providerName, status);
                }
            }

            // Get default provider (may be null)
            String defaultProvider = null;
            try {
                StorageService provider = storageFactory.getDefaultProvider(userId);
                defaultProvider = provider.getProviderName();
            } catch (Exception e) {
                // No default provider configured
                log.debug("No default provider for user: {}", userId);
            }

            StorageIntegrationsResponse response = StorageIntegrationsResponse.builder()
                    .integrations(integrations)
                    .defaultProvider(defaultProvider)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get storage integrations for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Connect Google Drive for the current user
     *
     * POST /api/storage/google-drive/connect
     */
    @PostMapping("/google-drive/connect")
    public ResponseEntity<StorageConnectionResponse> connectGoogleDrive(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail,
            @Valid @RequestBody StorageConnectionRequest request) {

        log.info("Connecting Google Drive for user: {}", userEmail);

        try {
            StorageService googleDrive = storageFactory.getProvider("google-drive");

            googleDrive.connect(
                userId,
                request.getAccessToken(),
                request.getRefreshToken(),
                request.getExpiresIn()
            );

            log.info("Google Drive connected successfully for user: {}", userEmail);

            return ResponseEntity.ok(
                StorageConnectionResponse.builder()
                    .status("success")
                    .message("Google Drive connected successfully")
                    .provider("google-drive")
                    .connected(true)
                    .build()
            );

        } catch (Exception e) {
            log.error("Failed to connect Google Drive for user {}: {}", userEmail, e.getMessage());

            return ResponseEntity.status(500).body(
                StorageConnectionResponse.builder()
                    .status("error")
                    .message("Failed to connect Google Drive: " + e.getMessage())
                    .provider("google-drive")
                    .connected(false)
                    .build()
            );
        }
    }

    /**
     * Disconnect Google Drive for the current user
     *
     * DELETE /api/storage/google-drive/disconnect
     */
    @DeleteMapping("/google-drive/disconnect")
    public ResponseEntity<StorageConnectionResponse> disconnectGoogleDrive(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Disconnecting Google Drive for user: {}", userEmail);

        try {
            StorageService googleDrive = storageFactory.getProvider("google-drive");
            googleDrive.disconnect(userId);

            log.info("Google Drive disconnected successfully for user: {}", userEmail);

            return ResponseEntity.ok(
                StorageConnectionResponse.builder()
                    .status("success")
                    .message("Google Drive disconnected successfully")
                    .provider("google-drive")
                    .connected(false)
                    .build()
            );

        } catch (Exception e) {
            log.error("Failed to disconnect Google Drive for user {}: {}", userEmail, e.getMessage());

            return ResponseEntity.status(500).body(
                StorageConnectionResponse.builder()
                    .status("error")
                    .message("Failed to disconnect Google Drive: " + e.getMessage())
                    .provider("google-drive")
                    .connected(true)
                    .build()
            );
        }
    }

    /**
     * Get Google Drive connection status
     *
     * GET /api/storage/google-drive/status
     */
    @GetMapping("/google-drive/status")
    public ResponseEntity<StorageStatus> getGoogleDriveStatus(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Getting Google Drive status for user: {}", userEmail);

        try {
            StorageService googleDrive = storageFactory.getProvider("google-drive");
            StorageStatus status = googleDrive.getStatus(userId);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get Google Drive status for user {}: {}", userEmail, e.getMessage());

            // Return disconnected status
            StorageStatus status = new StorageStatus();
            status.setProvider("google-drive");
            status.setDisplayName("Google Drive");
            status.setConnected(false);

            return ResponseEntity.ok(status);
        }
    }
}
```

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper logging on all operations
- [ ] Error handling with appropriate HTTP status codes
- [ ] CORS configured for Chrome extension
- [ ] Request validation with @Valid
- [ ] Clear response messages

---

### Task 2.3: Update Exception Handler

**File:** `src/main/java/net/shamansoft/cookbook/CookbookExceptionHandler.java`

Add handlers for storage exceptions:

```java
/**
 * Handle NoStorageConfiguredException
 * Returns 428 Precondition Required
 */
@ExceptionHandler(NoStorageConfiguredException.class)
public ResponseEntity<Map<String, Object>> handleNoStorageConfigured(
        NoStorageConfiguredException ex) {

    log.warn("No storage configured: {}", ex.getMessage());

    return ResponseEntity
            .status(428)  // 428 Precondition Required
            .body(Map.of(
                "error", ex.getMessage(),
                "errorCode", "NO_STORAGE_CONFIGURED",
                "action", "connect_storage",
                "message", "Please connect a storage provider (Google Drive, Dropbox, etc.) to save recipes."
            ));
}

/**
 * Handle StorageNotConfiguredException
 * Returns 428 Precondition Required
 */
@ExceptionHandler(StorageNotConfiguredException.class)
public ResponseEntity<Map<String, Object>> handleStorageNotConfigured(
        StorageNotConfiguredException ex) {

    log.warn("Storage {} not configured: {}", ex.getProvider(), ex.getMessage());

    return ResponseEntity
            .status(428)  // 428 Precondition Required
            .body(Map.of(
                "error", ex.getMessage(),
                "errorCode", "STORAGE_NOT_CONFIGURED",
                "provider", ex.getProvider(),
                "action", "connect_storage",
                "message", String.format("Please connect %s to save recipes.", ex.getProvider())
            ));
}
```

**Acceptance Criteria:**
- [ ] Exception handlers added
- [ ] HTTP 428 (Precondition Required) used
- [ ] Clear error codes for client handling
- [ ] Actionable error messages

---

## API Documentation

### Endpoint Summary

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/storage/integrations` | Required | List all storage providers and their status |
| POST | `/api/storage/google-drive/connect` | Required | Connect Google Drive |
| DELETE | `/api/storage/google-drive/disconnect` | Required | Disconnect Google Drive |
| GET | `/api/storage/google-drive/status` | Required | Get Google Drive connection status |

### Example Requests/Responses

**Connect Google Drive:**
```bash
POST /api/storage/google-drive/connect
Headers:
  Authorization: Bearer <firebase-id-token>
  Content-Type: application/json

Body:
{
  "accessToken": "ya29.a0AfH6SMB...",
  "refreshToken": "1//0g...",
  "expiresIn": 3600
}

Response (200 OK):
{
  "status": "success",
  "message": "Google Drive connected successfully",
  "provider": "google-drive",
  "connected": true
}
```

**Get Integrations:**
```bash
GET /api/storage/integrations
Headers:
  Authorization: Bearer <firebase-id-token>

Response (200 OK):
{
  "integrations": {
    "google-drive": {
      "provider": "google-drive",
      "displayName": "Google Drive",
      "connected": true,
      "connectedAt": "2024-01-15T10:30:00Z",
      "expiresAt": "2024-01-15T11:30:00Z"
    },
    "dropbox": {
      "provider": "dropbox",
      "displayName": "Dropbox",
      "connected": false
    }
  },
  "defaultProvider": "google-drive"
}
```

---

## Testing Requirements

### Integration Tests

**File:** `src/test/java/net/shamansoft/cookbook/controller/StorageControllerIntegrationTest.java`

Test scenarios:
1. **Connect Google Drive**
   - [ ] Success: 200 OK with tokens stored
   - [ ] Error: Invalid token returns 500
   - [ ] Validation: Missing accessToken returns 400

2. **Disconnect Google Drive**
   - [ ] Success: 200 OK with tokens removed
   - [ ] Error: Not found returns 404 or handles gracefully

3. **Get Status**
   - [ ] Connected: Returns status with timestamps
   - [ ] Not connected: Returns connected=false

4. **Get Integrations**
   - [ ] Lists all providers with status
   - [ ] Returns default provider

### Manual Testing with cURL

```bash
# Get Firebase token first
FIREBASE_TOKEN="your-firebase-token"

# Connect Google Drive
curl -X POST http://localhost:8080/api/storage/google-drive/connect \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "ya29.test...",
    "refreshToken": "1//test...",
    "expiresIn": 3600
  }'

# Get integrations
curl -X GET http://localhost:8080/api/storage/integrations \
  -H "Authorization: Bearer $FIREBASE_TOKEN"

# Get status
curl -X GET http://localhost:8080/api/storage/google-drive/status \
  -H "Authorization: Bearer $FIREBASE_TOKEN"

# Disconnect
curl -X DELETE http://localhost:8080/api/storage/google-drive/disconnect \
  -H "Authorization: Bearer $FIREBASE_TOKEN"
```

---

## Acceptance Criteria Summary

- [ ] All DTOs created
- [ ] StorageController implemented with all endpoints
- [ ] Exception handlers updated
- [ ] CORS configured correctly
- [ ] Request validation working
- [ ] Integration tests passing
- [ ] API documentation complete
- [ ] Manual testing successful
- [ ] Proper HTTP status codes used
- [ ] Clear error messages

---

## Dependencies

**Required:**
- BACKEND-1 completed (StorageFactory and providers available)
- FirebaseAuthFilter configured (for @RequestAttribute userId)

**New:**
- None

---

## Notes

- All endpoints require Firebase authentication (via Authorization header)
- HTTP 428 (Precondition Required) used for "not configured" errors
- This is different from 401 (Unauthorized) - user IS authenticated, just missing storage setup
- Future providers (Dropbox) will follow same pattern: `/api/storage/dropbox/connect`, etc.

---

## Next Steps

After completing this ticket:
1. Test endpoints with Postman/cURL
2. Move to **BACKEND-3**: Update User Profile Schema
3. Then **BACKEND-4**: Update Recipe Endpoint to use storage abstraction
