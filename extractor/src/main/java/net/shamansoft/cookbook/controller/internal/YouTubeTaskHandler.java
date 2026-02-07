package net.shamansoft.cookbook.controller.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.YouTubeTaskRequest;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/youtube-tasks")
@RequiredArgsConstructor
@Slf4j
public class YouTubeTaskHandler {

    static final String CLOUD_TASKS_HEADER = "X-CloudTasks-TaskName";

    private final @Qualifier("youtubeTransformer") Transformer youtubeTransformer;
    private final FirestoreYouTubeJobRepository jobRepository;
    private final DriveService driveService;
    private final RecipeValidationService validationService;

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
                String yamlContent = validationService.toYaml(response.recipe());
                String title = response.recipe().metadata() != null
                        ? response.recipe().metadata().title()
                        : "YouTube Recipe";

                DriveService.UploadResult uploadResult =
                        driveService.saveRecipeForUser(request.userId(), yamlContent, title);

                jobRepository.updateStatus(request.userId(), request.jobId(),
                        JobStatus.COMPLETED, null).join();

                log.info("YouTube job {} completed - recipe saved to Drive: {}",
                        request.jobId(), uploadResult.fileId());
            } else {
                jobRepository.updateStatus(request.userId(), request.jobId(),
                        JobStatus.COMPLETED, null).join();

                log.info("YouTube job {} completed - content is not a recipe", request.jobId());
            }
        } catch (Exception e) {
            log.error("YouTube job {} failed: {}", request.jobId(), e.getMessage(), e);
            try {
                String errorMsg = e.getMessage() != null
                        ? e.getMessage().substring(0, Math.min(500, e.getMessage().length()))
                        : "Unknown error";
                jobRepository.updateStatus(request.userId(), request.jobId(),
                        JobStatus.FAILED, errorMsg).join();
            } catch (Exception statusUpdateError) {
                log.error("Failed to update job {} status to FAILED: {}",
                        request.jobId(), statusUpdateError.getMessage(), statusUpdateError);
            }
        }

        return ResponseEntity.ok(Map.of("status", "processed"));
    }
}
