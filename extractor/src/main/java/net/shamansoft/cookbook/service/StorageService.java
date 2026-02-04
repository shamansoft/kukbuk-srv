package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleAuthClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.client.GoogleDrive.Item;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.GoogleDriveException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing storage provider configuration in Firestore
 */
@Service
@Slf4j
public class StorageService {

    private static final String USERS_COLLECTION = "users";
    private static final String STORAGE_FIELD = "storage";
    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleAuthClient googleAuthClient;
    private final GoogleDrive googleDrive;
    @Value("${cookbook.drive.folder-name}")
    private String defaultFolderName;

    public StorageService(Firestore firestore,
                          TokenEncryptionService tokenEncryptionService,
                          GoogleAuthClient googleAuthClient,
                          GoogleDrive googleDrive) {
        this.firestore = firestore;
        this.tokenEncryptionService = tokenEncryptionService;
        this.googleAuthClient = googleAuthClient;
        this.googleDrive = googleDrive;
    }

    /**
     * Store Google Drive connection information.
     * Exchanges authorization code for tokens, creates/finds folder, and stores everything.
     *
     * @param userId            Firebase user ID
     * @param authorizationCode OAuth authorization code from mobile app
     * @param redirectUri       Redirect URI used in OAuth flow
     * @param folderName        Google Drive folder name (optional, uses default if blank)
     * @return FolderInfo with folder ID and name
     */
    public FolderInfo connectGoogleDrive(String userId, String authorizationCode, String redirectUri,
                                         String folderName) {
        log.info("Connecting Google Drive storage for user: {}", userId);

        try {
            // 1. Exchange authorization code for tokens
            GoogleAuthClient.TokenResponse tokens = googleAuthClient.exchangeAuthorizationCode(authorizationCode, redirectUri);

            // 2. Resolve folder name (use default if blank/null)
            String resolvedFolderName = resolveFolderName(folderName);
            log.info("Resolved folder name: '{}'", resolvedFolderName);

            // 3. Get or create folder on Google Drive
            Item folder = getOrCreateFolder(resolvedFolderName, tokens.accessToken());
            log.info("Using folder '{}' with ID: {}", folder.name(), folder.id());

            // 4. Store tokens and folder information (encrypted)
            connectGoogleDriveWithTokens(userId, tokens.accessToken(), tokens.refreshToken(),
                    tokens.expiresIn(), folder.id(), folder.name());

            return new FolderInfo(folder.id(), folder.name());

        } catch (GoogleDriveException e) {
            // Re-throw Google Drive exceptions as-is
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Re-throw OAuth exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to connect Google Drive for user {}: {}", userId, e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to connect Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve folder name: use default if null or blank.
     */
    private String resolveFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            log.debug("No folder name provided, using default: '{}'", defaultFolderName);
            return defaultFolderName;
        }
        return folderName.trim();
    }

