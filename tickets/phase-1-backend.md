# Phase 1: Backend Firebase Authentication

**Repository:** `sar-srv/extractor/`
**Duration:** 2-3 days
**Dependencies:** None (can start immediately)

---

## Objective

Add Firebase Admin SDK to backend and implement token validation. Keep existing Firestore integration unchanged.

---

## Ticket 1.1: Add Firebase Dependencies

**File:** `extractor/build.gradle.kts`

### Task
Add Firebase Admin SDK to dependencies.

### Implementation

Add to the `dependencies` block:

```kotlin
dependencies {
    // ... existing dependencies

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.3.0")
}
```

### Verification

```bash
cd extractor
./gradlew dependencies | grep firebase-admin
# Should show: com.google.firebase:firebase-admin:9.3.0
```

### Acceptance Criteria
- [ ] Firebase Admin SDK dependency added to build.gradle.kts
- [ ] Dependency resolves successfully

---

## Ticket 1.2: Create Firebase Configuration

**File:** `extractor/src/main/java/net/shamansoft/cookbook/config/FirebaseConfig.java` (NEW)

### Task
Create Spring configuration bean for Firebase initialization.

### Implementation

```java
package net.shamansoft.cookbook.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId("kukbuk-tf")
                .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase initialized for project: kukbuk-tf");
            return app;
        }
        log.info("Firebase already initialized");
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}
```

### Verification

```bash
# Run the application
./gradlew bootRun

# Check logs for:
# "Firebase initialized for project: kukbuk-tf"
```

### Acceptance Criteria
- [ ] FirebaseConfig.java created in config package
- [ ] FirebaseApp bean initializes successfully
- [ ] FirebaseAuth bean created
- [ ] Logs show successful Firebase initialization

---

## Ticket 1.3: Create Firebase Authentication Filter

**File:** `extractor/src/main/java/net/shamansoft/cookbook/security/FirebaseAuthFilter.java` (NEW)

### Task
Create servlet filter to validate Firebase ID tokens on all protected endpoints.

### Implementation

```java
package net.shamansoft.cookbook.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of("/", "/hello");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip auth for public endpoints
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            log.debug("Public endpoint, skipping auth");
            filterChain.doFilter(request, response);
            return;
        }

        // Extract Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No Authorization token for: {}", path);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"No authorization token\"}");
            return;
        }

        String idToken = authHeader.substring(7);

        try {
            // Verify Firebase ID token
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);

            // Store user info in request attributes
            request.setAttribute("userId", decodedToken.getUid());
            request.setAttribute("userEmail", decodedToken.getEmail());

            log.debug("Authenticated: {} ({})", decodedToken.getEmail(), decodedToken.getUid());

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (FirebaseAuthException e) {
            log.error("Token validation failed: {}", e.getMessage());
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
        }
    }
}
```

### Verification

```bash
# Test with invalid token
curl -X GET http://localhost:8080/recipe \
  -H "Authorization: Bearer invalid-token"

# Expected: 401 Unauthorized, {"error": "Invalid token"}

# Test without token
curl -X GET http://localhost:8080/recipe

# Expected: 401 Unauthorized, {"error": "No authorization token"}

# Test public endpoint
curl -X GET http://localhost:8080/

# Expected: 200 OK
```

### Acceptance Criteria
- [ ] FirebaseAuthFilter.java created in security package
- [ ] Filter validates Firebase ID tokens
- [ ] Public endpoints bypass authentication
- [ ] Protected endpoints require valid token
- [ ] userId and userEmail stored in request attributes
- [ ] Returns 401 for invalid/missing tokens

---

## Ticket 1.4: Add User Profile Endpoint

**File:** `extractor/src/main/java/net/shamansoft/cookbook/CookbookController.java`

### Task
Add basic user profile endpoint that returns userId and email from Firebase token.

### Implementation

Add this method to `CookbookController`:

```java
/**
 * Get current user's profile (basic info from Firebase token)
 */
@GetMapping("/api/user/profile")
public ResponseEntity<Map<String, String>> getUserProfile(
        @RequestAttribute("userId") String userId,
        @RequestAttribute("userEmail") String userEmail) {

    log.info("Getting profile for user: {}", userId);

    return ResponseEntity.ok(Map.of(
        "userId", userId,
        "email", userEmail
    ));
}
```

