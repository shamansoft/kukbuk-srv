package net.shamansoft.cookbook.repository.firestore.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import net.shamansoft.cookbook.dto.UserProfileResponseDto;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Firestore entity for user profile.
 * Maps to the document structure in the users collection.
 */
@Builder
public record UserProfile(
        String uid,           // Firebase UID (primary identifier)
        String userId,        // Legacy field (same as uid)
        String email,
        String displayName,
        Timestamp createdAt,
        Timestamp updatedAt,
        String googleOAuthToken,     // Encrypted OAuth access token
        String googleRefreshToken,   // Encrypted OAuth refresh token
        Timestamp tokenExpiresAt,    // OAuth token expiration time
        StorageEntity storage) {

    /**
     * Create UserProfile from Firestore Map data
     */
    public static Optional<UserProfile> fromMap(Map<String, Object> data) {
        if (data == null) {
            return Optional.empty();
        }

        // Extract storage entity if present
        StorageEntity storage = null;
        Object storageData = data.get("storage");
        if (storageData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> storageMap = (Map<String, Object>) storageData;
            storage = StorageEntity.builder()
                    .type((String) storageMap.get("type"))
                    .connected(Boolean.TRUE.equals(storageMap.get("connected")))
                    .accessToken((String) storageMap.get("accessToken"))
                    .refreshToken((String) storageMap.get("refreshToken"))
                    .expiresAt((Timestamp) storageMap.get("expiresAt"))
                    .connectedAt((Timestamp) storageMap.get("connectedAt"))
                    .folderId((String) storageMap.get("folderId"))
                    .folderName((String) storageMap.get("folderName"))
                    .build();
        }

        return Optional.of(UserProfile.builder()
                .uid((String) data.get("uid"))
                .userId((String) data.get("userId"))
                .email((String) data.get("email"))
                .displayName((String) data.get("displayName"))
                .createdAt((Timestamp) data.get("createdAt"))
                .updatedAt((Timestamp) data.get("updatedAt"))
                .googleOAuthToken((String) data.get("googleOAuthToken"))
                .googleRefreshToken((String) data.get("googleRefreshToken"))
                .tokenExpiresAt((Timestamp) data.get("tokenExpiresAt"))
                .storage(storage)
                .build());
    }

    /**
     * Convert UserProfile to Firestore Map format for storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();

        // Always include core fields
        if (uid != null) {
            data.put("uid", uid);
        }
        if (userId != null) {
            data.put("userId", userId);
        }
        if (email != null) {
            data.put("email", email);
        }
        if (displayName != null) {
            data.put("displayName", displayName);
        }
        if (createdAt != null) {
            data.put("createdAt", createdAt);
        }
        if (updatedAt != null) {
            data.put("updatedAt", updatedAt);
        }

        // OAuth token fields (optional)
        if (googleOAuthToken != null) {
            data.put("googleOAuthToken", googleOAuthToken);
        }
        if (googleRefreshToken != null) {
            data.put("googleRefreshToken", googleRefreshToken);
        }
        if (tokenExpiresAt != null) {
            data.put("tokenExpiresAt", tokenExpiresAt);
        }

        // Storage entity (nested object)
        if (storage != null) {
            data.put("storage", storage.toMap());
        }

        return data;
    }

    public UserProfileResponseDto toDto() {
        return UserProfileResponseDto.builder()
                .userId(this.userId)
                .email(this.email)
                .createdAt(this.createdAt != null
                        ? java.time.Instant.ofEpochSecond(
                        this.createdAt.getSeconds(),
                        this.createdAt.getNanos())
                        : null)
                .storage(this.storage == null ? null : this.storage.toDto())
                .build();
    }
}
