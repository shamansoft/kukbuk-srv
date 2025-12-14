package net.shamansoft.cookbook.repository.firestore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Firestore entity for user profile.
 * Maps to the document structure in the users collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private StorageEntity storage;
}
