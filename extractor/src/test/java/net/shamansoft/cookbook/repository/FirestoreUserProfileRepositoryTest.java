package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import net.shamansoft.cookbook.entitlement.UserTier;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirestoreUserProfileRepository - Profile Operations")
class FirestoreUserProfileRepositoryTest {

    @Mock
    private Firestore firestore;

    @Mock
    private Transformer transformer;

    private FirestoreUserProfileRepository repository;

    private UserProfile testProfile;
    private String testUserId;

    @BeforeEach
    void setUp() {
        repository = new FirestoreUserProfileRepository(firestore, transformer);

        testUserId = "user-123";
        testProfile = UserProfile.builder()
                .uid(testUserId)
                .userId(testUserId)
                .email("test@example.com")
                .displayName("Test User")
                .tier(UserTier.FREE)
                .credits(5)
                .build();
    }

    @Test
    @DisplayName("findByUserId should return profile when user document exists")
    void testFindByUserIdSuccess() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(true);
        when(transformer.documentToUserProfile(docSnapshot)).thenReturn(testProfile);

        // When
        CompletableFuture<java.util.Optional<UserProfile>> result = repository.findByUserId(testUserId);

        // Then
        assertThat(result.get()).isPresent().contains(testProfile);
        verify(transformer).documentToUserProfile(docSnapshot);
    }

    @Test
    @DisplayName("findByUserId should return empty when user profile not found")
    void testFindByUserIdNotFound() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(false);

        // When
        CompletableFuture<java.util.Optional<UserProfile>> result = repository.findByUserId(testUserId);

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("findByUserId should handle exceptions and return empty")
    void testFindByUserIdException() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("Firestore error"));

        // When
        CompletableFuture<java.util.Optional<UserProfile>> result = repository.findByUserId(testUserId);

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("save should persist profile using userId field")
    void testSaveWithUserIdSuccess() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.set(testProfile.toMap())).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<UserProfile> result = repository.save(testProfile);

        // Then
        assertThat(result.get()).isEqualTo(testProfile);
        verify(docRef).set(testProfile.toMap());
    }

    @Test
    @DisplayName("save should fall back to uid when userId is null")
    void testSaveWithUidFallback() throws ExecutionException, InterruptedException {
        // Given
        UserProfile profileWithNullUserId = UserProfile.builder()
                .uid(testUserId)
                .userId(null)
                .email("test@example.com")
                .displayName("Test User")
                .tier(UserTier.FREE)
                .credits(5)
                .build();

        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.set(profileWithNullUserId.toMap())).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<UserProfile> result = repository.save(profileWithNullUserId);

        // Then
        assertThat(result.get()).isEqualTo(profileWithNullUserId);
        verify(docRef).set(profileWithNullUserId.toMap());
    }


    @Test
    @DisplayName("update should update profile fields")
    void testUpdateSuccess() throws ExecutionException, InterruptedException {
        // Given
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", "Updated Name");
        updates.put("credits", 10);

        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.update(updates)).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<Void> result = repository.update(testUserId, updates);

        // Then
        assertThat(result.get()).isNull();
        verify(docRef).update(updates);
    }

    @Test
    @DisplayName("update should handle empty updates")
    void testUpdateEmpty() throws ExecutionException, InterruptedException {
        // Given
        Map<String, Object> emptyUpdates = new HashMap<>();

        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        WriteResult writeResult = mock(WriteResult.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.update(emptyUpdates)).thenReturn(future);
        when(future.get()).thenReturn(writeResult);

        // When
        CompletableFuture<Void> result = repository.update(testUserId, emptyUpdates);

        // Then
        assertThat(result.get()).isNull();
        verify(docRef).update(emptyUpdates);
    }


    @Test
    @DisplayName("existsByUserId should return true when user exists")
    void testExistsByUserIdTrue() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(true);

        // When
        CompletableFuture<Boolean> result = repository.existsByUserId(testUserId);

        // Then
        assertThat(result.get()).isTrue();
    }

    @Test
    @DisplayName("existsByUserId should return false when user not found")
    void testExistsByUserIdFalse() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenReturn(docSnapshot);
        when(docSnapshot.exists()).thenReturn(false);

        // When
        CompletableFuture<Boolean> result = repository.existsByUserId(testUserId);

        // Then
        assertThat(result.get()).isFalse();
    }

    @Test
    @DisplayName("existsByUserId should return false on exception")
    void testExistsByUserIdException() throws ExecutionException, InterruptedException {
        // Given
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        lenient().when(firestore.collection("users")).thenReturn(mock(com.google.cloud.firestore.CollectionReference.class));
        when(firestore.collection("users").document(testUserId)).thenReturn(docRef);
        when(docRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("Check failed"));

        // When
        CompletableFuture<Boolean> result = repository.existsByUserId(testUserId);

        // Then
        assertThat(result.get()).isFalse();
    }
}