### Verification

```bash
# Get a test Firebase token from Firebase Console
# Authentication → Users → Add user → Get custom token

# Test endpoint
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>"

# Expected response:
# {
#   "userId": "firebase-uid-123",
#   "email": "test@example.com"
# }
```

### Acceptance Criteria
- [ ] GET /api/user/profile endpoint added
- [ ] Returns userId and email from request attributes
- [ ] Requires authentication (filter applies)
- [ ] Returns 200 OK with valid token
- [ ] Returns 401 Unauthorized without token

---

## Ticket 1.5: Update Recipe Endpoint for User Context

**File:** `extractor/src/main/java/net/shamansoft/cookbook/CookbookController.java`

### Task
Update the `/recipe` endpoint to extract userId from Firebase token and log it.

### Implementation

Update the `createRecipe` method signature:

```java
@PostMapping(path = "/recipe", consumes = "application/json", produces = "application/json")
public RecipeResponse createRecipe(
        @RequestBody @Valid Request request,
        @RequestParam(value = "compression", required = false) String compression,
        @RequestAttribute("userId") String userId,           // ADD THIS
        @RequestAttribute("userEmail") String userEmail,     // ADD THIS
        @RequestHeader HttpHeaders httpHeaders)
        throws IOException {

    log.info("Creating recipe for user: {} ({})", userEmail, userId);

    // Existing logic continues unchanged...
    String authToken = tokenService.getAuthToken(httpHeaders);
    String html = extractHtml(request, compression);
    // ... rest of existing code
}
```

### Notes
- Don't change existing logic - just add the new parameters
- userId will be used in Phase 2 for storing user-specific data
- For now, just log it to verify it's working

### Verification

```bash
# Save a recipe with Firebase token
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -H "X-Google-Token: <GOOGLE_OAUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "html": "<html>Recipe content</html>",
    "title": "Test Recipe",
    "url": "https://example.com/recipe"
  }'

# Check logs for:
# "Creating recipe for user: test@example.com (firebase-uid-123)"
```

### Acceptance Criteria
- [ ] POST /recipe has userId and userEmail parameters
- [ ] userId extracted from Firebase token via filter
- [ ] User info logged on recipe creation
- [ ] Existing recipe save functionality still works

---

## Ticket 1.6: Update TokenService for Dual-Token Model

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/TokenService.java`

### Task
Update TokenService interface documentation and implementation to clarify dual-token model.

### Implementation

Update `TokenService.java` interface:

```java
package net.shamansoft.cookbook.service;

import org.springframework.http.HttpHeaders;

import javax.naming.AuthenticationException;

public interface TokenService {

    /**
     * Verifies the provided OAuth2 access token by calling Google's tokeninfo endpoint.
     *
     * @param authToken OAuth2 access token header value
     * @return true if token is valid
     */
    boolean verifyToken(String authToken);

    /**
     * Retrieves the Google OAuth access token for Drive API from HTTP headers.
     *
     * IMPORTANT - Dual-Token Model:
     * - Firebase ID token: Used for backend authentication (in Authorization header)
     *   → Validated by FirebaseAuthFilter
     * - Google OAuth token: Used for Google Drive API access (in X-Google-Token header)
     *   → Retrieved by this method
     *
     * Clients must send both tokens:
     * - Authorization: Bearer <firebase-id-token>
     * - X-Google-Token: <google-oauth-token>
     *
     * @param httpHeaders HTTP headers containing the Google OAuth token
     * @return the Google OAuth access token if valid
     * @throws AuthenticationException if the token is invalid or not present
     */
    String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException;
}
```

Update implementation to look for `X-Google-Token` header:

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/TokenRestService.java`

