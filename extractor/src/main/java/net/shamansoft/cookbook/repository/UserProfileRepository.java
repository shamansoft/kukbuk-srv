package net.shamansoft.cookbook.repository;

import net.shamansoft.cookbook.entitlement.UserTier;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for user profile data access.
 * Provides async operations for CRUD operations on user profiles.
 */
public interface UserProfileRepository {

    /**
     * Find user profile by userId
     *
     * @param userId Firebase UID
     * @return CompletableFuture containing Optional of UserProfile
     */
    CompletableFuture<Optional<UserProfile>> findByUserId(String userId);

    /**
     * Save user profile (create or update)
     *
     * @param profile UserProfile to save
     * @return CompletableFuture containing the saved UserProfile
     */
    CompletableFuture<UserProfile> save(UserProfile profile);

    /**
     * Update user profile with partial data
     * Useful for OAuth token updates without loading full profile
     *
     * @param userId  Firebase UID
     * @param updates Map of fields to update
     * @return CompletableFuture that completes when update is done
     */
    CompletableFuture<Void> update(String userId, Map<String, Object> updates);

    /**
     * Check if user profile exists
     *
     * @param userId Firebase UID
     * @return CompletableFuture containing true if profile exists, false otherwise
     */
    CompletableFuture<Boolean> existsByUserId(String userId);

    /**
     * Update only the tier field. Fails if the user document does not exist.
     *
     * @param userId Firebase UID of the target user
     * @param tier   New tier value to set
     */
    void updateTier(String userId, UserTier tier);
}
