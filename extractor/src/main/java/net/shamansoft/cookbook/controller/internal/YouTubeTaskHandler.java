package net.shamansoft.cookbook.controller.internal;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.YouTubeTaskRequest;
import net.shamansoft.cookbook.exception.TranscriptException;
import net.shamansoft.cookbook.exception.YouTubeApiException;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RecipePostProcessor;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/youtube-tasks")
@ConditionalOnProperty(name = "cookbook.tasks.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class YouTubeTaskHandler {

    static final String CLOUD_TASKS_HEADER = "X-CloudTasks-TaskName";

    private final Transformer youtubeTransformer;
    private final FirestoreYouTubeJobRepository jobRepository;
    private final DriveService driveService;
    private final RecipeValidationService validationService;
    private final RecipePostProcessor postProcessor;

    public YouTubeTaskHandler(
            @Qualifier("youtubeTransformer") Transformer youtubeTransformer,
            FirestoreYouTubeJobRepository jobRepository,
            DriveService driveService,
            RecipeValidationService validationService,
            RecipePostProcessor postProcessor) {
        this.youtubeTransformer = youtubeTransformer;
        this.jobRepository = jobRepository;
        this.driveService = driveService;
        this.validationService = validationService;
        this.postProcessor = postProcessor;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processYouTubeJob(
            @RequestHeader(CLOUD_TASKS_HEADER) String taskName,
            @RequestBody @Valid YouTubeTaskRequest request) {

        log.info("Received Cloud Task '{}' for job: {}, video: {}",
                taskName, request.jobId(), request.videoUrl());

        try {
            jobRepository.updateStatus(request.userId(), request.jobId(),
                    JobStatus.PROCESSING, null).join();

            Transformer.Response response = youtubeTransformer.transform(null, request.videoUrl());

            if (response.isRecipe()) {
                // Validate the recipe
                RecipeValidationService.ValidationResult validationResult =
                        validationService.validate(response.recipe());
                if (!validationResult.isValid()) {
                    log.warn("YouTube recipe failed validation for job {}: {}",
                            request.jobId(), validationResult.getErrorMessage());
                }

                // Post-process to populate deterministic fields (schemaVersion, dateCreated, source URL)
                Recipe recipe = validationResult.isValid()
                        ? validationResult.getRecipe()
                        : response.recipe();
                Recipe postProcessed = postProcessor.process(recipe, request.videoUrl());

                String yamlContent = validationService.toYaml(postProcessed);
                String title = postProcessed.metadata() != null
                        ? postProcessed.metadata().title()
                        : "YouTube Recipe";

                DriveService.UploadResult uploadResult =
                        driveService.saveRecipeForUser(request.userId(), yamlContent, title);

                jobRepository.updateStatus(request.userId(), request.jobId(),
                        JobStatus.COMPLETED, null, uploadResult.fileId()).join();

                log.info("YouTube job {} completed - recipe saved to Drive: {}",
                        request.jobId(), uploadResult.fileId());
            } else {
                jobRepository.updateStatus(request.userId(), request.jobId(),
                        JobStatus.COMPLETED, null).join();

                log.info("YouTube job {} completed - content is not a recipe", request.jobId());
            }
        } catch (IllegalArgumentException e) {
            // Permanent failure: invalid input (bad URL format, etc.) - don't retry
            log.error("YouTube job {} failed permanently (invalid input): {}", request.jobId(), e.getMessage());
            updateJobToFailed(request, e.getMessage());
            return ResponseEntity.ok(Map.of("status", "failed", "reason", "invalid_input"));
        } catch (TranscriptException | YouTubeApiException e) {
            // Transient failure: external API errors - return 5xx to trigger Cloud Tasks retry
            // Don't mark as FAILED yet - let Cloud Tasks retry logic handle it
            log.error("YouTube job {} failed (transient, will retry): {}", request.jobId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "transient_error", "reason", e.getMessage()));
        } catch (Exception e) {
            // Unknown errors - treat as transient to allow retry
            log.error("YouTube job {} failed (unknown, will retry): {}", request.jobId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "reason", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }

        return ResponseEntity.ok(Map.of("status", "processed"));
    }

    private void updateJobToFailed(YouTubeTaskRequest request, String errorMessage) {
        try {
            String errorMsg = errorMessage != null
                    ? errorMessage.substring(0, Math.min(500, errorMessage.length()))
                    : "Unknown error";
            jobRepository.updateStatus(request.userId(), request.jobId(),
                    JobStatus.FAILED, errorMsg).join();
        } catch (Exception statusUpdateError) {
            log.error("Failed to update job {} status to FAILED: {}",
                    request.jobId(), statusUpdateError.getMessage(), statusUpdateError);
        }
    }
}