```java
@Override
public String getAuthToken(HttpHeaders httpHeaders) throws AuthenticationException {
    // Look for Google OAuth token in X-Google-Token header
    String authToken = httpHeaders.getFirst("X-Google-Token");

    if (authToken == null || authToken.isBlank()) {
        log.warn("No Google OAuth token found in X-Google-Token header");
        throw new AuthenticationException("Google OAuth token required for Drive access");
    }

    // Optionally verify the token is valid
    if (!verifyToken(authToken)) {
        throw new AuthenticationException("Invalid Google OAuth token");
    }

    return authToken;
}
```

### Verification

```bash
# Test with both tokens
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -H "X-Google-Token: <GOOGLE_OAUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html>Test</html>","title":"Test","url":"https://test.com"}'

# Should work successfully

# Test with missing Google OAuth token
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html>Test</html>","title":"Test","url":"https://test.com"}'

# Expected error about missing Google OAuth token
```

### Acceptance Criteria
- [ ] TokenService interface documented with dual-token explanation
- [ ] Implementation looks for X-Google-Token header
- [ ] Throws AuthenticationException if Google OAuth token missing
- [ ] Firebase token validated by filter (Authorization header)
- [ ] Google OAuth token retrieved by TokenService (X-Google-Token header)

---

## Ticket 1.7: Setup Cloud KMS for Token Encryption

**Task:** Create Cloud KMS resources for encrypting OAuth tokens at rest

### Task
Set up Google Cloud KMS (Key Management Service) to encrypt/decrypt OAuth tokens stored in Firestore.

### Implementation

**Option A: Via gcloud CLI** (Recommended for Phase 1)

```bash
# Set project
gcloud config set project kukbuk-tf

# Enable Cloud KMS API
gcloud services enable cloudkms.googleapis.com

# Create key ring
gcloud kms keyrings create cookbook-keyring \
  --location=us-west1 \
  --project=kukbuk-tf

# Create crypto key for OAuth token encryption
gcloud kms keys create oauth-token-key \
  --location=us-west1 \
  --keyring=cookbook-keyring \
  --purpose=encryption \
  --project=kukbuk-tf

# Grant service account permission to encrypt/decrypt
SA_EMAIL="cookbook@kukbuk-tf.iam.gserviceaccount.com"

gcloud kms keys add-iam-policy-binding oauth-token-key \
  --location=us-west1 \
  --keyring=cookbook-keyring \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/cloudkms.cryptoKeyEncrypterDecrypter" \
  --project=kukbuk-tf
```

**Option B: Via Terraform** (Will be automated in Phase 1 Terraform ticket)

See `phase-1-tf.md` Ticket 9 for Terraform implementation.

### Verification

```bash
# List key rings
gcloud kms keyrings list --location=us-west1 --project=kukbuk-tf

# List keys
gcloud kms keys list \
  --location=us-west1 \
  --keyring=cookbook-keyring \
  --project=kukbuk-tf

# Verify service account permissions
gcloud kms keys get-iam-policy oauth-token-key \
  --location=us-west1 \
  --keyring=cookbook-keyring \
  --project=kukbuk-tf
```

### Acceptance Criteria
- [ ] Cloud KMS API enabled
- [ ] Key ring `cookbook-keyring` created in us-west1
- [ ] Crypto key `oauth-token-key` created with encryption purpose
- [ ] Service account has `roles/cloudkms.cryptoKeyEncrypterDecrypter` permission
- [ ] Verification commands show resources exist

---

## Ticket 1.8: Create TokenEncryptionService

**File:** `extractor/src/main/java/net/shamansoft/cookbook/security/TokenEncryptionService.java` (NEW)

### Task
Create service to encrypt and decrypt OAuth tokens using Cloud KMS.

### Implementation

First, add Cloud KMS dependency to `build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies

    // Cloud KMS for token encryption
    implementation("com.google.cloud:google-cloud-kms:2.30.0")
}
```

Create `TokenEncryptionService.java`:

