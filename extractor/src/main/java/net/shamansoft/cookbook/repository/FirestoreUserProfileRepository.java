package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Firestore implementation of UserProfileRepository.
 * Provides async operations for user profile data access.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class FirestoreUserProfileRepository implements UserProfileRepository {

    private static final String COLLECTION_NAME = "users";
    private static final Executor executor = Executors.newCachedThreadPool();
    private final Firestore firestore;
    private final Transformer transformer;

    @Override
    public CompletableFuture<Optional<UserProfile>> findByUserId(String userId) {
        log.debug("Retrieving user profile for userId: {}", userId);
        long startTime = System.currentTimeMillis();

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(userId);
        ApiFuture<DocumentSnapshot> future = docRef.get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot documentSnapshot = future.get();
                long duration = System.currentTimeMillis() - startTime;

                if (!documentSnapshot.exists()) {
                    log.debug("User profile not found for userId: {} (retrieved in {}ms)", userId, duration);
                    return Optional.empty();
                }

                UserProfile profile = transformer.documentToUserProfile(documentSnapshot);
                log.debug("Retrieved user profile for userId: {} (retrieved in {}ms)", userId, duration);

                return Optional.of(profile);
            } catch (Exception e) {
                log.error("Error retrieving user profile for userId {}: {}", userId, e.getMessage(), e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<UserProfile> save(UserProfile profile) {
        String userId = profile.userId() != null ? profile.userId() : profile.uid();
        log.debug("Saving user profile for userId: {}", userId);

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(userId);
        Map<String, Object> data = profile.toMap();
        ApiFuture<WriteResult> future = docRef.set(data);

        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully saved user profile for userId: {}", userId);
                return profile;
            } catch (Exception e) {
                log.error("Error saving user profile for userId {}: {}", userId, e.getMessage(), e);
                throw new CompletionException("Failed to save user profile", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> update(String userId, Map<String, Object> updates) {
        log.debug("Updating user profile for userId: {} with {} fields", userId, updates.size());

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(userId);
        ApiFuture<WriteResult> future = docRef.update(updates);

        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully updated user profile for userId: {}", userId);
                return null;
            } catch (Exception e) {
                log.error("Error updating user profile for userId {}: {}", userId, e.getMessage(), e);
                throw new CompletionException("Failed to update user profile", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> existsByUserId(String userId) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(userId);
        ApiFuture<DocumentSnapshot> future = docRef.get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot documentSnapshot = future.get();
                return documentSnapshot.exists();
            } catch (Exception e) {
                log.error("Error checking existence for userId {}: {}", userId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }
}
