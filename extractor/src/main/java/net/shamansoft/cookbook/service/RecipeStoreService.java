package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.model.Recipe;
import net.shamansoft.cookbook.repository.RecipeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "recipe.store.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RecipeStoreService {

    private final RecipeRepository repository;
    private final ContentHashService contentHashService;

    public CompletableFuture<Optional<Recipe>> findCachedRecipe(String url) {
        try {
            String contentHash = contentHashService.generateContentHash(url);
            return findCachedRecipeByHash(contentHash);
        } catch (Exception e) {
            log.error("Error generating content hash for URL {}: {}", url, e.getMessage(), e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public CompletableFuture<Optional<Recipe>> findCachedRecipeByHash(String contentHash) {
        log.debug("Looking up stored recipe for hash: {}", contentHash);
        
        return repository.findByContentHash(contentHash)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error retrieving stored recipe for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                        return Optional.<Recipe>empty();
                    }
                    
                    if (result.isPresent()) {
                        log.debug("Found stored recipe for hash: {}", contentHash);
                    } else {
                        log.debug("No stored recipe found for hash: {}", contentHash);
                    }
                    
                    return result;
                })
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    log.warn("Cache lookup timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                    return Optional.<Recipe>empty();
                });
    }

    public CompletableFuture<Void> storeRecipe(String url, String recipeYaml) {
        try {
            String contentHash = contentHashService.generateContentHash(url);
            return storeRecipeWithHash(contentHash, url, recipeYaml);
        } catch (Exception e) {
            log.error("Error generating content hash for caching URL {}: {}", url, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> storeRecipeWithHash(String contentHash, String url, String recipeYaml) {
        log.debug("Caching recipe for hash: {}", contentHash);
        
        Recipe recipe = Recipe.builder()
                .contentHash(contentHash)
                .sourceUrl(url)
                .recipeYaml(recipeYaml)
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(0L)
                .build();

        return repository.save(recipe)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error caching recipe for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                    } else {
                        log.debug("Successfully stored recipe for hash: {}", contentHash);
                    }
                    return (Void) null;
                })
                .orTimeout(5, TimeUnit.SECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("Cache save timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                    }
                    return (Void) null;
                });
    }

    public CompletableFuture<Boolean> isCached(String url) {
        try {
            String contentHash = contentHashService.generateContentHash(url);
            return isCachedByHash(contentHash);
        } catch (Exception e) {
            log.error("Error generating content hash for existence check URL {}: {}", url, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> isCachedByHash(String contentHash) {
        return repository.existsByContentHash(contentHash)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error checking recipe existence for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                        return false;
                    }
                    return result;
                })
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    log.warn("Cache existence check timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Void> evictFromCache(String url) {
        try {
            String contentHash = contentHashService.generateContentHash(url);
            return evictFromCacheByHash(contentHash);
        } catch (Exception e) {
            log.error("Error generating content hash for eviction URL {}: {}", url, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> evictFromCacheByHash(String contentHash) {
        log.debug("Evicting stored recipe for hash: {}", contentHash);
        
        return repository.deleteByContentHash(contentHash)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error evicting stored recipe for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                    } else {
                        log.debug("Successfully evicted stored recipe for hash: {}", contentHash);
                    }
                    return (Void) null;
                })
                .orTimeout(5, TimeUnit.SECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("Cache eviction timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                    }
                    return (Void) null;
                });
    }

    public CompletableFuture<Long> getCacheSize() {
        return repository.count()
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error getting store size: {}", throwable.getMessage(), throwable);
                        return 0L;
                    }
                    return result;
                })
                .orTimeout(1, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.warn("Cache size check timed out or failed: {}", throwable.getMessage());
                    return 0L;
                });
    }
}