```java
package net.shamansoft.cookbook.security;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for encrypting and decrypting OAuth tokens using Google Cloud KMS.
 *
 * Tokens are encrypted at rest in Firestore for security.
 */
@Service
@Slf4j
public class TokenEncryptionService {

    @Value("${gcp.project-id:kukbuk-tf}")
    private String projectId;

    @Value("${gcp.kms.location:us-west1}")
    private String location;

    @Value("${gcp.kms.keyring:cookbook-keyring}")
    private String keyring;

    @Value("${gcp.kms.key:oauth-token-key}")
    private String keyName;

    /**
     * Encrypt plaintext using Cloud KMS
     *
     * @param plaintext The plaintext to encrypt (e.g., OAuth token)
     * @return Base64-encoded ciphertext
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Plaintext cannot be null or blank");
        }

        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            CryptoKeyName cryptoKeyName = CryptoKeyName.of(
                projectId, location, keyring, keyName
            );

            ByteString plaintextBytes = ByteString.copyFrom(
                plaintext.getBytes(StandardCharsets.UTF_8)
            );

            var response = client.encrypt(cryptoKeyName, plaintextBytes);
            byte[] ciphertext = response.getCiphertext().toByteArray();

            String encrypted = Base64.getEncoder().encodeToString(ciphertext);
            log.debug("Successfully encrypted token");
            return encrypted;

        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext using Cloud KMS
     *
     * @param encryptedToken Base64-encoded ciphertext
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new IllegalArgumentException("Encrypted token cannot be null or blank");
        }

        try (KeyManagementServiceClient client = KeyManagementServiceClient.create()) {
            CryptoKeyName cryptoKeyName = CryptoKeyName.of(
                projectId, location, keyring, keyName
            );

            byte[] ciphertext = Base64.getDecoder().decode(encryptedToken);
            ByteString ciphertextBytes = ByteString.copyFrom(ciphertext);

            var response = client.decrypt(cryptoKeyName, ciphertextBytes);
            String decrypted = response.getPlaintext().toString(StandardCharsets.UTF_8);

            log.debug("Successfully decrypted token");
            return decrypted;

        } catch (Exception e) {
            log.error("Failed to decrypt token: {}", e.getMessage());
            throw new RuntimeException("Token decryption failed", e);
        }
    }
}
```

### Verification

Create a simple test:

```java
@SpringBootTest
class TokenEncryptionServiceTest {

    @Autowired
    private TokenEncryptionService tokenEncryptionService;

    @Test
    void testEncryptDecrypt() {
        String original = "ya29.test-oauth-token-12345";

        String encrypted = tokenEncryptionService.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = tokenEncryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }
}
```

Run:
```bash
./gradlew test --tests TokenEncryptionServiceTest
```

### Acceptance Criteria
- [ ] Cloud KMS dependency added to build.gradle.kts
- [ ] TokenEncryptionService.java created
- [ ] encrypt() method works with Cloud KMS
- [ ] decrypt() method works with Cloud KMS
- [ ] Proper error handling for null/blank inputs
- [ ] Logging added for debugging
- [ ] Test passes locally (requires KMS setup)

---

