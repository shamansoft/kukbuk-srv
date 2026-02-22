package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.RecipeRepository;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeStoreService {

    private final RecipeRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${recipe.store.enabled:false}")
    private boolean enabled = false;
    @Value("${recipe.store.timeout.lookup-ms:200}")
    private int lookupTimeoutMs;
    @Value("${recipe.store.timeout.save-ms:5000}")
    private int saveTimeoutMs;
    @Value("${recipe.store.timeout.count-ms:1000}")
    private int countTimeoutMs;

    /**
     * Result of a cache lookup. Either a valid list of recipes or an invalid (non-recipe) marker.
     */
    public record CachedRecipes(boolean valid, List<Recipe> recipes) {
        public static CachedRecipes valid(List<Recipe> recipes) {
            return new CachedRecipes(true, recipes);
        }

        public static CachedRecipes invalid() {
            return new CachedRecipes(false, List.of());
        }
    }

    /**
     * Looks up cached recipes by content hash.
     *
     * @return present with {@link CachedRecipes} if the URL was previously processed,
     *         empty if not cached (triggers fresh extraction)
     */
    public Optional<CachedRecipes> findCachedRecipes(String contentHash) {
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
                            return Optional.<CachedRecipes>empty();
                        }

                        if (result.isEmpty()) {
                            log.debug("No stored recipe found for hash: {}", contentHash);
                            return Optional.<CachedRecipes>empty();
                        }

                        StoredRecipe stored = result.get();
                        log.debug("Found stored recipe for hash: {}", contentHash);
                        return Optional.of(toCachedRecipes(stored));
                    })
                    .orTimeout(lookupTimeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        log.warn("Cache lookup timed out or failed for hash {}: {}", contentHash, throwable.getMessage());
                        return Optional.empty();
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

    /**
     * Serializes and caches a list of recipes as JSON.
     */
    public void storeValidRecipes(String contentHash, String url, List<Recipe> recipes) {
        String json = serializeRecipes(recipes);
        if (json != null) {
            storeRecipeWithHash(contentHash, url, json, true);
        }
    }

    void storeRecipeWithHash(String contentHash, String url, String recipesJson, boolean isValid) {
        if (!enabled) {
            log.debug("Recipe store is disabled, skipping cache store for hash: {}", contentHash);
            return;
        }

        log.debug("Caching recipe for hash: {}", contentHash);

        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash(contentHash)
                .sourceUrl(url)
                .recipesJson(recipesJson)
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

    private CachedRecipes toCachedRecipes(StoredRecipe stored) {
        if (!stored.isValid()) {
            return CachedRecipes.invalid();
        }
        List<Recipe> recipes = deserializeRecipes(stored.getRecipesJson());
        return CachedRecipes.valid(recipes);
    }

    private String serializeRecipes(List<Recipe> recipes) {
        try {
            return objectMapper.writeValueAsString(recipes);
        } catch (Exception e) {
            log.error("Failed to serialize recipes to JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<Recipe> deserializeRecipes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, Recipe[].class));
        } catch (Exception e) {
            log.error("Failed to deserialize recipes from JSON: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
