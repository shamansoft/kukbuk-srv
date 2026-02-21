package net.shamansoft.cookbook.repository;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

        repository = new FirestoreRecipeRepository(firestore, new Transformer());
    }

    @Test
    @DisplayName("Should save and retrieve recipe cache successfully")
    void shouldSaveAndRetrieveRecipeCacheSuccessfully() {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("integration-test-hash")
                .sourceUrl("https://example.com/integration-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        // When - Save
        CompletableFuture<Void> saveResult = repository.save(recipe);
        assertThatCode(() -> saveResult.join()).doesNotThrowAnyException();

        // When - Retrieve
        CompletableFuture<Optional<StoredRecipe>> retrieveResult = repository.findByContentHash("integration-test-hash");
        Optional<StoredRecipe> retrievedCache = retrieveResult.join();

        // Then
        assertThat(retrievedCache)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.getContentHash()).isEqualTo("integration-test-hash");
                    assertThat(retrieved.getSourceUrl()).isEqualTo("https://example.com/integration-test");
                    assertThat(retrieved.getRecipesJson()).isEqualTo("[]");
                    assertThat(retrieved.getVersion()).isEqualTo(1L);
                    assertThat(retrieved.getCreatedAt()).isNotNull();
                    assertThat(retrieved.getLastUpdatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should return empty for non-existent recipe cache")
    void shouldReturnEmptyForNonExistentRecipeCache() {
        // When
        CompletableFuture<Optional<StoredRecipe>> result = repository.findByContentHash("non-existent-hash");
        Optional<StoredRecipe> recipeCache = result.join();

        // Then
        assertThat(recipeCache).isEmpty();
    }

    @Test
    @DisplayName("Should check existence correctly")
    void shouldCheckExistenceCorrectly() {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("existence-test-hash")
                .sourceUrl("https://example.com/existence-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        // When - Save first
        repository.save(recipe).join();

        // When - Check existence
        CompletableFuture<Boolean> existsResult = repository.existsByContentHash("existence-test-hash");
        CompletableFuture<Boolean> notExistsResult = repository.existsByContentHash("non-existent-hash");

        // Then
        assertThat(existsResult.join()).isTrue();
        assertThat(notExistsResult.join()).isFalse();
    }

    @Test
    @DisplayName("Should delete recipe cache successfully")
    void shouldDeleteRecipeCacheSuccessfully() {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("delete-test-hash")
                .sourceUrl("https://example.com/delete-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        // When - Save first
        repository.save(recipe).join();

        // Verify it exists
        assertThat(repository.existsByContentHash("delete-test-hash").join()).isTrue();

        // When - Delete
        CompletableFuture<Void> deleteResult = repository.deleteByContentHash("delete-test-hash");
        assertThatCode(() -> deleteResult.join()).doesNotThrowAnyException();

        // Then - Verify it's gone
        assertThat(repository.existsByContentHash("delete-test-hash").join()).isFalse();
    }

    @Test
    @DisplayName("Should count documents correctly")
    void shouldCountDocumentsCorrectly() {
        // Given - Clear any existing data and add some test data
        String[] hashes = {"count-test-1", "count-test-2", "count-test-3"};

        for (String hash : hashes) {
            StoredRecipe recipe = StoredRecipe.builder()
                    .contentHash(hash)
                    .sourceUrl("https://example.com/" + hash)
                    .recipesJson("[]")
                    .createdAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .version(1L)
                    .build();
            repository.save(recipe).join();
        }

        // When
        CompletableFuture<Long> countResult = repository.count();
        Long count = countResult.join();

        // Then
        assertThat(count)
                .as("Count should be at least 3, but was: %d", count)
                .isGreaterThanOrEqualTo(3L);
    }

    @Test
    @DisplayName("Should handle concurrent access correctly")
    void shouldHandleConcurrentAccessCorrectly() throws InterruptedException {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("concurrent-test-hash")
                .sourceUrl("https://example.com/concurrent-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        // Save initially
        repository.save(recipe).join();

        // When - Multiple concurrent reads
        CompletableFuture<Optional<StoredRecipe>>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = repository.findByContentHash("concurrent-test-hash");
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).join();

        // Then - All should succeed
        for (CompletableFuture<Optional<StoredRecipe>> future : futures) {
            Optional<StoredRecipe> result = future.join();
            assertThat(result)
                    .isPresent()
                    .get()
                    .extracting(StoredRecipe::getContentHash)
                    .isEqualTo("concurrent-test-hash");
        }
    }

    @Test
    @DisplayName("Should meet performance requirements for retrieval")
    void shouldMeetPerformanceRequirementsForRetrieval() {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("performance-test-hash")
                .sourceUrl("https://example.com/performance-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();

        repository.save(recipe).join();

        // When - Measure retrieval time
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<StoredRecipe>> result = repository.findByContentHash("performance-test-hash");
        Optional<StoredRecipe> retrievedCache = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(retrievedCache).isPresent();
        assertThat(duration)
                .as("Retrieval took %dms, which exceeds the 100ms requirement", duration)
                .isLessThan(100L);
    }

    @Test
    @DisplayName("Should update version on update")
    void shouldUpdateVersionOnUpdate() {
        // Given
        StoredRecipe recipe = StoredRecipe.builder()
                .contentHash("version-test-hash")
                .sourceUrl("https://example.com/version-test")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        repository.save(recipe).join();

        // When - Update the recipe
        StoredRecipe updatedRecipe = recipe.withUpdatedVersion();
        repository.save(updatedRecipe).join();

        // Then - Version should be incremented
        Optional<StoredRecipe> finalResult = repository.findByContentHash("version-test-hash").join();
        assertThat(finalResult)
                .isPresent()
                .get()
                .satisfies(result -> {
                    assertThat(result.getVersion())
                            .as("Version should be incremented to 1")
                            .isEqualTo(1L);
                    assertThat(result.getLastUpdatedAt()).isNotNull();
                });
    }
}