## Ticket 1.9: Add OAuth Token Storage to UserProfileService

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/UserProfileService.java` (NEW)

### Task
Create UserProfileService to manage user profiles in Firestore, including encrypted OAuth token storage.

### Implementation

```java
package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;

    @Value("${google.oauth.client-id}")
    private String googleClientId;

    @Value("${google.oauth.client-secret}")
    private String googleClientSecret;

    private static final String USERS_COLLECTION = "users";
    private static final long TOKEN_BUFFER_SECONDS = 300; // 5 minutes

    /**
     * Store OAuth tokens in user profile (encrypted)
     *
     * @param userId Firebase UID
     * @param accessToken Google OAuth access token
     * @param refreshToken Google OAuth refresh token
     * @param expiresIn Token expiration in seconds
     */
    public void storeOAuthTokens(String userId, String accessToken,
                                 String refreshToken, long expiresIn)
            throws Exception {

        log.info("Storing OAuth tokens for user: {}", userId);

        String encryptedAccess = tokenEncryptionService.encrypt(accessToken);
        String encryptedRefresh = tokenEncryptionService.encrypt(refreshToken);

        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 + expiresIn, 0
        );

        Map<String, Object> updates = new HashMap<>();
        updates.put("googleOAuthToken", encryptedAccess);
        updates.put("googleRefreshToken", encryptedRefresh);
        updates.put("tokenExpiresAt", expiresAt);
        updates.put("updatedAt", Timestamp.now());

        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentSnapshot doc = docRef.get().get();

        if (doc.exists()) {
            // Update existing profile
            docRef.update(updates).get();
            log.info("Updated OAuth tokens in existing profile");
        } else {
            // Create new profile with tokens
            updates.put("userId", userId);
            updates.put("createdAt", Timestamp.now());
            docRef.set(updates).get();
            log.info("Created new profile with OAuth tokens");
        }
    }

    /**
     * Get valid OAuth access token for user (with auto-refresh if needed)
     *
     * @param userId Firebase UID
     * @return Valid OAuth access token
     * @throws Exception if profile not found or token refresh fails
     */
    public String getValidOAuthToken(String userId) throws Exception {
        DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .get();

        if (!doc.exists()) {
            throw new IllegalStateException("User profile not found: " + userId);
        }

        String encryptedToken = doc.getString("googleOAuthToken");
        if (encryptedToken == null) {
            throw new IllegalStateException("No OAuth token in profile for user: " + userId);
        }

        Timestamp expiresAt = doc.getTimestamp("tokenExpiresAt");
        if (expiresAt == null) {
            throw new IllegalStateException("No token expiration in profile");
        }

        // Check if token is still valid (with buffer)
        long now = System.currentTimeMillis() / 1000;

        if (expiresAt.getSeconds() - now > TOKEN_BUFFER_SECONDS) {
            // Token is still valid, decrypt and return
            log.debug("Using cached OAuth token for user: {}", userId);
            return tokenEncryptionService.decrypt(encryptedToken);
        } else {
            // Token expired or about to expire, refresh it
            log.info("OAuth token expired or expiring soon, refreshing for user: {}", userId);
            return refreshOAuthToken(userId, doc);
        }
    }

    /**
     * Refresh OAuth access token using refresh token
     */
    private String refreshOAuthToken(String userId, DocumentSnapshot doc) throws Exception {
        String encryptedRefresh = doc.getString("googleRefreshToken");
        if (encryptedRefresh == null) {
            throw new IllegalStateException("No refresh token in profile");
        }

        String refreshToken = tokenEncryptionService.decrypt(encryptedRefresh);

        // Call Google's token endpoint to refresh
        String tokenUrl = "https://oauth2.googleapis.com/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                tokenUrl, request, Map.class
            );

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalStateException("Failed to refresh OAuth token");
            }

            String newAccessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (expiresIn == null) {
                expiresIn = 3600; // Default to 1 hour
            }

            // Store new access token
            storeOAuthTokens(userId, newAccessToken, refreshToken, expiresIn.longValue());

            log.info("Successfully refreshed OAuth token for user: {}", userId);
            return newAccessToken;

        } catch (Exception e) {
            log.error("Failed to refresh OAuth token: {}", e.getMessage());
            throw new Exception("OAuth token refresh failed", e);
        }
    }

    /**
     * Get or create minimal user profile
     */
    public Map<String, Object> getOrCreateProfile(String userId, String email)
            throws ExecutionException, InterruptedException {

        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentSnapshot doc = docRef.get().get();

        if (doc.exists()) {
            return doc.getData();
        } else {
            log.info("Creating new profile for user: {}", userId);
            Map<String, Object> profile = new HashMap<>();
            profile.put("userId", userId);
            profile.put("email", email);
            profile.put("createdAt", Timestamp.now());
            profile.put("updatedAt", Timestamp.now());

            docRef.set(profile).get();
            return profile;
        }
    }
}
```

### Verification

```bash
# Build should succeed
./gradlew build

