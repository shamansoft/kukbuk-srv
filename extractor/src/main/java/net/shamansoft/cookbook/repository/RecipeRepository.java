package net.shamansoft.cookbook.repository;

import net.shamansoft.cookbook.model.Recipe;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RecipeRepository {
    
    CompletableFuture<Optional<Recipe>> findByContentHash(String contentHash);
    
    CompletableFuture<Void> save(Recipe recipe);
    
    CompletableFuture<Boolean> existsByContentHash(String contentHash);
    
    CompletableFuture<Void> deleteByContentHash(String contentHash);
    
    CompletableFuture<Long> count();
}