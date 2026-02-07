package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.dto.YouTubeJobStatusResponse;
import net.shamansoft.cookbook.dto.YouTubeRecipeRequest;
import net.shamansoft.cookbook.dto.YouTubeRecipeResponse;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.repository.YouTubeJob;
import net.shamansoft.cookbook.service.CloudTasksService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeControllerTest {

    private static final String USER_ID = "user-456";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Mock
    private FirestoreYouTubeJobRepository jobRepository;

    @Mock
    private CloudTasksService cloudTasksService;

    private YouTubeController controller;

    @BeforeEach
    void setUp() {
        controller = new YouTubeController(jobRepository, cloudTasksService);
    }

    @Nested
    @DisplayName("POST /v1/recipes/youtube")
    class CreateYouTubeRecipe {

        @Test
        @DisplayName("Should create job and enqueue task for valid YouTube URL")
        void shouldCreateJobAndEnqueueTask() {
            YouTubeRecipeRequest request = new YouTubeRecipeRequest(VIDEO_URL);

            when(jobRepository.save(any(YouTubeJob.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<YouTubeRecipeResponse> result = controller.createYouTubeRecipe(request, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().jobId()).isNotNull().isNotBlank();
            assertThat(result.getBody().status()).isEqualTo("PENDING");

            ArgumentCaptor<YouTubeJob> jobCaptor = ArgumentCaptor.forClass(YouTubeJob.class);
            verify(jobRepository).save(jobCaptor.capture());

            YouTubeJob savedJob = jobCaptor.getValue();
            assertThat(savedJob.userId()).isEqualTo(USER_ID);
            assertThat(savedJob.videoUrl()).isEqualTo(VIDEO_URL);
            assertThat(savedJob.status()).isEqualTo(JobStatus.PENDING);
            assertThat(savedJob.createdAt()).isNotNull();
            assertThat(savedJob.jobId()).isEqualTo(result.getBody().jobId());

            verify(cloudTasksService).enqueueYouTubeJob(
                    eq(savedJob.jobId()), eq(USER_ID), eq(VIDEO_URL));
        }

        @Test
        @DisplayName("Should accept youtu.be short URL")
        void shouldAcceptShortUrl() {
            YouTubeRecipeRequest request = new YouTubeRecipeRequest("https://youtu.be/dQw4w9WgXcQ");

            when(jobRepository.save(any(YouTubeJob.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<YouTubeRecipeResponse> result = controller.createYouTubeRecipe(request, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody().jobId()).isNotNull();
        }

        @Test
        @DisplayName("Should return bad request for invalid URL")
        void shouldReturnBadRequestForInvalidUrl() {
            YouTubeRecipeRequest request = new YouTubeRecipeRequest("https://example.com/not-youtube");

            ResponseEntity<YouTubeRecipeResponse> result = controller.createYouTubeRecipe(request, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().status()).isEqualTo("INVALID_URL");
            assertThat(result.getBody().jobId()).isNull();

            verify(jobRepository, never()).save(any());
            verify(cloudTasksService, never()).enqueueYouTubeJob(any(), any(), any());
        }

        @Test
        @DisplayName("Should return bad request for empty URL")
        void shouldReturnBadRequestForEmptyUrl() {
            YouTubeRecipeRequest request = new YouTubeRecipeRequest("   ");

            ResponseEntity<YouTubeRecipeResponse> result = controller.createYouTubeRecipe(request, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should generate unique job IDs for each request")
        void shouldGenerateUniqueJobIds() {
            when(jobRepository.save(any(YouTubeJob.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<YouTubeRecipeResponse> result1 =
                    controller.createYouTubeRecipe(new YouTubeRecipeRequest(VIDEO_URL), USER_ID);
            ResponseEntity<YouTubeRecipeResponse> result2 =
                    controller.createYouTubeRecipe(new YouTubeRecipeRequest(VIDEO_URL), USER_ID);

            assertThat(result1.getBody().jobId()).isNotEqualTo(result2.getBody().jobId());
        }

        @Test
        @DisplayName("Should handle enqueue failure and mark job as FAILED")
        void shouldHandleEnqueueFailure() {
            YouTubeRecipeRequest request = new YouTubeRecipeRequest(VIDEO_URL);

            when(jobRepository.save(any(YouTubeJob.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(jobRepository.updateStatus(any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            doThrow(new RuntimeException("Cloud Tasks unavailable"))
                    .when(cloudTasksService).enqueueYouTubeJob(any(), any(), any());

            ResponseEntity<YouTubeRecipeResponse> result = controller.createYouTubeRecipe(request, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(500);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().status()).isEqualTo("ENQUEUE_FAILED");
            assertThat(result.getBody().jobId()).isNotNull();

            verify(jobRepository).updateStatus(
                    eq(USER_ID),
                    any(String.class),
                    eq(JobStatus.FAILED),
                    argThat(msg -> msg != null && msg.contains("Failed to enqueue")));
        }
    }

    @Nested
    @DisplayName("GET /v1/recipes/youtube/{jobId}")
    class GetJobStatus {

        private static final String JOB_ID = "job-123";

        @Test
        @DisplayName("Should return job status when job exists and belongs to user")
        void shouldReturnJobStatusWhenFound() {
            YouTubeJob job = new YouTubeJob(
                    JOB_ID, USER_ID, VIDEO_URL, JobStatus.PROCESSING,
                    Instant.parse("2026-02-06T10:00:00Z"), null, null, null
            );

            when(jobRepository.findById(USER_ID, JOB_ID))
                    .thenReturn(CompletableFuture.completedFuture(Optional.of(job)));

            ResponseEntity<YouTubeJobStatusResponse> result = controller.getJobStatus(JOB_ID, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().jobId()).isEqualTo(JOB_ID);
            assertThat(result.getBody().status()).isEqualTo("PROCESSING");
            assertThat(result.getBody().driveFileId()).isNull();
            assertThat(result.getBody().driveFileUrl()).isNull();
            assertThat(result.getBody().errorMessage()).isNull();
        }

        @Test
        @DisplayName("Should return 404 when job not found")
        void shouldReturn404WhenJobNotFound() {
            when(jobRepository.findById(USER_ID, JOB_ID))
                    .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

            ResponseEntity<YouTubeJobStatusResponse> result = controller.getJobStatus(JOB_ID, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(404);
            assertThat(result.getBody()).isNull();
        }

        @Test
        @DisplayName("Should return completed job with drive file URL")
        void shouldReturnCompletedJobWithDriveUrl() {
            String driveFileId = "drive-file-abc";
            YouTubeJob job = new YouTubeJob(
                    JOB_ID, USER_ID, VIDEO_URL, JobStatus.COMPLETED,
                    Instant.parse("2026-02-06T10:00:00Z"),
                    Instant.parse("2026-02-06T10:05:00Z"),
                    driveFileId, null
            );

            when(jobRepository.findById(USER_ID, JOB_ID))
                    .thenReturn(CompletableFuture.completedFuture(Optional.of(job)));

            ResponseEntity<YouTubeJobStatusResponse> result = controller.getJobStatus(JOB_ID, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().jobId()).isEqualTo(JOB_ID);
            assertThat(result.getBody().status()).isEqualTo("COMPLETED");
            assertThat(result.getBody().driveFileId()).isEqualTo(driveFileId);
            assertThat(result.getBody().driveFileUrl())
                    .isEqualTo("https://drive.google.com/file/d/" + driveFileId + "/view");
            assertThat(result.getBody().errorMessage()).isNull();
        }

        @Test
        @DisplayName("Should return failed job with error message")
        void shouldReturnFailedJobWithErrorMessage() {
            String errorMsg = "Transcript extraction failed";
            YouTubeJob job = new YouTubeJob(
                    JOB_ID, USER_ID, VIDEO_URL, JobStatus.FAILED,
                    Instant.parse("2026-02-06T10:00:00Z"),
                    Instant.parse("2026-02-06T10:03:00Z"),
                    null, errorMsg
            );

            when(jobRepository.findById(USER_ID, JOB_ID))
                    .thenReturn(CompletableFuture.completedFuture(Optional.of(job)));

            ResponseEntity<YouTubeJobStatusResponse> result = controller.getJobStatus(JOB_ID, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().jobId()).isEqualTo(JOB_ID);
            assertThat(result.getBody().status()).isEqualTo("FAILED");
            assertThat(result.getBody().driveFileId()).isNull();
            assertThat(result.getBody().driveFileUrl()).isNull();
            assertThat(result.getBody().errorMessage()).isEqualTo(errorMsg);
        }

        @Test
        @DisplayName("Should return pending job status")
        void shouldReturnPendingJobStatus() {
            YouTubeJob job = new YouTubeJob(
                    JOB_ID, USER_ID, VIDEO_URL, JobStatus.PENDING,
                    Instant.parse("2026-02-06T10:00:00Z"), null, null, null
            );

            when(jobRepository.findById(USER_ID, JOB_ID))
                    .thenReturn(CompletableFuture.completedFuture(Optional.of(job)));

            ResponseEntity<YouTubeJobStatusResponse> result = controller.getJobStatus(JOB_ID, USER_ID);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().status()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("URL validation")
    class UrlValidation {

        @Test
        @DisplayName("Should accept standard youtube.com URL")
        void shouldAcceptStandardUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept youtube.com URL without www")
        void shouldAcceptUrlWithoutWww() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://youtube.com/watch?v=dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept mobile youtube URL")
        void shouldAcceptMobileUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept youtu.be short URL")
        void shouldAcceptShortUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept embed URL")
        void shouldAcceptEmbedUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://www.youtube.com/embed/dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept shorts URL")
        void shouldAcceptShortsUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should accept HTTP URL")
        void shouldAcceptHttpUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("http://www.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue();
        }

        @Test
        @DisplayName("Should reject non-YouTube URL")
        void shouldRejectNonYouTubeUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://example.com/watch?v=dQw4w9WgXcQ")).isFalse();
        }

        @Test
        @DisplayName("Should reject null URL")
        void shouldRejectNullUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl(null)).isFalse();
        }

        @Test
        @DisplayName("Should reject blank URL")
        void shouldRejectBlankUrl() {
            assertThat(YouTubeController.isValidYouTubeUrl("   ")).isFalse();
        }

        @Test
        @DisplayName("Should reject URL without video ID")
        void shouldRejectUrlWithoutVideoId() {
            assertThat(YouTubeController.isValidYouTubeUrl("https://www.youtube.com/watch")).isFalse();
        }

        @Test
        @DisplayName("Should reject random text")
        void shouldRejectRandomText() {
            assertThat(YouTubeController.isValidYouTubeUrl("not a url at all")).isFalse();
        }
    }
}
