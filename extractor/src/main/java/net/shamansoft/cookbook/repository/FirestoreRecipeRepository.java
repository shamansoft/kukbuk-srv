package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.model.Recipe;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FirestoreRecipeRepository implements RecipeRepository {

    private final Firestore firestore;
    private static final String COLLECTION_NAME = "recipe_store";
    private static final Executor executor = Executors.newCachedThreadPool();
    private final Transformer transformer;

    @Override
    public CompletableFuture<Optional<Recipe>> findByContentHash(String contentHash) {
        log.debug("Retrieving recipe for hash: {}", contentHash);
        long startTime = System.currentTimeMillis();
        
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(contentHash);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot documentSnapshot = future.get();
                long duration = System.currentTimeMillis() - startTime;
                
                if (!documentSnapshot.exists()) {
                    log.debug("Recipe not found for hash: {} (retrieved in {}ms)", contentHash, duration);
                    return Optional.<Recipe>empty();
                }
                
                Recipe recipe = transformer.documentToRecipeCache(documentSnapshot);
                log.debug("Retrieved recipe for hash: {} (retrieved in {}ms)", contentHash, duration);
                
                return Optional.of(recipe);
            } catch (Exception e) {
                log.error("Error retrieving recipe for hash {}: {}", contentHash, e.getMessage(), e);
                return Optional.<Recipe>empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(Recipe recipe) {
        log.debug("Saving recipe for hash: {}", recipe.getContentHash());
        
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(recipe.getContentHash());
        ApiFuture<WriteResult> future = docRef.set(recipe);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully saved recipe for hash: {}", recipe.getContentHash());
                return null;
            } catch (Exception e) {
                log.error("Error saving recipe for hash {}: {}", recipe.getContentHash(), e.getMessage(), e);
                throw new CompletionException("Failed to save recipe", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> existsByContentHash(String contentHash) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(contentHash);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot documentSnapshot = future.get();
                return documentSnapshot.exists();
            } catch (Exception e) {
                log.error("Error checking existence for hash {}: {}", contentHash, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteByContentHash(String contentHash) {
        log.debug("Deleting recipe for hash: {}", contentHash);
        
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(contentHash);
        ApiFuture<WriteResult> future = docRef.delete();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully deleted recipe for hash: {}", contentHash);
                return null;
            } catch (Exception e) {
                log.error("Error deleting recipe for hash {}: {}", contentHash, e.getMessage(), e);
                throw new CompletionException("Failed to delete recipe", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> count() {
        ApiFuture<com.google.cloud.firestore.QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                com.google.cloud.firestore.QuerySnapshot querySnapshot = future.get();
                return (long) querySnapshot.size();
            } catch (Exception e) {
                log.error("Error counting recipe documents: {}", e.getMessage(), e);
                return 0L;
            }
        }, executor);
    }
}