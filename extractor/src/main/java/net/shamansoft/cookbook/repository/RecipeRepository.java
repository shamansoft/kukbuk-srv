package net.shamansoft.cookbook.repository;

import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RecipeRepository {

    CompletableFuture<Optional<StoredRecipe>> findByContentHash(String contentHash);

    CompletableFuture<Void> save(StoredRecipe recipe);

    CompletableFuture<Boolean> existsByContentHash(String contentHash);

    CompletableFuture<Void> deleteByContentHash(String contentHash);

    CompletableFuture<Long> count();
}