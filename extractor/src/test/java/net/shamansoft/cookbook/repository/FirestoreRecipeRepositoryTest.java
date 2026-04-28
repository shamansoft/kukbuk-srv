package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import net.shamansoft.cookbook.repository.firestore.model.StoredRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirestoreRecipeRepository - CRUD Operations")
class FirestoreRecipeRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private Transformer transformer;

    private FirestoreRecipeRepository repository;

    private StoredRecipe testRecipe;
    private String testHash;

    @BeforeEach
    void setUp() {
        repository = new FirestoreRecipeRepository(firestore, transformer);

        testHash = "test-hash-abc123";
        testRecipe = StoredRecipe.builder()
                .contentHash(testHash)
                .sourceUrl("https://example.com/recipe")
                .recipesJson("[{\"name\": \"Test Recipe\"}]")
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("findByContentHash should return recipe when document exists")
    void testFindByContentHashSuccess() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(true);
        when(transformer.documentToRecipeCache(docSnapshot)).thenReturn(testRecipe);

        // When
        CompletableFuture<java.util.Optional<StoredRecipe>> result = repository.findByContentHash(testHash);

        // Then
        assertThat(result.get()).isPresent().contains(testRecipe);
        verify(transformer).documentToRecipeCache(docSnapshot);
    }

    @Test
    @DisplayName("findByContentHash should return empty when document not found")
    void testFindByContentHashNotFound() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(false);

        // When
        CompletableFuture<java.util.Optional<StoredRecipe>> result = repository.findByContentHash(testHash);

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("findByContentHash should handle Firestore exceptions")
    void testFindByContentHashException() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("Firestore error"));

        // When
        CompletableFuture<java.util.Optional<StoredRecipe>> result = repository.findByContentHash(testHash);

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("save should successfully persist recipe")
    void testSaveSuccess() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.set(testRecipe)).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<Void> result = repository.save(testRecipe);

        // Then
        assertThat(result.get()).isNull();
        verify(docRef).set(testRecipe);
    }


    @Test
    @DisplayName("existsByContentHash should return true when document exists")
    void testExistsByContentHashTrue() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(true);

        // When
        CompletableFuture<Boolean> result = repository.existsByContentHash(testHash);

        // Then
        assertThat(result.get()).isTrue();
    }

    @Test
    @DisplayName("existsByContentHash should return false when document not found")
    void testExistsByContentHashFalse() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(false);

        // When
        CompletableFuture<Boolean> result = repository.existsByContentHash(testHash);

        // Then
        assertThat(result.get()).isFalse();
    }

    @Test
    @DisplayName("existsByContentHash should return false on exception")
    void testExistsByContentHashException() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("Check failed"));

        // When
        CompletableFuture<Boolean> result = repository.existsByContentHash(testHash);

        // Then
        assertThat(result.get()).isFalse();
    }

    @Test
    @DisplayName("deleteByContentHash should successfully delete recipe")
    void testDeleteByContentHashSuccess() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").document(testHash)).thenReturn(docRef);
        when(docRef.delete()).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<Void> result = repository.deleteByContentHash(testHash);

        // Then
        assertThat(result.get()).isNull();
        verify(docRef).delete();
    }


    @Test
    @DisplayName("count should return number of documents in collection")
    void testCountSuccess() throws ExecutionException, InterruptedException {
        // Given
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").get()).thenReturn(future);
        when(future.get()).thenReturn(querySnapshot);
        when(querySnapshot.size()).thenReturn(5);

        // When
        CompletableFuture<Long> result = repository.count();

        // Then
        assertThat(result.get()).isEqualTo(5L);
    }

    @Test
    @DisplayName("count should return zero on exception")
    void testCountException() throws ExecutionException, InterruptedException {
        // Given
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").get()).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("Count failed"));

        // When
        CompletableFuture<Long> result = repository.count();

        // Then
        assertThat(result.get()).isZero();
    }

    @Test
    @DisplayName("count should handle empty collection")
    void testCountEmptyCollection() throws ExecutionException, InterruptedException {
        // Given
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("recipe_store")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("recipe_store").get()).thenReturn(future);
        when(future.get()).thenReturn(querySnapshot);
        when(querySnapshot.size()).thenReturn(0);

        // When
        CompletableFuture<Long> result = repository.count();

        // Then
        assertThat(result.get()).isZero();
    }
}
