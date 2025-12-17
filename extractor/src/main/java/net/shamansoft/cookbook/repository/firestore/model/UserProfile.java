package net.shamansoft.cookbook.repository.firestore.model;

import lombok.Builder;

/**
 * Firestore entity for user profile.
 * Maps to the document structure in the users collection.
 */
@Builder
public record UserProfile(String userId, StorageEntity storage) {
}
