package net.shamansoft.cookbook.repository.firestore.model;

import com.google.cloud.Timestamp;
import lombok.Builder;

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
        StorageEntity storage) {
}
