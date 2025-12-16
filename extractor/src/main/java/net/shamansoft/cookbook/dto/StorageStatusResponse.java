package net.shamansoft.cookbook.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for storage connection status.
 * Does NOT include sensitive tokens - only metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatusResponse {

    private boolean connected;
    private String storageType;     // "googleDrive" - matches Firestore value

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant connectedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant expiresAt;

    private String defaultFolderId;

    /**
     * Factory method for not connected state
     */
    public static StorageStatusResponse notConnected() {
        return StorageStatusResponse.builder()
                .connected(false)
                .storageType(null)
                .connectedAt(null)
                .expiresAt(null)
                .defaultFolderId(null)
                .build();
    }

    /**
     * Factory method from StorageInfo domain object
     */
    public static StorageStatusResponse fromStorageInfo(StorageInfo info) {
        return StorageStatusResponse.builder()
                .connected(info.isConnected())
                .storageType(info.getType().getFirestoreValue())
                .connectedAt(info.getConnectedAt())
                .expiresAt(info.getExpiresAt())
                .defaultFolderId(info.getDefaultFolderId())
                .build();
    }
}
