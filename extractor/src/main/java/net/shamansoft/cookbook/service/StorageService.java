package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive.Item;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.GoogleDriveException;
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

import javax.annotation.PostConstruct;
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
    private final net.shamansoft.cookbook.client.GoogleDrive googleDrive;

    @Value("${cookbook.drive.oauth-id}")
    private String googleClientId;

    @Value("${cookbook.drive.oauth-secret}")
    private String googleClientSecret;

    @Value("${cookbook.drive.folder-name}")
    private String defaultFolderName;

    private static final String USERS_COLLECTION = "users";
    private static final String STORAGE_FIELD = "storage";

    // Refresh token if it expires within this window (5 minutes)
    private static final long TOKEN_BUFFER_SECONDS = 300;

    public StorageService(Firestore firestore,
                         TokenEncryptionService tokenEncryptionService,
                          WebClient.Builder webClientBuilder,
                          net.shamansoft.cookbook.client.GoogleDrive googleDrive) {
        this.firestore = firestore;
        this.tokenEncryptionService = tokenEncryptionService;
        this.webClient = webClientBuilder.build();
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
            TokenExchangeResult tokens = exchangeAuthorizationCodeForTokens(authorizationCode, redirectUri);

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

    @PostConstruct
    public void init() {
        log.info("StorageService initialized with Google OAuth client_id: {} / {}", googleClientId, googleClientSecret.substring(googleClientSecret.length() - 4));
    }

    /**
     * Exchange authorization code for OAuth tokens.
     * Calls Google's OAuth token endpoint with the authorization code.
     *
     * @param authorizationCode Authorization code from OAuth flow
     * @param redirectUri       Redirect URI that was used in the OAuth flow
     * @return TokenExchangeResult with access token, refresh token, and expiration
     * @throws IllegalArgumentException     if authorization code is invalid
     * @throws DatabaseUnavailableException if OAuth service is unavailable
     */
    private TokenExchangeResult exchangeAuthorizationCodeForTokens(String authorizationCode, String redirectUri) {
        log.info("Exchanging authorization code for OAuth tokens");
        log.info("OAuth exchange - client_id: {}, redirect_uri: {}", googleClientId, redirectUri);

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authorizationCode);
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

            Map<String, Object> response = webClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(params))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalArgumentException("Invalid response from Google OAuth: " + response);
            }

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (refreshToken == null) {
                throw new IllegalStateException(
                        "No refresh token received. Ensure OAuth consent screen requests offline access."
                );
            }

            if (expiresIn == null) {
                expiresIn = 3600; // Default to 1 hour
            }

            log.info("Successfully exchanged authorization code for tokens, expires in {}s", expiresIn);

            return new TokenExchangeResult(accessToken, refreshToken, expiresIn.longValue());

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("OAuth token exchange failed with status {}: {}", e.getStatusCode(), errorBody);

            // Try to extract error from Google's JSON response
            String errorMessage = "Invalid authorization code";
            try {
                if (errorBody.contains("error")) {
                    // Google returns {"error": "invalid_grant", "error_description": "..."}
                    errorMessage = errorBody;
                }
            } catch (Exception parseEx) {
                // Ignore parsing errors, use default message
            }

            throw new IllegalArgumentException(
                    "OAuth token exchange failed: " + errorMessage +
                            ". Check that client_id, client_secret, and redirect_uri match your OAuth configuration."
            );
        } catch (Exception e) {
            log.error("Failed to exchange authorization code: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("OAuth token exchange failed: " + e.getMessage(), e);
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
     * @param userId            Firebase user ID
     * @param accessToken       OAuth access token (will be encrypted)
     * @param refreshToken      OAuth refresh token (will be encrypted)
     * @param expiresIn         Token expiration in seconds
     * @param defaultFolderId   Google Drive folder ID
     * @param defaultFolderName Google Drive folder name
     */
    private void connectGoogleDriveWithTokens(String userId, String accessToken, String refreshToken,
                                              long expiresIn, String defaultFolderId, String defaultFolderName) {
        log.info("Storing Google Drive connection for user: {} with folder '{}' ({})",
                userId, defaultFolderName, defaultFolderId);

        try {

            StorageEntity storageEntity = StorageEntity.builder()
                    .type(StorageType.GOOGLE_DRIVE.getFirestoreValue())
                    .connected(true)
                    .accessToken(tokenEncryptionService.encrypt(accessToken))
                    .refreshToken(refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null)
                    .expiresAt(Timestamp.ofTimeSecondsAndNanos(
                            System.currentTimeMillis() / 1000 + expiresIn, 0))
                    .connectedAt(Timestamp.now())
                    .folderId(defaultFolderId)
                    .folderName(defaultFolderName)
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
                    .defaultFolderId(storage.folderId())
                    .defaultFolderName(storage.folderName())
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

    /**
     * Result of exchanging authorization code for OAuth tokens.
     *
     * @param accessToken  OAuth access token
     * @param refreshToken OAuth refresh token
     * @param expiresIn    Token expiration in seconds
     */
    private record TokenExchangeResult(String accessToken, String refreshToken, long expiresIn) {
    }
}
