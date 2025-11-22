package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final WebClient webClient;

    @Value("${cookbook.google.oauth-id}")
    private String googleClientId;

    @Value("${cookbook.google.oauth-secret}")
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

        try {
            Map<String, Object> response = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

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
