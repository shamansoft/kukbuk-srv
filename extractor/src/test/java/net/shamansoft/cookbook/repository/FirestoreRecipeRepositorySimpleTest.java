package net.shamansoft.cookbook.repository;

import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FirestoreRecipeRepositorySimpleTest {

    @Mock
    private com.google.cloud.firestore.Firestore firestore;

    private StoredRecipe testRecipe;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testRecipe = StoredRecipe.builder()
                .contentHash("test-hash-123")
                .sourceUrl("https://example.com/recipe")
                .recipesJson("[]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should create repository instance successfully")
    void shouldCreateRepositoryInstanceSuccessfully() {
        // When
        FirestoreRecipeRepository repository = new FirestoreRecipeRepository(firestore, new Transformer());

        // Then
        assertThat(repository).isNotNull();
    }

    @Test
    @DisplayName("Should handle null values in RecipeCache model")
    void shouldHandleNullValuesInRecipeCacheModel() {
        // Given
        StoredRecipe cacheWithNulls = StoredRecipe.builder()
                .contentHash("test-hash")
                .sourceUrl(null)
                .recipesJson(null)
                .createdAt(null)
                .lastUpdatedAt(null)
                .version(0L)
                .build();

        // Then - Should not throw exceptions
        assertThat(cacheWithNulls)
                .isNotNull()
                .extracting(
                        StoredRecipe::getContentHash,
                        StoredRecipe::getSourceUrl,
                        StoredRecipe::getRecipesJson,
                        StoredRecipe::getVersion
                )
                .containsExactly(
                        "test-hash",
                        null,
                        null,
                        0L
                );
    }

    @Test
    @DisplayName("Should handle instant comparisons correctly")
    void shouldHandleInstantComparisonsCorrectly() {
        // Given
        Instant now = Instant.now();
        StoredRecipe cache1 = StoredRecipe.builder()
                .contentHash("test-hash-1")
                .sourceUrl("https://example.com")
                .recipesJson("[]")
                .createdAt(now)
                .lastUpdatedAt(now)
                .version(1L)
                .build();

        StoredRecipe cache2 = StoredRecipe.builder()
                .contentHash("test-hash-2")
                .sourceUrl("https://example.com")
                .recipesJson("[]")
                .createdAt(now)
                .lastUpdatedAt(now)
                .version(1L)
                .build();

        // Then
        assertThat(cache1.getCreatedAt()).isEqualTo(cache2.getCreatedAt());
        assertThat(cache1.getLastUpdatedAt()).isEqualTo(cache2.getLastUpdatedAt());
        assertThat(cache1.getContentHash()).isNotEqualTo(cache2.getContentHash());
    }
}