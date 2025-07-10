package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.model.Recipe;
import net.shamansoft.cookbook.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository repository;

    @Mock
    private ContentHashService contentHashService;

    private RecipeStoreService service;

    private final String testUrl = "https://example.com/recipe";
    private final String testHash = "test-hash-123";
    private final String testYaml = "recipe: test";

    @BeforeEach
    void setUp() {
        service = new RecipeStoreService(repository, contentHashService);
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
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.of(cachedRecipe)));

        // When
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);

        // Then
        Optional<Recipe> retrievedCache = result.join();
        assertTrue(retrievedCache.isPresent());
        assertEquals(testHash, retrievedCache.get().getContentHash());
        assertEquals(testUrl, retrievedCache.get().getSourceUrl());
        assertEquals(testYaml, retrievedCache.get().getRecipeYaml());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should return empty when cache miss")
    void shouldReturnEmptyWhenCacheMiss() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // When
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);

        // Then
        Optional<Recipe> retrievedCache = result.join();
        assertFalse(retrievedCache.isPresent());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should handle hash generation error gracefully")
    void shouldHandleHashGenerationErrorGracefully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenThrow(new RuntimeException("Hash generation failed"));

        // When
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);

        // Then
        Optional<Recipe> retrievedCache = result.join();
        assertFalse(retrievedCache.isPresent());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository, never()).findByContentHash(any());
    }

    @Test
    @DisplayName("Should handle repository error gracefully")
    void shouldHandleRepositoryErrorGracefully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Repository error")));

        // When
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);

        // Then
        Optional<Recipe> retrievedCache = result.join();
        assertFalse(retrievedCache.isPresent());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).findByContentHash(testHash);
    }

    @Test
    @DisplayName("Should cache recipe successfully")
    void shouldCacheRecipeSuccessfully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = service.storeRecipe(testUrl, testYaml);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).save(argThat(recipeCache -> 
                testHash.equals(recipeCache.getContentHash()) &&
                testUrl.equals(recipeCache.getSourceUrl()) &&
                testYaml.equals(recipeCache.getRecipeYaml()) &&
                recipeCache.getCreatedAt() != null &&
                recipeCache.getLastAccessedAt() != null &&
                recipeCache.getAccessCount() == 0L
        ));
    }

    @Test
    @DisplayName("Should handle cache save error gracefully")
    void shouldHandleCacheSaveErrorGracefully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        // When
        CompletableFuture<Void> result = service.storeRecipe(testUrl, testYaml);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).save(any(Recipe.class));
    }

    @Test
    @DisplayName("Should check if recipe is cached successfully")
    void shouldCheckIfRecipeIsCachedSuccessfully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.existsByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(true));

        // When
        CompletableFuture<Boolean> result = service.isCached(testUrl);

        // Then
        Boolean isCached = result.join();
        assertTrue(isCached);

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).existsByContentHash(testHash);
    }

    @Test
    @DisplayName("Should return false when checking cache existence fails")
    void shouldReturnFalseWhenCheckingCacheExistenceFails() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.existsByContentHash(testHash)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Existence check failed")));

        // When
        CompletableFuture<Boolean> result = service.isCached(testUrl);

        // Then
        Boolean isCached = result.join();
        assertFalse(isCached);

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).existsByContentHash(testHash);
    }

    @Test
    @DisplayName("Should evict from cache successfully")
    void shouldEvictFromCacheSuccessfully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.deleteByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = service.evictFromCache(testUrl);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).deleteByContentHash(testHash);
    }

    @Test
    @DisplayName("Should handle eviction error gracefully")
    void shouldHandleEvictionErrorGracefully() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.deleteByContentHash(testHash)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete failed")));

        // When
        CompletableFuture<Void> result = service.evictFromCache(testUrl);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(contentHashService).generateContentHash(testUrl);
        verify(repository).deleteByContentHash(testHash);
    }

    @Test
    @DisplayName("Should get cache size successfully")
    void shouldGetCacheSizeSuccessfully() {
        // Given
        when(repository.count()).thenReturn(CompletableFuture.completedFuture(42L));

        // When
        CompletableFuture<Long> result = service.getCacheSize();

        // Then
        Long cacheSize = result.join();
        assertEquals(42L, cacheSize);

        verify(repository).count();
    }

    @Test
    @DisplayName("Should return 0 when cache size check fails")
    void shouldReturn0WhenCacheSizeCheckFails() {
        // Given
        when(repository.count()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Count failed")));

        // When
        CompletableFuture<Long> result = service.getCacheSize();

        // Then
        Long cacheSize = result.join();
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
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.of(cachedRecipe)));

        // When
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipeByHash(testHash);

        // Then
        Optional<Recipe> retrievedCache = result.join();
        assertTrue(retrievedCache.isPresent());
        assertEquals(testHash, retrievedCache.get().getContentHash());

        verify(repository).findByContentHash(testHash);
        verify(contentHashService, never()).generateContentHash(any());
    }

    @Test
    @DisplayName("Should cache recipe with hash directly")
    void shouldCacheRecipeWithHashDirectly() {
        // Given
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = service.storeRecipeWithHash(testHash, testUrl, testYaml);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(repository).save(argThat(recipeCache -> 
                testHash.equals(recipeCache.getContentHash()) &&
                testUrl.equals(recipeCache.getSourceUrl()) &&
                testYaml.equals(recipeCache.getRecipeYaml())
        ));
        verify(contentHashService, never()).generateContentHash(any());
    }

    @Test
    @DisplayName("Should check if cached by hash directly")
    void shouldCheckIfCachedByHashDirectly() {
        // Given
        when(repository.existsByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(true));

        // When
        CompletableFuture<Boolean> result = service.isCachedByHash(testHash);

        // Then
        Boolean isCached = result.join();
        assertTrue(isCached);

        verify(repository).existsByContentHash(testHash);
        verify(contentHashService, never()).generateContentHash(any());
    }

    @Test
    @DisplayName("Should evict from cache by hash directly")
    void shouldEvictFromCacheByHashDirectly() {
        // Given
        when(repository.deleteByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = service.evictFromCacheByHash(testHash);

        // Then
        assertDoesNotThrow(() -> result.join());

        verify(repository).deleteByContentHash(testHash);
        verify(contentHashService, never()).generateContentHash(any());
    }
}