    /**
     * Get existing folder or create new one on Google Drive.
     *
     * @param folderName  Folder name to search for/create
     * @param accessToken OAuth access token for Google Drive API
     * @return Folder Item with ID and name
     * @throws net.shamansoft.cookbook.exception.GoogleDriveException if Google Drive API fails
     */
    private Item getOrCreateFolder(String folderName, String accessToken) {
        try {
            // Search for existing folder in root directory
            java.util.Optional<Item> existingFolder =
                    googleDrive.getFolder(folderName, accessToken);

            if (existingFolder.isPresent()) {
                log.info("Found existing folder '{}' with ID: {}",
                        folderName, existingFolder.get().id());
                return existingFolder.get();
            }

            // Create new folder if not found
            log.info("Folder '{}' not found, creating new folder", folderName);
            Item newFolder =
                    googleDrive.createFolder(folderName, accessToken);
            log.info("Created folder '{}' with ID: {}", folderName, newFolder.id());
            return newFolder;

        } catch (Exception e) {
            log.error("Failed to get or create folder '{}': {}", folderName, e.getMessage(), e);
            throw new net.shamansoft.cookbook.exception.GoogleDriveException(
                    "Failed to access Google Drive folder: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method to store OAuth tokens and folder information.
     * Separated for backwards compatibility and testing.
     *
     * @param userId       Firebase user ID
     * @param accessToken  OAuth access token (will be encrypted)
     * @param refreshToken OAuth refresh token (will be encrypted)
     * @param expiresIn    Token expiration in seconds
     * @param folderId     Google Drive folder ID
     * @param folderName   Google Drive folder name
     */
    private void connectGoogleDriveWithTokens(String userId, String accessToken, String refreshToken,
                                              long expiresIn, String folderId, String folderName) {
        log.info("Storing Google Drive connection for user: {} with folder '{}' ({})",
                userId, folderName, folderId);

        try {

            StorageEntity storageEntity = StorageEntity.builder()
                    .type(StorageType.GOOGLE_DRIVE.getFirestoreValue())
                    .connected(true)
                    .accessToken(tokenEncryptionService.encrypt(accessToken))
                    .refreshToken(refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null)
                    .expiresAt(Timestamp.ofTimeSecondsAndNanos(
                            System.currentTimeMillis() / 1000 + expiresIn, 0))
                    .connectedAt(Timestamp.now())
                    .folderId(folderId)
                    .folderName(folderName)
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
     * Refresh access token using refresh token
     */
    private StorageInfo getFreshStorageInfo(String userId, StorageEntity storage) {
        if (storage.refreshToken() == null) {
            throw new StorageNotConnectedException(
                    "Access token expired and no refresh token available. Please reconnect storage."
            );
        }

        try {
            String refreshToken = tokenEncryptionService.decrypt(storage.refreshToken());

            log.info("Refreshing OAuth token for user: {}", userId);

            // Call GoogleAuthClient to refresh token
            GoogleAuthClient.RefreshTokenResponse response = googleAuthClient.refreshAccessToken(refreshToken);

            log.info("Successfully refreshed OAuth token for user: {}", userId);

            // Update Firestore with new token
            String encryptedAccessToken = tokenEncryptionService.encrypt(response.accessToken());

            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(
                            STORAGE_FIELD + ".accessToken", encryptedAccessToken,
                            STORAGE_FIELD + ".expiresAt", response.expiresAt()
                    )
                    .get();

            log.info("Updated Firestore with refreshed token for user: {}", userId);

            // Return StorageInfo with new token
            return StorageInfo.builder()
                    .type(StorageType.fromFirestoreValue(storage.type()))
                    .connected(true)
                    .accessToken(response.accessToken())  // Decrypted new token
                    .refreshToken(refreshToken)   // Keep same refresh token
                    .expiresAt(response.expiresAt().toDate().toInstant())
                    .connectedAt(storage.connectedAt() != null
                            ? storage.connectedAt().toDate().toInstant()
                            : null)
                    .folderId(storage.folderId())
                    .folderName(storage.folderName())
                    .build();

        } catch (Exception e) {
            log.error("Failed to refresh OAuth token for user {}: {}", userId, e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to refresh OAuth token: " + e.getMessage(), e);
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

            // Manual deserialization to handle Java record compatibility issues
            Map<String, Object> storageMap = (Map<String, Object>) doc.get("storage");

            if (storageMap == null) {
                throw new StorageNotConnectedException("No storage configuration found for user: " + userId);
            }

            // Deserialize StorageEntity from map
            StorageEntity storageEntity = StorageEntity.builder()
                    .type((String) storageMap.get("type"))
                    .connected(Boolean.TRUE.equals(storageMap.get("connected")))
                    .accessToken((String) storageMap.get("accessToken"))
                    .refreshToken((String) storageMap.get("refreshToken"))
                    .expiresAt((com.google.cloud.Timestamp) storageMap.get("expiresAt"))
                    .connectedAt((com.google.cloud.Timestamp) storageMap.get("connectedAt"))
                    .folderId((String) storageMap.get("folderId"))
                    .folderName((String) storageMap.get("folderName"))
                    .build();

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
            if (googleAuthClient.isTokenExpired(storageEntity.expiresAt())) {
                log.info("OAuth token expired or expiring soon for user: {}, refreshing...", userId);
                return getFreshStorageInfo(userId, storageEntity);
            }

            // 5. Token is valid, decrypt and return
            log.debug("Using cached OAuth token for user: {}", userId);
            return storageEntity.toInfo(tokenEncryptionService);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to fetch user profile: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to fetch user profile", e);
        } catch (StorageNotConnectedException e) {
            // Re-throw domain exception as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while retrieving storage info for user {}: {}",
                    userId, e.getMessage(), e);
            throw new DatabaseUnavailableException(
                    "Unexpected error while retrieving storage info: " + e.getMessage(), e);
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
                    .update(STORAGE_FIELD + ".folderId", folderId)
                    .get();

            log.info("Default folder updated successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to update default folder: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to update default folder", e);
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
     * Result of connecting Google Drive with folder information.
     *
     * @param folderId   Google Drive folder ID
     * @param folderName Human-readable folder name
     */
    public record FolderInfo(String folderId, String folderName) {
    }
}
