package net.shamansoft.cookbook.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeJobTest {

    @Test
    @DisplayName("Should convert YouTubeJob to map and back")
    void shouldConvertToMapAndBack() {
        Instant now = Instant.now();
        YouTubeJob job = new YouTubeJob(
                "job-123", "user-456", "https://youtube.com/watch?v=abc",
                JobStatus.PENDING, now, null, null, null
        );

        Map<String, Object> map = job.toMap();
        Optional<YouTubeJob> restored = YouTubeJob.fromMap(map);

        assertThat(restored).isPresent();
        assertThat(restored.get().jobId()).isEqualTo("job-123");
        assertThat(restored.get().userId()).isEqualTo("user-456");
        assertThat(restored.get().videoUrl()).isEqualTo("https://youtube.com/watch?v=abc");
        assertThat(restored.get().status()).isEqualTo(JobStatus.PENDING);
        assertThat(restored.get().createdAt()).isEqualTo(now);
        assertThat(restored.get().completedAt()).isNull();
        assertThat(restored.get().driveFileId()).isNull();
        assertThat(restored.get().errorMessage()).isNull();
    }

    @Test
    @DisplayName("Should convert completed job to map and back")
    void shouldConvertCompletedJobToMapAndBack() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:05:00Z");
        YouTubeJob job = new YouTubeJob(
                "job-789", "user-111", "https://youtu.be/xyz",
                JobStatus.COMPLETED, created, completed, "drive-file-id", null
        );

        Map<String, Object> map = job.toMap();
        Optional<YouTubeJob> restored = YouTubeJob.fromMap(map);

        assertThat(restored).isPresent();
        assertThat(restored.get().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(restored.get().completedAt()).isEqualTo(completed);
        assertThat(restored.get().driveFileId()).isEqualTo("drive-file-id");
    }

    @Test
    @DisplayName("Should convert failed job to map and back")
    void shouldConvertFailedJobToMapAndBack() {
        YouTubeJob job = new YouTubeJob(
                "job-fail", "user-222", "https://youtube.com/watch?v=err",
                JobStatus.FAILED, Instant.now(), Instant.now(), null, "Transcript API failed"
        );

        Map<String, Object> map = job.toMap();
        Optional<YouTubeJob> restored = YouTubeJob.fromMap(map);

        assertThat(restored).isPresent();
        assertThat(restored.get().status()).isEqualTo(JobStatus.FAILED);
        assertThat(restored.get().errorMessage()).isEqualTo("Transcript API failed");
    }

    @Test
    @DisplayName("Should return empty optional for null map")
    void shouldReturnEmptyForNullMap() {
        Optional<YouTubeJob> result = YouTubeJob.fromMap(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should create new job with updated status using withStatus")
    void shouldCreateNewJobWithUpdatedStatus() {
        YouTubeJob job = new YouTubeJob(
                "job-1", "user-1", "https://youtube.com/watch?v=test",
                JobStatus.PENDING, Instant.now(), null, null, null
        );

        YouTubeJob processing = job.withStatus(JobStatus.PROCESSING);

        assertThat(processing.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(processing.jobId()).isEqualTo(job.jobId());
        assertThat(processing.userId()).isEqualTo(job.userId());
    }

    @Test
    @DisplayName("Should create completed job with driveFileId using withCompleted")
    void shouldCreateCompletedJobWithDriveFileId() {
        YouTubeJob job = new YouTubeJob(
                "job-1", "user-1", "https://youtube.com/watch?v=test",
                JobStatus.PROCESSING, Instant.now(), null, null, null
        );

        YouTubeJob completed = job.withCompleted("file-abc");

        assertThat(completed.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completed.driveFileId()).isEqualTo("file-abc");
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.errorMessage()).isNull();
    }

    @Test
    @DisplayName("Should create failed job with error message using withFailed")
    void shouldCreateFailedJobWithErrorMessage() {
        YouTubeJob job = new YouTubeJob(
                "job-1", "user-1", "https://youtube.com/watch?v=test",
                JobStatus.PROCESSING, Instant.now(), null, null, null
        );

        YouTubeJob failed = job.withFailed("Something went wrong");

        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("Something went wrong");
        assertThat(failed.completedAt()).isNotNull();
    }
}
