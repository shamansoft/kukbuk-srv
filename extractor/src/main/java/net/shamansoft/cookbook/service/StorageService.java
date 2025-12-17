package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

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
     * Get storage information for a user
     *
     * @param userId Firebase user ID
     * @return StorageInfo domain object with decrypted tokens
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

            if (userProfile == null || userProfile.getStorage() == null) {
                throw new StorageNotConnectedException("No storage configuration found for user: " + userId);
            }

            StorageEntity storageEntity = userProfile.getStorage();

            // 4. Domain logic validation
            if (!storageEntity.isConnected()) {
                throw new StorageNotConnectedException(
                    "Storage not connected. Please connect Google Drive first."
                );
            }

            if (storageEntity.getAccessToken() == null) {
                throw new StorageNotConnectedException(
                    "Storage connected but no access token found for user: " + userId
                );
            }

            // 5. Decrypt and map to domain object
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
     * @param userId Firebase user ID
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
