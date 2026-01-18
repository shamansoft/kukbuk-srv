package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final WebClient webClient;

    @Value("${cookbook.google.oauth-id}")
    private String googleClientId;

    @Value("${cookbook.google.oauth-secret}")
    private String googleClientSecret;

    private static final long TOKEN_BUFFER_SECONDS = 300; // 5 minutes

    /**
     * Store OAuth tokens in user profile (encrypted)
     *
     * @param userId       Firebase UID
     * @param accessToken  Google OAuth access token
     * @param refreshToken Google OAuth refresh token
     * @param expiresIn    Token expiration in seconds
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

        Optional<UserProfile> existingProfile = userProfileRepository.findByUserId(userId).join();

        if (existingProfile.isPresent()) {
            // Update existing profile
            Map<String, Object> updates = new HashMap<>();
            updates.put("googleOAuthToken", encryptedAccess);
            updates.put("googleRefreshToken", encryptedRefresh);
            updates.put("tokenExpiresAt", expiresAt);
            updates.put("updatedAt", Timestamp.now());

            userProfileRepository.update(userId, updates).join();
            log.info("Updated OAuth tokens in existing profile");
        } else {
            // Create new profile with tokens
            UserProfile newProfile = UserProfile.builder()
                    .userId(userId)
                    .googleOAuthToken(encryptedAccess)
                    .googleRefreshToken(encryptedRefresh)
                    .tokenExpiresAt(expiresAt)
                    .createdAt(Timestamp.now())
                    .updatedAt(Timestamp.now())
                    .build();

            userProfileRepository.save(newProfile).join();
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
        Optional<UserProfile> profileOpt = userProfileRepository.findByUserId(userId).join();

        if (profileOpt.isEmpty()) {
            throw new IllegalStateException("User profile not found: " + userId);
        }

        UserProfile profile = profileOpt.get();

        String encryptedToken = profile.googleOAuthToken();
        if (encryptedToken == null) {
            throw new IllegalStateException("No OAuth token in profile for user: " + userId);
        }

        Timestamp expiresAt = profile.tokenExpiresAt();
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
            return refreshOAuthToken(userId, profile);
        }
    }

    /**
     * Refresh OAuth access token using refresh token
     */
    private String refreshOAuthToken(String userId, UserProfile profile) throws Exception {
        String encryptedRefresh = profile.googleRefreshToken();
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
     * Get user profile
     */
    public Optional<UserProfile> getProfile(String userId) {
        return userProfileRepository.findByUserId(userId).join();
    }

    /**
     * Update user profile with provided fields.
     * Creates profile if it doesn't exist.
     *
     * @param userId      Firebase UID
     * @param email       User email (from OAuth token)
     * @param displayName Display name to update (optional)
     * @param emailUpdate Email to update (optional)
     * @return Updated UserProfile
     */
    public UserProfile updateProfile(String userId, String email, String displayName, String emailUpdate) {

        log.info("Updating profile for user: {}", userId);

        Optional<UserProfile> existingProfile = userProfileRepository.findByUserId(userId).join();

        if (existingProfile.isPresent()) {
            // Update existing profile
            UserProfile current = existingProfile.get();

            // Build updated profile with new values

            return userProfileRepository.save(UserProfile.builder()
                            .userId(current.userId())
                            .uid(current.uid())
                            .email(emailUpdate != null ? emailUpdate : current.email())
                            .displayName(displayName != null ? displayName : current.displayName())
                            .createdAt(current.createdAt())
                            .updatedAt(Timestamp.now())
                            .googleOAuthToken(current.googleOAuthToken())
                            .googleRefreshToken(current.googleRefreshToken())
                            .tokenExpiresAt(current.tokenExpiresAt())
                            .storage(current.storage())
                            .build())
                    .join();
        } else {
            // Create new profile
            log.info("Creating new profile for user: {}", userId);
            return userProfileRepository.save(UserProfile.builder()
                    .userId(userId)
                    .email(emailUpdate != null ? emailUpdate : email)
                    .displayName(displayName)
                    .createdAt(Timestamp.now())
                    .updatedAt(Timestamp.now())
                    .build()).join();
        }
    }
}