# Check for compilation errors
./gradlew compileJava
```

### Acceptance Criteria
- [ ] UserProfileService.java created
- [ ] storeOAuthTokens() method encrypts and stores tokens
- [ ] getValidOAuthToken() retrieves token from profile
- [ ] Automatic token refresh when expired (< 5 min remaining)
- [ ] Creates user profile if doesn't exist
- [ ] Proper error handling and logging
- [ ] Uses TokenEncryptionService for encryption/decryption
- [ ] Calls Google token endpoint for refresh

---

## Ticket 1.10: Add OAuth Token Storage Endpoint

**File:** `extractor/src/main/java/net/shamansoft/cookbook/CookbookController.java`

### Task
Add endpoint for clients to send OAuth tokens to backend for secure storage.

### Implementation

**Step 1:** Create DTO for OAuth token request

Create `extractor/src/main/java/net/shamansoft/cookbook/dto/OAuthTokenRequest.java`:

```java
package net.shamansoft.cookbook.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * Request DTO for storing OAuth tokens
 */
@Data
public class OAuthTokenRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @Min(value = 0, message = "Expires in must be non-negative")
    private long expiresIn;
}
```

**Step 2:** Add endpoint to CookbookController

Add these imports:
```java
import net.shamansoft.cookbook.dto.OAuthTokenRequest;
import net.shamansoft.cookbook.service.UserProfileService;
import javax.validation.Valid;
```

Add the field:
```java
@Autowired
private UserProfileService userProfileService;
```

Add the endpoint method:
```java
/**
 * Store OAuth tokens after user sign-in
 *
 * Clients call this endpoint after authentication to send OAuth tokens
 * for secure storage on the backend. Backend will then manage token
 * refresh automatically.
 */
@PostMapping("/api/user/oauth-tokens")
public ResponseEntity<Map<String, String>> storeOAuthTokens(
        @RequestAttribute("userId") String userId,
        @RequestBody @Valid OAuthTokenRequest tokenRequest) {

    log.info("Storing OAuth tokens for user: {}", userId);

    try {
        userProfileService.storeOAuthTokens(
            userId,
            tokenRequest.getAccessToken(),
            tokenRequest.getRefreshToken(),
            tokenRequest.getExpiresIn()
        );

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "OAuth tokens stored successfully"
        ));

    } catch (Exception e) {
        log.error("Failed to store OAuth tokens: {}", e.getMessage());
        return ResponseEntity.status(500).body(Map.of(
            "status", "error",
            "message", "Failed to store OAuth tokens: " + e.getMessage()
        ));
    }
}
```

### Verification

```bash
# Start application
./gradlew bootRun

# Test endpoint (requires valid Firebase token)
curl -X POST http://localhost:8080/api/user/oauth-tokens \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "ya29.test-access-token",
    "refreshToken": "1//test-refresh-token",
    "expiresIn": 3600
  }'

# Expected response:
# {
#   "status": "success",
#   "message": "OAuth tokens stored successfully"
# }

# Verify in Firestore Console that tokens are encrypted (not plaintext)
```

### Acceptance Criteria
- [ ] OAuthTokenRequest.java DTO created with validation
- [ ] POST /api/user/oauth-tokens endpoint added
- [ ] Endpoint requires Firebase authentication
- [ ] Validates request body
- [ ] Calls UserProfileService.storeOAuthTokens()
- [ ] Returns success response on successful storage
- [ ] Returns error response with details on failure
- [ ] Tokens stored encrypted in Firestore

---

## Ticket 1.11: Update Recipe Endpoint to Use Profile OAuth Token

**File:** `extractor/src/main/java/net/shamansoft/cookbook/CookbookController.java`

### Task
Update the `/recipe` endpoint to retrieve OAuth token from user profile instead of requiring X-Google-Token header.

### Implementation

Update the `createRecipe` method:

```java
@PostMapping(path = "/recipe", consumes = "application/json", produces = "application/json")
public RecipeResponse createRecipe(
        @RequestBody @Valid Request request,
        @RequestParam(value = "compression", required = false) String compression,
        @RequestAttribute("userId") String userId,
        @RequestAttribute("userEmail") String userEmail,
        @RequestHeader HttpHeaders httpHeaders)
        throws IOException {

    log.info("Creating recipe for user: {} ({})", userEmail, userId);

    // Get OAuth token from user profile (with auto-refresh)
    // Fall back to X-Google-Token header for backward compatibility
    String googleOAuthToken;
    try {
        googleOAuthToken = userProfileService.getValidOAuthToken(userId);
        log.debug("Using OAuth token from user profile");
    } catch (Exception e) {
        // Fallback: Try X-Google-Token header (for clients not yet updated)
        log.warn("Failed to get OAuth token from profile ({}), trying X-Google-Token header",
                 e.getMessage());

        try {
            googleOAuthToken = tokenService.getAuthToken(httpHeaders);
            log.debug("Using OAuth token from X-Google-Token header (fallback)");
        } catch (Exception headerException) {
            log.error("No OAuth token in profile or X-Google-Token header");
            throw new RuntimeException(
                "OAuth token not found. Please sign in again to store tokens.", headerException
            );
        }
    }

    // Existing logic continues with googleOAuthToken...
    String html = extractHtml(request, compression);

    // Transform HTML to recipe using Gemini
    RecipeDto recipe = transformer.transform(html);

    // Save to Google Drive using the OAuth token
    // (existing drive save logic uses googleOAuthToken)

    RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
            .title(recipe.getTitle())
            .ingredients(recipe.getIngredients())
            .instructions(recipe.getInstructions());

    // ... rest of existing code

    return responseBuilder.build();
}
```

### Verification

**Test 1: With OAuth token in profile**

```bash
# First, store OAuth tokens
curl -X POST http://localhost:8080/api/user/oauth-tokens \
  -H "Authorization: Bearer <FIREBASE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"accessToken":"<OAUTH_TOKEN>","refreshToken":"<REFRESH_TOKEN>","expiresIn":3600}'

