package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.model.Recipe;
import net.shamansoft.cookbook.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository repository;

    private RecipeStoreService service;

    private final String testUrl = "https://example.com/recipe";
    private final String testHash = "test-hash-123";
    private final String testYaml = "recipe: test";

    @BeforeEach
    void setUp() {
        service = new RecipeStoreService(repository);
        // Enable the service for testing by setting the enabled field through reflection
        try {
            var enabledField = RecipeStoreService.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(service, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable RecipeStoreService for testing", e);
        }
    }

    @Test
    @DisplayName("Should find cached recipe successfully")
    void shouldFindCachedRecipeSuccessfully() {
        // Given
        Recipe cachedRecipe = Recipe.builder()
                .contentHash(testHash)
                .sourceUrl(testUrl)
                .recipeYaml(testYaml)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.of(cachedRecipe)));

        // When
        Optional<Recipe> retrievedCache = service.findStoredRecipeByHash(testHash);

        // Then
        assertTrue(retrievedCache.isPresent());
        assertEquals(testHash, retrievedCache.get().getContentHash());
        assertEquals(testUrl, retrievedCache.get().getSourceUrl());
        assertEquals(testYaml, retrievedCache.get().getRecipeYaml());

        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should return empty when cache miss")
    void shouldReturnEmptyWhenCacheMiss() {
        // Given
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // When
        Optional<Recipe> retrievedCache = service.findStoredRecipeByHash(testHash);

        // Then
        assertFalse(retrievedCache.isPresent());

        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should handle hash generation error gracefully")
    void shouldHandleHashGenerationErrorGracefully() {
        // Given - simulate repository returning null instead of CompletableFuture
        when(repository.findByContentHash(testHash)).thenReturn(null);
        
        // When
        Optional<Recipe> retrievedCache = service.findStoredRecipeByHash(testHash);

        // Then
        assertFalse(retrievedCache.isPresent());

        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should handle repository error gracefully")
    void shouldHandleRepositoryErrorGracefully() {
        // Given
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Repository error")));

        // When
        Optional<Recipe> retrievedCache = service.findStoredRecipeByHash(testHash);

        // Then
        assertFalse(retrievedCache.isPresent());

        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should cache recipe successfully")
    void shouldCacheRecipeSuccessfully() {
        // Given
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        assertDoesNotThrow(() -> service.storeRecipeWithHash(testHash, testUrl, testYaml, true));

        verify(repository).save(argThat(recipeCache ->
                testHash.equals(recipeCache.getContentHash()) &&
                        testUrl.equals(recipeCache.getSourceUrl()) &&
                        testYaml.equals(recipeCache.getRecipeYaml()) &&
                        recipeCache.getCreatedAt() != null &&
                        recipeCache.getLastUpdatedAt() != null &&
                        recipeCache.getVersion() == 0L
        ));
    }

    @Test
    @DisplayName("Should handle cache save error gracefully")
    void shouldHandleCacheSaveErrorGracefully() {
        // Given
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        // When & Then
        assertDoesNotThrow(() -> service.storeRecipeWithHash(testHash, testUrl, testYaml, true));

        verify(repository).save(any(Recipe.class));
    }

    @Test
    @DisplayName("Should get cache size successfully")
    void shouldGetCacheSizeSuccessfully() {
        // Given
        when(repository.count()).thenReturn(CompletableFuture.completedFuture(42L));

        // When
        long cacheSize = service.getCacheSize();

        // Then
        assertEquals(42L, cacheSize);

        verify(repository).count();
    }

    @Test
    @DisplayName("Should return 0 when cache size check fails")
    void shouldReturn0WhenCacheSizeCheckFails() {
        // Given
        when(repository.count()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Count failed")));

        // When
        long cacheSize = service.getCacheSize();

        // Then
        assertEquals(0L, cacheSize);

        verify(repository).count();
    }

    @Test
    @DisplayName("Should find cached recipe by hash directly")
    void shouldFindCachedRecipeByHashDirectly() {
        // Given
        Recipe cachedRecipe = Recipe.builder()
                .contentHash(testHash)
                .sourceUrl(testUrl)
                .recipeYaml(testYaml)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.of(cachedRecipe)));

        // When
        Optional<Recipe> retrievedCache = service.findStoredRecipeByHash(testHash);

        // Then
        assertTrue(retrievedCache.isPresent());
        assertEquals(testHash, retrievedCache.get().getContentHash());

        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should cache recipe with hash directly")
    void shouldCacheRecipeWithHashDirectly() {
        // Given
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        assertDoesNotThrow(() -> service.storeValidRecipe(testHash, testUrl, testYaml));

        verify(repository).save(argThat(recipeCache ->
                testHash.equals(recipeCache.getContentHash()) &&
                        testUrl.equals(recipeCache.getSourceUrl()) &&
                        testYaml.equals(recipeCache.getRecipeYaml())
        ));
    }
}