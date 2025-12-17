package net.shamansoft.cookbook.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Domain object for storage connection information.
 * Uses standard Java types (not Firestore-specific types).
 */
@Builder
public record StorageInfo(StorageType type,
                          boolean connected,
                          String accessToken,
                          String refreshToken,
                          Instant expiresAt,
                          Instant connectedAt,
                          String defaultFolderId) {
}
