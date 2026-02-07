package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FirestoreYouTubeJobRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String JOBS_COLLECTION = "youtube_jobs";
    private static final Executor executor = Executors.newCachedThreadPool();
    private final Firestore firestore;

    public CompletableFuture<YouTubeJob> save(YouTubeJob job) {
        log.debug("Saving YouTube job: {} for user: {}", job.jobId(), job.userId());

        DocumentReference docRef = firestore
                .collection(USERS_COLLECTION).document(job.userId())
                .collection(JOBS_COLLECTION).document(job.jobId());

        ApiFuture<WriteResult> future = docRef.set(job.toMap());

        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully saved YouTube job: {}", job.jobId());
                return job;
            } catch (Exception e) {
                log.error("Error saving YouTube job {}: {}", job.jobId(), e.getMessage(), e);
                throw new CompletionException("Failed to save YouTube job", e);
            }
        }, executor);
    }

    public CompletableFuture<Optional<YouTubeJob>> findById(String userId, String jobId) {
        log.debug("Retrieving YouTube job: {} for user: {}", jobId, userId);

        DocumentReference docRef = firestore
                .collection(USERS_COLLECTION).document(userId)
                .collection(JOBS_COLLECTION).document(jobId);

        ApiFuture<DocumentSnapshot> future = docRef.get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentSnapshot snapshot = future.get();
                if (!snapshot.exists()) {
                    log.debug("YouTube job not found: {}", jobId);
                    return Optional.empty();
                }
                Map<String, Object> data = snapshot.getData();
                return YouTubeJob.fromMap(data);
            } catch (Exception e) {
                log.error("Error retrieving YouTube job {}: {}", jobId, e.getMessage(), e);
                return Optional.empty();
            }
        }, executor);
    }

    public CompletableFuture<Void> updateStatus(String userId, String jobId, JobStatus status, String errorMessage) {
        log.debug("Updating YouTube job {} status to: {}", jobId, status);

        DocumentReference docRef = firestore
                .collection(USERS_COLLECTION).document(userId)
                .collection(JOBS_COLLECTION).document(jobId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status.name());
        if (errorMessage != null) {
            updates.put("errorMessage", errorMessage);
        }
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            updates.put("completedAt", Instant.now().toString());
        }

        ApiFuture<WriteResult> future = docRef.update(updates);

        return CompletableFuture.supplyAsync(() -> {
            try {
                future.get();
                log.debug("Successfully updated YouTube job {} to status: {}", jobId, status);
                return null;
            } catch (Exception e) {
                log.error("Error updating YouTube job {} status: {}", jobId, e.getMessage(), e);
                throw new CompletionException("Failed to update YouTube job status", e);
            }
        }, executor);
    }
}
