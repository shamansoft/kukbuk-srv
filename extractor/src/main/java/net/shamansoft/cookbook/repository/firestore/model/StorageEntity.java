package net.shamansoft.cookbook.repository.firestore.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import net.shamansoft.cookbook.dto.StorageDto;
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
@Builder
public record StorageEntity(String type,
                            boolean connected,
                            String accessToken,
                            String refreshToken,
                            Timestamp expiresAt,
                            Timestamp connectedAt,
                            String folderId,
                            String folderName) {

    public Map<String, Object> toMap() {
        Map<String, Object> storage = new HashMap<>();
        storage.put("type", StorageType.GOOGLE_DRIVE.getFirestoreValue());
        storage.put("connected", connected);
        storage.put("accessToken", accessToken);
        if (refreshToken != null) {
            storage.put("refreshToken", refreshToken);
        }
        storage.put("expiresAt", expiresAt);
        storage.put("connectedAt", connectedAt);
        if (folderId != null) {
            storage.put("folderId", folderId);
        }
        if (folderName != null) {
            storage.put("folderName", folderName);
        }
        return storage;
    }

    public StorageDto toDto() {
        return StorageDto.builder()
                .type(this.type)
                .folderId(this.folderId)
                .folderName(this.folderName)
                .connectedAt(this.connectedAt != null
                        ? this.connectedAt.toDate().toInstant()
                        : null)
                .build();
    }

    public StorageInfo toInfo(TokenEncryptionService tokenEncryptionService) {
        try {
            // Decrypt tokens
            String accessToken = tokenEncryptionService.decrypt(this.accessToken());
            String refreshToken = this.refreshToken != null
                    ? tokenEncryptionService.decrypt(this.refreshToken)
                    : null;

            // Parse storage type
            StorageType type = StorageType.fromFirestoreValue(this.type);

            return StorageInfo.builder()
                    .type(type)
                    .connected(true)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    // Convert Firestore Timestamp to Java Instant
                    .expiresAt(this.expiresAt != null
                            ? this.expiresAt.toDate().toInstant()
                            : null)
                    .connectedAt(this.connectedAt != null
                            ? this.connectedAt.toDate().toInstant()
                            : null)
                    .folderId(this.folderId)
                    .folderName(this.folderName)
                    .build();
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Failed to decrypt tokens or map storage data", e);
        }
    }
}
