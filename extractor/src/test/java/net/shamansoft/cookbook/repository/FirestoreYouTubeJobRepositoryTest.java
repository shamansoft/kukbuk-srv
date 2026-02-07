package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirestoreYouTubeJobRepositoryTest {

    @Mock
    private Firestore firestore;
    @Mock
    private CollectionReference usersCollection;
    @Mock
    private DocumentReference userDoc;
    @Mock
    private CollectionReference jobsCollection;
    @Mock
    private DocumentReference jobDoc;

    private FirestoreYouTubeJobRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new FirestoreYouTubeJobRepository(firestore);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document("user-1")).thenReturn(userDoc);
        when(userDoc.collection("youtube_jobs")).thenReturn(jobsCollection);
        when(jobsCollection.document("job-1")).thenReturn(jobDoc);
    }

    @Test
    @DisplayName("save() should persist job to Firestore and return saved job")
    void saveShouldPersistJob() throws Exception {
        YouTubeJob job = new YouTubeJob(
                "job-1", "user-1", "https://youtube.com/watch?v=abc",
                JobStatus.PENDING, Instant.now(), null, null, null
        );

        @SuppressWarnings("unchecked")
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));
        when(jobDoc.set(any(Map.class))).thenReturn(writeFuture);

        YouTubeJob result = repository.save(job).get();

        assertThat(result).isEqualTo(job);
        verify(jobDoc).set(any(Map.class));
    }

    @Test
    @DisplayName("save() should throw CompletionException on Firestore error")
    void saveShouldThrowOnError() throws Exception {
        YouTubeJob job = new YouTubeJob(
                "job-1", "user-1", "https://youtube.com/watch?v=abc",
                JobStatus.PENDING, Instant.now(), null, null, null
        );

        @SuppressWarnings("unchecked")
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenThrow(new ExecutionException("Firestore error", new RuntimeException()));
        when(jobDoc.set(any(Map.class))).thenReturn(writeFuture);

        assertThatThrownBy(() -> repository.save(job).join())
                .isInstanceOf(java.util.concurrent.CompletionException.class);
    }

    @Test
    @DisplayName("findById() should return job when found")
    void findByIdShouldReturnJobWhenFound() throws Exception {
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> getFuture = mock(ApiFuture.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);

        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> data = Map.of(
                "jobId", "job-1",
                "userId", "user-1",
                "videoUrl", "https://youtube.com/watch?v=abc",
                "status", "PENDING",
                "createdAt", createdAt.toString()
        );

        when(getFuture.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getData()).thenReturn(data);
        when(jobDoc.get()).thenReturn(getFuture);

        Optional<YouTubeJob> result = repository.findById("user-1", "job-1").get();

        assertThat(result).isPresent();
        assertThat(result.get().jobId()).isEqualTo("job-1");
        assertThat(result.get().status()).isEqualTo(JobStatus.PENDING);
        assertThat(result.get().createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("findById() should return empty when not found")
    void findByIdShouldReturnEmptyWhenNotFound() throws Exception {
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> getFuture = mock(ApiFuture.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);

        when(getFuture.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(false);
        when(jobDoc.get()).thenReturn(getFuture);

        Optional<YouTubeJob> result = repository.findById("user-1", "job-1").get();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById() should throw CompletionException on Firestore error")
    void findByIdShouldThrowOnError() throws Exception {
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> getFuture = mock(ApiFuture.class);
        when(getFuture.get()).thenThrow(new ExecutionException("Firestore error", new RuntimeException()));
        when(jobDoc.get()).thenReturn(getFuture);

        assertThatThrownBy(() -> repository.findById("user-1", "job-1").join())
                .isInstanceOf(java.util.concurrent.CompletionException.class);
    }

    @Test
    @DisplayName("updateStatus() should update status fields in Firestore")
    @SuppressWarnings("unchecked")
    void updateStatusShouldUpdateFields() throws Exception {
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));
        when(jobDoc.update(anyMap())).thenReturn(writeFuture);

        repository.updateStatus("user-1", "job-1", JobStatus.PROCESSING, null).get();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDoc).update(captor.capture());

        Map<String, Object> updates = captor.getValue();
        assertThat(updates.get("status")).isEqualTo("PROCESSING");
        assertThat(updates).doesNotContainKey("errorMessage");
        assertThat(updates).doesNotContainKey("completedAt");
    }

    @Test
    @DisplayName("updateStatus() should include completedAt when status is COMPLETED")
    @SuppressWarnings("unchecked")
    void updateStatusShouldIncludeCompletedAtWhenCompleted() throws Exception {
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));
        when(jobDoc.update(anyMap())).thenReturn(writeFuture);

        repository.updateStatus("user-1", "job-1", JobStatus.COMPLETED, null).get();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDoc).update(captor.capture());

        Map<String, Object> updates = captor.getValue();
        assertThat(updates.get("status")).isEqualTo("COMPLETED");
        assertThat(updates).containsKey("completedAt");
    }

    @Test
    @DisplayName("updateStatus() should include error message and completedAt when FAILED")
    @SuppressWarnings("unchecked")
    void updateStatusShouldIncludeErrorMessageWhenFailed() throws Exception {
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));
        when(jobDoc.update(anyMap())).thenReturn(writeFuture);

        repository.updateStatus("user-1", "job-1", JobStatus.FAILED, "Transcript not available").get();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDoc).update(captor.capture());

        Map<String, Object> updates = captor.getValue();
        assertThat(updates.get("status")).isEqualTo("FAILED");
        assertThat(updates.get("errorMessage")).isEqualTo("Transcript not available");
        assertThat(updates).containsKey("completedAt");
    }

    @Test
    @DisplayName("updateStatus() should include driveFileId when provided")
    @SuppressWarnings("unchecked")
    void updateStatusShouldIncludeDriveFileId() throws Exception {
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));
        when(jobDoc.update(anyMap())).thenReturn(writeFuture);

        repository.updateStatus("user-1", "job-1", JobStatus.COMPLETED, null, "drive-file-123").get();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobDoc).update(captor.capture());

        Map<String, Object> updates = captor.getValue();
        assertThat(updates.get("status")).isEqualTo("COMPLETED");
        assertThat(updates.get("driveFileId")).isEqualTo("drive-file-123");
        assertThat(updates).containsKey("completedAt");
    }

    @Test
    @DisplayName("updateStatus() should throw CompletionException on Firestore error")
    @SuppressWarnings("unchecked")
    void updateStatusShouldThrowOnError() throws Exception {
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(writeFuture.get()).thenThrow(new ExecutionException("Firestore error", new RuntimeException()));
        when(jobDoc.update(anyMap())).thenReturn(writeFuture);

        assertThatThrownBy(() ->
                repository.updateStatus("user-1", "job-1", JobStatus.PROCESSING, null).join()
        ).isInstanceOf(java.util.concurrent.CompletionException.class);
    }
}
