package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.RecipeRepository;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeStoreService {

    @Value("${recipe.store.enabled:false}")
    private boolean enabled = false;

    @Value("${recipe.store.timeout.lookup-ms:200}")
    private int lookupTimeoutMs;

    @Value("${recipe.store.timeout.save-ms:5000}")
    private int saveTimeoutMs;

    @Value("${recipe.store.timeout.count-ms:1000}")
    private int countTimeoutMs;

    private final RecipeRepository repository;

    public Optional<StoredRecipe> findStoredRecipeByHash(String contentHash) {
        if (contentHash == null || contentHash.isEmpty()) {
            log.debug("Content hash is null or empty, skipping cache lookup");
            return Optional.empty();
        }

        if (!enabled) {
            log.debug("Recipe store is disabled, skipping cache lookup for hash: {}", contentHash);
            return Optional.empty();
        }

        log.debug("Looking up stored recipe for hash: {}", contentHash);

        try {
            return repository.findByContentHash(contentHash)
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Error retrieving stored recipe for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                            return Optional.<StoredRecipe>empty();
                        }

                        if (result.isPresent()) {
                            log.debug("Found stored recipe for hash: {}", contentHash);
                        } else {
                            log.debug("No stored recipe found for hash: {}", contentHash);
                        }

                        return result;
                    })
                    .orTimeout(lookupTimeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        log.warn("Cache lookup timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                        return Optional.<StoredRecipe>empty();
                    })
                    .join();
        } catch (Exception e) {
            log.warn("Cache lookup failed for hash {}: {}", contentHash, e.getMessage());
            return Optional.empty();
        }
    }

    public void storeInvalidRecipe(String contentHash, String url) {
        storeRecipeWithHash(contentHash, url, null, false);
    }

    public void storeValidRecipe(String contentHash, String url, String recipeYaml) {
        storeRecipeWithHash(contentHash, url, recipeYaml, true);
    }

    void storeRecipeWithHash(String contentHash, String url, String recipeYaml, boolean isValid) {
        if (!enabled) {
            log.debug("Recipe store is disabled, skipping cache store for hash: {}", contentHash);
            return;
        }

        log.debug("Caching recipe for hash: {}", contentHash);

        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash(contentHash)
                .sourceUrl(url)
                .recipeYaml(recipeYaml)
                .isValid(isValid)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        try {
            repository.save(recipe)
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Error storing recipe for hash {}: {}", contentHash, throwable.getMessage(), throwable);
                        } else {
                            log.debug("Successfully stored recipe for hash: {}", contentHash);
                        }
                        return (Void) null;
                    })
                    .orTimeout(saveTimeoutMs, TimeUnit.MILLISECONDS)
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Cache save timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                        }
                        return (Void) null;
                    })
                    .join();
        } catch (Exception e) {
            log.warn("Cache save failed for hash {}: {}", contentHash, e.getMessage());
        }
    }

    public long getCacheSize() {
        if (!enabled) {
            log.debug("Recipe store is disabled, returning size 0");
            return 0L;
        }

        try {
            return repository.count()
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Error getting store size: {}", throwable.getMessage(), throwable);
                            return 0L;
                        }
                        return result;
                    })
                    .orTimeout(countTimeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        log.warn("Cache size check timed out or failed: {}", throwable.getMessage());
                        return 0L;
                    })
                    .join();
        } catch (Exception e) {
            log.warn("Cache size check failed: {}", e.getMessage());
            return 0L;
        }
    }
}