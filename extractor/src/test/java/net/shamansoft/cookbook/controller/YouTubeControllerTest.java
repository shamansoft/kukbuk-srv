package net.shamansoft.cookbook.controller;

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

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
