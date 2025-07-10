package net.shamansoft.cookbook.repository;

import net.shamansoft.cookbook.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreRecipeRepositorySimpleTest {

    @Mock
    private com.google.cloud.firestore.Firestore firestore;

    private Recipe testRecipe;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testRecipe = Recipe.builder()
                .contentHash("test-hash-123")
                .sourceUrl("https://example.com/recipe")
                .recipeYaml("recipe: test")
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(1L)
                .build();
    }

    @Test
    @DisplayName("Should create repository instance successfully")
    void shouldCreateRepositoryInstanceSuccessfully() {
        // When
        FirestoreRecipeRepository repository = new FirestoreRecipeRepository(firestore);

        // Then
        assertNotNull(repository);
    }

    @Test
    @DisplayName("Should handle null values in RecipeCache model")
    void shouldHandleNullValuesInRecipeCacheModel() {
        // Given
        Recipe cacheWithNulls = Recipe.builder()
                .contentHash("test-hash")
                .sourceUrl(null)
                .recipeYaml(null)
                .createdAt(null)
                .lastAccessedAt(null)
                .accessCount(0L)
                .build();

        // Then - Should not throw exceptions
        assertNotNull(cacheWithNulls);
        assertEquals("test-hash", cacheWithNulls.getContentHash());
        assertNull(cacheWithNulls.getSourceUrl());
        assertNull(cacheWithNulls.getRecipeYaml());
        assertEquals(0L, cacheWithNulls.getAccessCount());
    }

    @Test
    @DisplayName("Should handle instant comparisons correctly")
    void shouldHandleInstantComparisonsCorrectly() {
        // Given
        Instant now = Instant.now();
        Recipe cache1 = Recipe.builder()
                .contentHash("test-hash-1")
                .sourceUrl("https://example.com")
                .recipeYaml("recipe: test")
                .createdAt(now)
                .lastAccessedAt(now)
                .accessCount(1L)
                .build();

        Recipe cache2 = Recipe.builder()
                .contentHash("test-hash-2")
                .sourceUrl("https://example.com")
                .recipeYaml("recipe: test")
                .createdAt(now)
                .lastAccessedAt(now)
                .accessCount(1L)
                .build();

        // Then
        assertEquals(cache1.getCreatedAt(), cache2.getCreatedAt());
        assertEquals(cache1.getLastAccessedAt(), cache2.getLastAccessedAt());
        assertNotEquals(cache1.getContentHash(), cache2.getContentHash());
    }
}