# Then, save a recipe (no X-Google-Token header needed!)
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "html": "<html>Recipe content</html>",
    "title": "Test Recipe",
    "url": "https://example.com/recipe"
  }'

# Should succeed with OAuth token from profile
```

**Test 2: Fallback to X-Google-Token header**

```bash
# If profile doesn't have token, X-Google-Token header still works
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_TOKEN>" \
  -H "X-Google-Token: <OAUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "html": "<html>Recipe content</html>",
    "title": "Test Recipe",
    "url": "https://example.com/recipe"
  }'

# Should succeed with header fallback
```

**Test 3: No token**

```bash
# Without OAuth token in profile or header, should fail
curl -X POST http://localhost:8080/recipe \
  -H "Authorization: Bearer <FIREBASE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "html": "<html>Recipe content</html>",
    "title": "Test Recipe",
    "url": "https://example.com/recipe"
  }'

# Expected: Error about missing OAuth token
```

### Acceptance Criteria
- [ ] createRecipe() tries to get OAuth token from profile first
- [ ] Falls back to X-Google-Token header if profile doesn't have token
- [ ] Returns clear error if no token found in either location
- [ ] Existing recipe save logic works with retrieved token
- [ ] Logs indicate which token source was used (profile vs header)
- [ ] Backward compatible with Phase 1 clients sending X-Google-Token

---

## Ticket 1.12: Deploy to Cloud Run

**Files:** `extractor/scripts/build.sh`, `extractor/scripts/deploy.sh`

### Task
Build and deploy the updated backend to Cloud Run.

### Implementation

```bash
cd extractor/scripts

# Build Docker image
./build.sh v1.0.0-alpha.1

# Deploy to Cloud Run
# Note: Service account should have Firebase Admin role (set up in GCP milestone)
# This will be verified after deployment
```

### Environment Variables to Verify

Ensure Cloud Run service has:
- `SPRING_PROFILES_ACTIVE=gcp`
- `GCP_PROJECT_ID=kukbuk-tf`
- Service account with Application Default Credentials

### Verification

```bash
# Check deployment
gcloud run services describe cookbook --region us-west1 --format='value(status.url)'

# Get the URL and test
CLOUD_RUN_URL=$(gcloud run services describe cookbook --region us-west1 --format='value(status.url)')

# Test health endpoint
curl $CLOUD_RUN_URL/

