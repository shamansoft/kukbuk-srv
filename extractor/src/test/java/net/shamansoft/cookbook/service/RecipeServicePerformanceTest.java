package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.model.Recipe;
import net.shamansoft.cookbook.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
class RecipeServicePerformanceTest {

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
    @DisplayName("Should retrieve cached recipe within 100ms performance requirement")
    void shouldRetrieveCachedRecipeWithin100ms() {
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
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertTrue(retrievedCache.isPresent());
        assertTrue(duration < 100, "Cache retrieval took " + duration + "ms, which exceeds the 100ms requirement");
    }

    @Test
    @DisplayName("Should handle cache miss within 100ms performance requirement")
    void shouldHandleCacheMissWithin100ms() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertFalse(retrievedCache.isPresent());
        assertTrue(duration < 100, "Cache miss handling took " + duration + "ms, which exceeds the 100ms requirement");
    }

    @RepeatedTest(10)
    @DisplayName("Should consistently meet performance requirements across multiple runs")
    void shouldConsistentlyMeetPerformanceRequirementsAcrossMultipleRuns() {
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
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertTrue(retrievedCache.isPresent());
        assertTrue(duration < 100, "Cache retrieval took " + duration + "ms in repeated test");
    }

    @Test
    @DisplayName("Should handle concurrent retrieval requests efficiently")
    void shouldHandleConcurrentRetrievalRequestsEfficiently() {
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

        // When - Multiple concurrent requests
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = service.findCachedRecipe(testUrl);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).join();
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Then
        for (CompletableFuture<Optional<Recipe>> future : futures) {
            Optional<Recipe> result = future.join();
            assertTrue(result.isPresent());
        }
        
        // Total time for 10 concurrent requests should still be reasonable
        assertTrue(totalDuration < 500, "Concurrent retrieval took " + totalDuration + "ms for 10 requests");
    }

    @Test
    @DisplayName("Should cache recipe efficiently without blocking")
    void shouldCacheRecipeEfficientlyWithoutBlocking() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.save(any(Recipe.class))).thenReturn(CompletableFuture.completedFuture(null));

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> result = service.storeRecipe(testUrl, testYaml);
        result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        // Cache operations should complete quickly (though not as strict as retrieval)
        assertTrue(duration < 200, "Cache save took " + duration + "ms, which is too slow");
    }

    @Test
    @DisplayName("Should handle existence check efficiently")
    void shouldHandleExistenceCheckEfficiently() {
        // Given
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.existsByContentHash(testHash)).thenReturn(CompletableFuture.completedFuture(true));

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Boolean> result = service.isCached(testUrl);
        Boolean isCached = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertTrue(isCached);
        assertTrue(duration < 100, "Existence check took " + duration + "ms, which exceeds the 100ms requirement");
    }

    @Test
    @DisplayName("Should timeout gracefully when operations take too long")
    void shouldTimeoutGracefullyWhenOperationsTakeTooLong() {
        // Given - Mock a slow repository response
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        
        // Create a future that never completes to simulate a slow operation
        CompletableFuture<Optional<Recipe>> slowFuture = new CompletableFuture<>();
        when(repository.findByContentHash(testHash)).thenReturn(slowFuture);

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - Should timeout and return empty
        assertFalse(retrievedCache.isPresent());
        assertTrue(duration >= 100 && duration < 200, "Timeout should occur around 100ms, but took " + duration + "ms");
    }

    @Test
    @DisplayName("Should not block main processing when cache operations fail")
    void shouldNotBlockMainProcessingWhenCacheOperationsFail() {
        // Given - Mock failing operations
        when(contentHashService.generateContentHash(testUrl)).thenReturn(testHash);
        when(repository.findByContentHash(testHash)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Simulated failure")));

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = service.findCachedRecipe(testUrl);
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - Should handle error quickly and return empty
        assertFalse(retrievedCache.isPresent());
        assertTrue(duration < 50, "Error handling took " + duration + "ms, should be very fast");
    }
}