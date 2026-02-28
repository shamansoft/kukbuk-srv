package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.repository.RecipeRepository;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeStoreServiceTest {

    @Mock
    private RecipeRepository repository;

    private ObjectMapper objectMapper;
    private RecipeStoreService recipeStoreService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        recipeStoreService = new RecipeStoreService(repository, objectMapper);

        // Leave enabled=false (default) unless individual test sets it
        ReflectionTestUtils.setField(recipeStoreService, "lookupTimeoutMs", 500);
        ReflectionTestUtils.setField(recipeStoreService, "saveTimeoutMs", 500);
        ReflectionTestUtils.setField(recipeStoreService, "countTimeoutMs", 500);
    }

    // ---- findCachedRecipes --------------------------------------------------

    @Test
    @DisplayName("findCachedRecipes: returns empty when disabled")
    void findCached_returnsEmptyWhenDisabled() {
        // enabled is false by default
        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("hash-123");

        assertThat(result).isEmpty();
        verify(repository, never()).findByContentHash(any());
    }

    @Test
    @DisplayName("findCachedRecipes: returns empty for null hash")
    void findCached_returnsEmptyForNullHash() {
        enableStore();
        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes(null);

        assertThat(result).isEmpty();
        verify(repository, never()).findByContentHash(any());
    }

    @Test
    @DisplayName("findCachedRecipes: returns empty for blank hash")
    void findCached_returnsEmptyForBlankHash() {
        enableStore();
        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("   ");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findCachedRecipes: returns empty when repository has no entry")
    void findCached_returnsEmptyOnCacheMiss() {
        enableStore();
        when(repository.findByContentHash("hash-miss"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("hash-miss");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findCachedRecipes: returns valid CachedRecipes for valid stored recipe")
    void findCached_returnsValidRecipesOnHit() throws Exception {
        enableStore();
        Recipe recipe = createTestRecipe("Pasta");
        String json = objectMapper.writeValueAsString(List.of(recipe));

        StoredRecipe stored = StoredRecipe.builder()
                .contentHash("hash-valid")
                .sourceUrl("https://example.com")
                .recipesJson(json)
                .isValid(true)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        when(repository.findByContentHash("hash-valid"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(stored)));

        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("hash-valid");

        assertThat(result).isPresent();
        assertThat(result.get().valid()).isTrue();
        assertThat(result.get().recipes()).hasSize(1);
    }

    @Test
    @DisplayName("findCachedRecipes: returns invalid CachedRecipes for invalid stored recipe")
    void findCached_returnsInvalidMarkerOnInvalidHit() {
        enableStore();
        StoredRecipe stored = StoredRecipe.builder()
                .contentHash("hash-invalid")
                .sourceUrl("https://example.com")
                .recipesJson(null)
                .isValid(false)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        when(repository.findByContentHash("hash-invalid"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(stored)));

        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("hash-invalid");

        assertThat(result).isPresent();
        assertThat(result.get().valid()).isFalse();
        assertThat(result.get().recipes()).isEmpty();
    }

    @Test
    @DisplayName("findCachedRecipes: returns empty when repository throws")
    void findCached_returnsEmptyOnRepositoryError() {
        enableStore();
        CompletableFuture<Optional<StoredRecipe>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Firestore error"));
        when(repository.findByContentHash("hash-err")).thenReturn(failedFuture);

        Optional<RecipeStoreService.CachedRecipes> result =
                recipeStoreService.findCachedRecipes("hash-err");

        assertThat(result).isEmpty();
    }

    // ---- storeValidRecipes --------------------------------------------------

    @Test
    @DisplayName("storeValidRecipes: skips when disabled")
    void storeValid_skipsWhenDisabled() {
        recipeStoreService.storeValidRecipes("hash", "url", List.of(createTestRecipe("R")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("storeValidRecipes: saves serialized recipe JSON when enabled")
    void storeValid_savesWhenEnabled() {
        enableStore();
        when(repository.save(any(StoredRecipe.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        recipeStoreService.storeValidRecipes("hash-store", "https://example.com",
                List.of(createTestRecipe("Cake")));

        verify(repository).save(any(StoredRecipe.class));
    }

    // ---- storeInvalidRecipe -------------------------------------------------

    @Test
    @DisplayName("storeInvalidRecipe: skips when disabled")
    void storeInvalid_skipsWhenDisabled() {
        recipeStoreService.storeInvalidRecipe("hash", "url");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("storeInvalidRecipe: saves invalid marker when enabled")
    void storeInvalid_savesMarkerWhenEnabled() {
        enableStore();
        when(repository.save(any(StoredRecipe.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        recipeStoreService.storeInvalidRecipe("hash-inv", "https://example.com");

        verify(repository).save(any(StoredRecipe.class));
    }

    // ---- getCacheSize -------------------------------------------------------

    @Test
    @DisplayName("getCacheSize: returns 0 when disabled")
    void getCacheSize_returnsZeroWhenDisabled() {
        long size = recipeStoreService.getCacheSize();

        assertThat(size).isEqualTo(0L);
        verify(repository, never()).count();
    }

    @Test
    @DisplayName("getCacheSize: returns count from repository when enabled")
    void getCacheSize_returnsRepositoryCount() {
        enableStore();
        when(repository.count()).thenReturn(CompletableFuture.completedFuture(42L));

        long size = recipeStoreService.getCacheSize();

        assertThat(size).isEqualTo(42L);
    }

    @Test
    @DisplayName("getCacheSize: returns 0 when repository throws")
    void getCacheSize_returnsZeroOnError() {
        enableStore();
        CompletableFuture<Long> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Firestore error"));
        when(repository.count()).thenReturn(failed);

        long size = recipeStoreService.getCacheSize();

        assertThat(size).isEqualTo(0L);
    }

    // ---- CachedRecipes record -----------------------------------------------

    @Test
    @DisplayName("CachedRecipes.valid wraps recipe list")
    void cachedRecipes_validFactory() {
        List<Recipe> recipes = List.of(createTestRecipe("R"));
        RecipeStoreService.CachedRecipes cached = RecipeStoreService.CachedRecipes.valid(recipes);

        assertThat(cached.valid()).isTrue();
        assertThat(cached.recipes()).hasSize(1);
    }

    @Test
    @DisplayName("CachedRecipes.invalid returns empty list")
    void cachedRecipes_invalidFactory() {
        RecipeStoreService.CachedRecipes cached = RecipeStoreService.CachedRecipes.invalid();

        assertThat(cached.valid()).isFalse();
        assertThat(cached.recipes()).isEmpty();
    }

    // ---- helpers ------------------------------------------------------------

    private void enableStore() {
        ReflectionTestUtils.setField(recipeStoreService, "enabled", true);
    }

    private Recipe createTestRecipe(String title) {
        return new Recipe(
                true, "1.0.0", "1.0.0",
                new RecipeMetadata(title, "https://example.com", null, null, LocalDate.now(),
                        null, null, 4, null, null, null, null, null),
                "Description",
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main")),
                null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }
}