# Test authenticated endpoint with Firebase token
curl $CLOUD_RUN_URL/api/user/profile \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>"
```

### Acceptance Criteria
- [ ] Docker image built successfully
- [ ] Deployed to Cloud Run
- [ ] Service is accessible
- [ ] Health check endpoint works
- [ ] Firebase authentication works in Cloud Run
- [ ] Can retrieve user profile with valid Firebase token

---

## Testing Checklist

### Unit Tests
- [ ] Build succeeds: `./gradlew build`
- [ ] Tests pass: `./gradlew test`
- [ ] No compilation errors

### Local Integration Tests
- [ ] App starts successfully: `./gradlew bootRun`
- [ ] Firebase initializes (check logs)
- [ ] Public endpoints work without auth
- [ ] Protected endpoints require auth
- [ ] Valid Firebase token works
- [ ] Invalid token returns 401

### Cloud Run Tests
- [ ] Deployment successful
- [ ] Service accessible
- [ ] Firebase auth works in cloud
- [ ] Can save recipe with Firebase token only (OAuth token from profile)
- [ ] Logs show userId on recipe creation
- [ ] OAuth token auto-refresh works

---

## Notes

### Token Architecture

**Firebase ID Token** (in `Authorization: Bearer <token>`)
   - Purpose: Backend authentication
   - Validated by: FirebaseAuthFilter
   - Provides: userId, userEmail
   - Lifetime: 1 hour (auto-refreshed by Firebase SDK on clients)
   - **Sent by clients on every API request**

**Google OAuth Tokens** (stored encrypted in Firestore user profile)
   - **Access Token**: Short-lived (1 hour), used for Google Drive API
   - **Refresh Token**: Long-lived, used to get new access tokens
   - **Stored encrypted** using Cloud KMS
   - **Auto-refreshed** by backend when expired
   - **Sent once** by clients after sign-in to `/api/user/oauth-tokens`

### Why Separate OAuth Token Storage?

- **Security**: OAuth tokens encrypted at rest in Firestore using Cloud KMS
- **Simplicity**: Clients only need to manage Firebase ID token
- **Reliability**: Backend auto-refreshes expired OAuth tokens
- **Server-side operations**: Backend can access Drive without client involvement

### Client Flow

1. User signs in with Google (Firebase Auth)
2. Client gets both Firebase ID token and Google OAuth tokens
3. Client sends OAuth tokens once to `/api/user/oauth-tokens` for storage
4. For all subsequent API calls, client only sends Firebase ID token
5. Backend retrieves OAuth token from user profile (auto-refreshes if needed)

### Backward Compatibility

The recipe endpoint supports fallback to `X-Google-Token` header for clients not yet updated to store tokens on backend. This allows gradual migration.

### Service Account Permissions

The Cloud Run service account needs:
- `roles/firebase.admin` - To verify Firebase ID tokens
- `roles/datastore.user` - To access Firestore (existing)
- `roles/cloudkms.cryptoKeyEncrypterDecrypter` - To encrypt/decrypt OAuth tokens

These will be granted in the GCP setup milestone.

---

## Estimated Time

- Ticket 1.1: 15 minutes (Firebase dependencies)
- Ticket 1.2: 30 minutes (Firebase config)
- Ticket 1.3: 1 hour (Firebase auth filter)
- Ticket 1.4: 30 minutes (User profile endpoint)
- Ticket 1.5: 15 minutes (Update recipe endpoint for userId)
- Ticket 1.6: 30 minutes (TokenService dual-token docs)
- **Ticket 1.7: 30 minutes (Cloud KMS setup)**
- **Ticket 1.8: 1 hour (TokenEncryptionService)**
- **Ticket 1.9: 1.5 hours (UserProfileService)**
- **Ticket 1.10: 45 minutes (OAuth token storage endpoint)**
- **Ticket 1.11: 30 minutes (Update recipe endpoint for profile tokens)**
- Ticket 1.12: 1 hour (Deploy to Cloud Run)
- Testing: 1.5 hours

**Total: 3-4 days** (including OAuth token storage, encryption, testing, and debugging)

---

## Dependencies

- None initially
- GCP Firebase setup (Milestone 2) must be completed before Cloud Run deployment will fully work
- Can develop and test locally first

---

## Completion Criteria

✅ All tickets completed
✅ All acceptance criteria met
✅ Deployed to Cloud Run
✅ Firebase authentication working
✅ Ready for extension and mobile integration
