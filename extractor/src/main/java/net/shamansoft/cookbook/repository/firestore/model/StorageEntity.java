package net.shamansoft.cookbook.repository.firestore.model;

import com.google.cloud.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.security.TokenEncryptionService;

import java.util.HashMap;
import java.util.Map;

/**
 * Firestore entity for storage information.
 * This represents the raw data as stored in Firestore, including encrypted tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageEntity {
    private String type;              // Firestore value: "googleDrive", "dropbox", etc.
    private boolean connected;
    private String accessToken;       // Encrypted access token
    private String refreshToken;      // Encrypted refresh token, may be null
    private Timestamp expiresAt;
    private Timestamp connectedAt;
    private String defaultFolderId;   // For Google Drive folder ID

    public Map<String, Object> toMap() {
        Map<String, Object> storage = new HashMap<>();
        storage.put("type", StorageType.GOOGLE_DRIVE.getFirestoreValue());
        storage.put("connected", connected);
        storage.put("accessToken", accessToken);
        if(refreshToken != null) {
            storage.put("refreshToken", refreshToken);
        }
        storage.put("expiresAt", expiresAt);
        storage.put("connectedAt", connectedAt);
        if (defaultFolderId != null) {
            storage.put("defaultFolderId", defaultFolderId);
        }
        return storage;
    }

    public StorageInfo toDto(TokenEncryptionService tokenEncryptionService) {
        try {
            // Decrypt tokens
            String accessToken = tokenEncryptionService.decrypt(this.getAccessToken());
            String refreshToken = this.getRefreshToken() != null
                    ? tokenEncryptionService.decrypt(this.getRefreshToken())
                    : null;

            // Parse storage type
            StorageType type = StorageType.fromFirestoreValue(this.getType());

            return StorageInfo.builder()
                    .type(type)
                    .connected(true)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    // Convert Firestore Timestamp to Java Instant
                    .expiresAt(this.getExpiresAt() != null
                            ? this.getExpiresAt().toDate().toInstant()
                            : null)
                    .connectedAt(this.getConnectedAt() != null
                            ? this.getConnectedAt().toDate().toInstant()
                            : null)
                    .defaultFolderId(this.getDefaultFolderId())
                    .build();
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Failed to decrypt tokens or map storage data", e);
        }
    }
}
