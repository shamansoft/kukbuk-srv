package net.shamansoft.cookbook.controller.internal;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.dto.YouTubeTaskRequest;
import net.shamansoft.cookbook.exception.TranscriptException;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeTaskHandlerTest {

    private static final String JOB_ID = "job-123";
    private static final String USER_ID = "user-456";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String TASK_NAME = "youtube-job-job-123";

    @Mock
    private Transformer youtubeTransformer;

    @Mock
    private FirestoreYouTubeJobRepository jobRepository;

    @Mock
    private DriveService driveService;

    @Mock
    private RecipeValidationService validationService;

    private YouTubeTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new YouTubeTaskHandler(youtubeTransformer, jobRepository, driveService, validationService);
    }

    private Recipe createTestRecipe(String title) {
        RecipeMetadata metadata = new RecipeMetadata(
                title,
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "Test Channel",
                "en",
                null, null, null,
                4,
                null, null, null, null, null
        );

        List<Ingredient> ingredients = List.of(
                new Ingredient("Sugar", "1", "cup", null, null, null, null)
        );

        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null)
        );

        return new Recipe(
                true, "1.0.0", "1.0.0",
                metadata, null, ingredients, null, instructions,
                null, null, null
        );
    }

    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("Should process recipe successfully and save to Drive")
        void shouldProcessRecipeAndSaveToDrive() throws Exception {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);
            Recipe recipe = createTestRecipe("Chocolate Cake");
            Transformer.Response transformerResponse = Transformer.Response.recipe(recipe);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenReturn(transformerResponse);
            when(validationService.toYaml(recipe)).thenReturn("recipe: yaml content");
            when(driveService.saveRecipeForUser(USER_ID, "recipe: yaml content", "Chocolate Cake"))
                    .thenReturn(new DriveService.UploadResult("file-789", "https://drive.google.com/file/789"));
            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.COMPLETED, null))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<Map<String, String>> result = handler.processYouTubeJob(TASK_NAME, request);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsEntry("status", "processed");

            verify(jobRepository).updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null);
            verify(youtubeTransformer).transform(isNull(), eq(VIDEO_URL));
            verify(validationService).toYaml(recipe);
            verify(driveService).saveRecipeForUser(USER_ID, "recipe: yaml content", "Chocolate Cake");
            verify(jobRepository).updateStatus(USER_ID, JOB_ID, JobStatus.COMPLETED, null);
        }

        @Test
        @DisplayName("Should use default title when metadata title is null")
        void shouldUseDefaultTitleWhenMetadataTitleIsNull() throws Exception {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);
            Recipe recipe = new Recipe(
                    true, "1.0.0", "1.0.0",
                    null, null,
                    List.of(new Ingredient("Sugar", "1", "cup", null, null, null, null)),
                    null,
                    List.of(new Instruction(1, "Mix", null, null, null)),
                    null, null, null
            );
            Transformer.Response transformerResponse = Transformer.Response.recipe(recipe);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenReturn(transformerResponse);
            when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");
            when(driveService.saveRecipeForUser(USER_ID, "recipe: yaml", "YouTube Recipe"))
                    .thenReturn(new DriveService.UploadResult("file-1", "url"));
            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.COMPLETED, null))
                    .thenReturn(CompletableFuture.completedFuture(null));

            handler.processYouTubeJob(TASK_NAME, request);

            verify(driveService).saveRecipeForUser(USER_ID, "recipe: yaml", "YouTube Recipe");
        }
    }

    @Nested
    @DisplayName("Not a recipe")
    class NotRecipe {

        @Test
        @DisplayName("Should mark job completed when content is not a recipe")
        void shouldMarkCompletedWhenNotRecipe() throws Exception {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);
            Transformer.Response notRecipeResponse = Transformer.Response.notRecipe();

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenReturn(notRecipeResponse);
            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.COMPLETED, null))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<Map<String, String>> result = handler.processYouTubeJob(TASK_NAME, request);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            verify(driveService, never()).saveRecipeForUser(any(), any(), any());
            verify(jobRepository).updateStatus(USER_ID, JOB_ID, JobStatus.COMPLETED, null);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should mark job failed when transformer throws exception")
        void shouldMarkJobFailedOnTransformerError() {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenThrow(new TranscriptException("All transcript providers failed"));
            when(jobRepository.updateStatus(eq(USER_ID), eq(JOB_ID), eq(JobStatus.FAILED),
                    eq("All transcript providers failed")))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<Map<String, String>> result = handler.processYouTubeJob(TASK_NAME, request);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            verify(jobRepository).updateStatus(USER_ID, JOB_ID, JobStatus.FAILED,
                    "All transcript providers failed");
        }

        @Test
        @DisplayName("Should mark job failed when Drive upload throws exception")
        void shouldMarkJobFailedOnDriveError() throws Exception {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);
            Recipe recipe = createTestRecipe("Test Recipe");
            Transformer.Response transformerResponse = Transformer.Response.recipe(recipe);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenReturn(transformerResponse);
            when(validationService.toYaml(recipe)).thenReturn("yaml");
            when(driveService.saveRecipeForUser(USER_ID, "yaml", "Test Recipe"))
                    .thenThrow(new ClientException("Drive API error"));
            when(jobRepository.updateStatus(eq(USER_ID), eq(JOB_ID), eq(JobStatus.FAILED),
                    eq("Drive API error")))
                    .thenReturn(CompletableFuture.completedFuture(null));

            ResponseEntity<Map<String, String>> result = handler.processYouTubeJob(TASK_NAME, request);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            verify(jobRepository).updateStatus(USER_ID, JOB_ID, JobStatus.FAILED, "Drive API error");
        }

        @Test
        @DisplayName("Should still return 200 even when status update to FAILED fails")
        void shouldReturn200EvenWhenStatusUpdateFails() {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenThrow(new RuntimeException("Unexpected error"));
            when(jobRepository.updateStatus(eq(USER_ID), eq(JOB_ID), eq(JobStatus.FAILED),
                    eq("Unexpected error")))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Firestore down")));

            ResponseEntity<Map<String, String>> result = handler.processYouTubeJob(TASK_NAME, request);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should truncate long error messages to 500 characters")
        void shouldTruncateLongErrorMessages() {
            YouTubeTaskRequest request = new YouTubeTaskRequest(JOB_ID, USER_ID, VIDEO_URL);
            String longMessage = "x".repeat(1000);

            when(jobRepository.updateStatus(USER_ID, JOB_ID, JobStatus.PROCESSING, null))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(youtubeTransformer.transform(isNull(), eq(VIDEO_URL)))
                    .thenThrow(new RuntimeException(longMessage));
            when(jobRepository.updateStatus(eq(USER_ID), eq(JOB_ID), eq(JobStatus.FAILED),
                    eq("x".repeat(500))))
                    .thenReturn(CompletableFuture.completedFuture(null));

            handler.processYouTubeJob(TASK_NAME, request);

            verify(jobRepository).updateStatus(eq(USER_ID), eq(JOB_ID), eq(JobStatus.FAILED),
                    eq("x".repeat(500)));
        }
    }

    @Nested
    @DisplayName("Security validation")
    class SecurityValidation {

        @Test
        @DisplayName("Cloud Tasks header constant should match expected value")
        void cloudTasksHeaderShouldBeCorrect() {
            assertThat(YouTubeTaskHandler.CLOUD_TASKS_HEADER).isEqualTo("X-CloudTasks-TaskName");
        }
    }
}
