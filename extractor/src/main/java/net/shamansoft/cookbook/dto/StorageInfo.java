package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Domain object for storage connection information.
 * Uses standard Java types (not Firestore-specific types).
 */
@Data
@Builder
public class StorageInfo {
    private StorageType type;         // Enum for type safety
    private boolean connected;
    private String accessToken;       // Decrypted access token
    private String refreshToken;      // Decrypted refresh token, may be null
    private Instant expiresAt;        // Token expiration time
    private Instant connectedAt;      // When storage was connected
    private String defaultFolderId;   // For Google Drive folder ID
}
