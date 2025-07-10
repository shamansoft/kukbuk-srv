package net.shamansoft.cookbook.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FirestoreRecipeRepositoryIntegrationTest {

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );

    private FirestoreRecipeRepository repository;
    private Firestore firestore;

    @BeforeEach
    void setUp() {
        String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();
        firestore = FirestoreOptions.newBuilder()
                .setProjectId("test-project")
                .setHost(emulatorEndpoint)
                .setCredentials(com.google.auth.oauth2.GoogleCredentials.newBuilder().build())
                .build()
                .getService();

        repository = new FirestoreRecipeRepository(firestore);
    }

    @Test
    @DisplayName("Should save and retrieve recipe cache successfully")
    void shouldSaveAndRetrieveRecipeCacheSuccessfully() {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("integration-test-hash")
                .sourceUrl("https://example.com/integration-test")
                .recipeYaml("recipe: integration test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        // When - Save
        CompletableFuture<Void> saveResult = repository.save(recipe);
        assertDoesNotThrow(() -> saveResult.join());

        // When - Retrieve
        CompletableFuture<Optional<Recipe>> retrieveResult = repository.findByContentHash("integration-test-hash");
        Optional<Recipe> retrievedCache = retrieveResult.join();

        // Then
        assertTrue(retrievedCache.isPresent());
        Recipe retrieved = retrievedCache.get();
        assertEquals("integration-test-hash", retrieved.getContentHash());
        assertEquals("https://example.com/integration-test", retrieved.getSourceUrl());
        assertEquals("recipe: integration test", retrieved.getRecipeYaml());
        assertEquals(1L, retrieved.getAccessCount());
        assertNotNull(retrieved.getCreatedAt());
        assertNotNull(retrieved.getLastAccessedAt());
    }

    @Test
    @DisplayName("Should return empty for non-existent recipe cache")
    void shouldReturnEmptyForNonExistentRecipeCache() {
        // When
        CompletableFuture<Optional<Recipe>> result = repository.findByContentHash("non-existent-hash");
        Optional<Recipe> recipeCache = result.join();

        // Then
        assertFalse(recipeCache.isPresent());
    }

    @Test
    @DisplayName("Should check existence correctly")
    void shouldCheckExistenceCorrectly() {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("existence-test-hash")
                .sourceUrl("https://example.com/existence-test")
                .recipeYaml("recipe: existence test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        // When - Save first
        repository.save(recipe).join();

        // When - Check existence
        CompletableFuture<Boolean> existsResult = repository.existsByContentHash("existence-test-hash");
        CompletableFuture<Boolean> notExistsResult = repository.existsByContentHash("non-existent-hash");

        // Then
        assertTrue(existsResult.join());
        assertFalse(notExistsResult.join());
    }

    @Test
    @DisplayName("Should delete recipe cache successfully")
    void shouldDeleteRecipeCacheSuccessfully() {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("delete-test-hash")
                .sourceUrl("https://example.com/delete-test")
                .recipeYaml("recipe: delete test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        // When - Save first
        repository.save(recipe).join();

        // Verify it exists
        assertTrue(repository.existsByContentHash("delete-test-hash").join());

        // When - Delete
        CompletableFuture<Void> deleteResult = repository.deleteByContentHash("delete-test-hash");
        assertDoesNotThrow(() -> deleteResult.join());

        // Then - Verify it's gone
        assertFalse(repository.existsByContentHash("delete-test-hash").join());
    }

    @Test
    @DisplayName("Should count documents correctly")
    void shouldCountDocumentsCorrectly() {
        // Given - Clear any existing data and add some test data
        String[] hashes = {"count-test-1", "count-test-2", "count-test-3"};
        
        for (String hash : hashes) {
            Recipe recipe = Recipe.builder()
                    .contentHash(hash)
                    .sourceUrl("https://example.com/" + hash)
                    .recipeYaml("recipe: " + hash)
                    .createdAt(Instant.now())
                    .lastAccessedAt(Instant.now())
                    .accessCount(1L)
                    .build();
            repository.save(recipe).join();
        }

        // When
        CompletableFuture<Long> countResult = repository.count();
        Long count = countResult.join();

        // Then
        assertTrue(count >= 3, "Count should be at least 3, but was: " + count);
    }

    @Test
    @DisplayName("Should handle concurrent access correctly")
    void shouldHandleConcurrentAccessCorrectly() throws InterruptedException {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("concurrent-test-hash")
                .sourceUrl("https://example.com/concurrent-test")
                .recipeYaml("recipe: concurrent test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(0L)
                .build();

        // Save initially
        repository.save(recipe).join();

        // When - Multiple concurrent reads
        CompletableFuture<Optional<Recipe>>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = repository.findByContentHash("concurrent-test-hash");
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).join();

        // Then - All should succeed
        for (CompletableFuture<Optional<Recipe>> future : futures) {
            Optional<Recipe> result = future.join();
            assertTrue(result.isPresent());
            assertEquals("concurrent-test-hash", result.get().getContentHash());
        }
    }

    @Test
    @DisplayName("Should meet performance requirements for retrieval")
    void shouldMeetPerformanceRequirementsForRetrieval() {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("performance-test-hash")
                .sourceUrl("https://example.com/performance-test")
                .recipeYaml("recipe: performance test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();

        repository.save(recipe).join();

        // When - Measure retrieval time
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<Recipe>> result = repository.findByContentHash("performance-test-hash");
        Optional<Recipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertTrue(retrievedCache.isPresent());
        assertTrue(duration < 100, "Retrieval took " + duration + "ms, which exceeds the 100ms requirement");
    }

    @Test
    @DisplayName("Should update access count on retrieval")
    void shouldUpdateAccessCountOnRetrieval() throws InterruptedException {
        // Given
        Recipe recipe = Recipe.builder()
                .contentHash("access-count-test-hash")
                .sourceUrl("https://example.com/access-count-test")
                .recipeYaml("recipe: access count test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(0L)
                .build();

        repository.save(recipe).join();

        // When - Access the cache multiple times
        repository.findByContentHash("access-count-test-hash").join();
        
        // Wait a bit for the async access count update
        Thread.sleep(100);
        
        repository.findByContentHash("access-count-test-hash").join();
        
        // Wait a bit more for the async access count update
        Thread.sleep(100);

        // Then - Access count should be updated (note: due to async nature, exact count may vary)
        Optional<Recipe> finalResult = repository.findByContentHash("access-count-test-hash").join();
        assertTrue(finalResult.isPresent());
        // Access count should be at least 1 due to the async updates
        assertTrue(finalResult.get().getAccessCount() >= 1, 
                "Access count should be at least 1, but was: " + finalResult.get().getAccessCount());
    }
}