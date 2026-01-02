package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing storage provider configuration in Firestore
 */
@Service
@Slf4j
public class StorageService {

    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final WebClient webClient;

    @Value("${cookbook.google.oauth-id}")
    private String googleClientId;

    @Value("${cookbook.google.oauth-secret}")
    private String googleClientSecret;

    private static final String USERS_COLLECTION = "users";
    private static final String STORAGE_FIELD = "storage";

    // Refresh token if it expires within this window (5 minutes)
    private static final long TOKEN_BUFFER_SECONDS = 300;

    public StorageService(Firestore firestore,
                         TokenEncryptionService tokenEncryptionService,
                         WebClient.Builder webClientBuilder) {
        this.firestore = firestore;
        this.tokenEncryptionService = tokenEncryptionService;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Store Google Drive connection information
     *
     * @param userId          Firebase user ID
     * @param accessToken     OAuth access token (will be encrypted)
     * @param refreshToken    OAuth refresh token (will be encrypted, may be null)
     * @param expiresIn       Token expiration in seconds
     * @param defaultFolderId Google Drive folder ID (optional)
     */
    public void connectGoogleDrive(String userId, String accessToken, String refreshToken,
                                   long expiresIn, String defaultFolderId) {
        log.info("Connecting Google Drive storage for user: {}", userId);

        try {

            StorageEntity storageEntity = StorageEntity.builder()
                    .type(StorageType.GOOGLE_DRIVE.getFirestoreValue())
                    .connected(true)
                    .accessToken(tokenEncryptionService.encrypt(accessToken))
                    .refreshToken(refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null)
                    .expiresAt(Timestamp.ofTimeSecondsAndNanos(
                            System.currentTimeMillis() / 1000 + expiresIn, 0))
                    .connectedAt(Timestamp.now())
                    .defaultFolderId(defaultFolderId)
                    .build();

            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(STORAGE_FIELD, storageEntity.toMap())
                    .get();

            log.info("Google Drive connected successfully for user: {}", userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to connect Google Drive: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to connect Google Drive", e);
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Failed to encrypt tokens or update Firestore", e);
        }
    }

    /**
     * Get storage information for a user with automatic token refresh
     *
     * @param userId Firebase user ID
     * @return StorageInfo domain object with valid (possibly refreshed) tokens
     * @throws StorageNotConnectedException if no storage connected or user profile doesn't exist
     * @throws DatabaseUnavailableException if database operation fails
     */
    public StorageInfo getStorageInfo(String userId) {
        log.debug("Getting storage info for user: {}", userId);

        try {
            // 1. Fetch data from Firestore
            DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .get()
                    .get();

            // 2. Validate user exists - if not, treat as "no storage configured"
            if (!doc.exists()) {
                throw new StorageNotConnectedException("No user profile found. Please connect storage first.");
            }

            UserProfile userProfile = doc.toObject(UserProfile.class);

            if (userProfile == null || userProfile.storage() == null) {
                throw new StorageNotConnectedException("No storage configuration found for user: " + userId);
            }

            StorageEntity storageEntity = userProfile.storage();

            // 3. Domain logic validation
            if (!storageEntity.connected()) {
                throw new StorageNotConnectedException(
                        "Storage not connected. Please connect Google Drive first."
                );
            }

            if (storageEntity.accessToken() == null) {
                throw new StorageNotConnectedException(
                        "Storage connected but no access token found for user: " + userId
                );
            }

            // 4. Check if token needs refresh
            if (isTokenExpired(storageEntity)) {
                log.info("OAuth token expired or expiring soon for user: {}, refreshing...", userId);
                return refreshAccessToken(userId, storageEntity);
            }

            // 5. Token is valid, decrypt and return
            log.debug("Using cached OAuth token for user: {}", userId);
            return storageEntity.toDto(tokenEncryptionService);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to fetch user profile: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to fetch user profile", e);
        } catch (StorageNotConnectedException e) {
            // Re-throw domain exception as-is
            throw e;
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Unexpected error while retrieving storage info", e);
        }
    }

    /**
     * Check if access token is expired or expiring soon
     */
    private boolean isTokenExpired(StorageEntity storage) {
        if (storage.expiresAt() == null) {
            log.warn("No expiration time found for token, assuming expired");
            return true;
        }

        long now = System.currentTimeMillis() / 1000;
        long expiresAt = storage.expiresAt().getSeconds();

        boolean isExpired = (expiresAt - now) <= TOKEN_BUFFER_SECONDS;

        if (isExpired) {
            log.debug("Token expires at {}, now is {}, buffer is {}s - needs refresh",
                     expiresAt, now, TOKEN_BUFFER_SECONDS);
        }

        return isExpired;
    }

    /**
     * Refresh access token using refresh token
     */
    private StorageInfo refreshAccessToken(String userId, StorageEntity storage) {
        if (storage.refreshToken() == null) {
            throw new StorageNotConnectedException(
                "Access token expired and no refresh token available. Please reconnect storage."
            );
        }

        try {
            String refreshToken = tokenEncryptionService.decrypt(storage.refreshToken());

            log.info("Calling Google OAuth token endpoint to refresh access token for user: {}", userId);

            // Call Google's token endpoint
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");

            Map<String, Object> response = webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Invalid response from Google OAuth: " + response);
            }

            String newAccessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (expiresIn == null) {
                expiresIn = 3600; // Default to 1 hour
            }

            log.info("Successfully refreshed OAuth token for user: {}, expires in {}s", userId, expiresIn);

            // Update Firestore with new token
            String encryptedAccessToken = tokenEncryptionService.encrypt(newAccessToken);
            Timestamp newExpiresAt = Timestamp.ofTimeSecondsAndNanos(
                System.currentTimeMillis() / 1000 + expiresIn, 0
            );

            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    STORAGE_FIELD + ".accessToken", encryptedAccessToken,
                    STORAGE_FIELD + ".expiresAt", newExpiresAt
                )
                .get();

            log.info("Updated Firestore with refreshed token for user: {}", userId);

            // Return StorageInfo with new token
            return StorageInfo.builder()
                .type(StorageType.fromFirestoreValue(storage.type()))
                .connected(true)
                .accessToken(newAccessToken)  // Decrypted new token
                .refreshToken(refreshToken)   // Keep same refresh token
                .expiresAt(newExpiresAt.toDate().toInstant())
                .connectedAt(storage.connectedAt() != null
                    ? storage.connectedAt().toDate().toInstant()
                    : null)
                .defaultFolderId(storage.defaultFolderId())
                .build();

        } catch (Exception e) {
            log.error("Failed to refresh OAuth token for user {}: {}", userId, e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to refresh OAuth token: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user has storage connected
     *
     * @param userId Firebase user ID
     * @return true if storage is connected
     */
    public boolean isStorageConnected(String userId) {
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
    public void disconnectStorage(String userId) {
        log.info("Disconnecting storage for user: {}", userId);

        try {
            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(STORAGE_FIELD, null)
                    .get();

            log.info("Storage disconnected for user: {}", userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to disconnect storage: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to disconnect storage", e);
        }
    }

    /**
     * Update default folder ID for Google Drive
     *
     * @param userId   Firebase user ID
     * @param folderId Google Drive folder ID
     */
    public void updateDefaultFolder(String userId, String folderId) {
        log.info("Updating default folder for user {}: {}", userId, folderId);

        try {
            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(STORAGE_FIELD + ".defaultFolderId", folderId)
                    .get();

            log.info("Default folder updated successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to update default folder: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to update default folder", e);
        }
    }
}
