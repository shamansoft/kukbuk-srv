package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.YouTubeJobStatusResponse;
import net.shamansoft.cookbook.dto.YouTubeRecipeRequest;
import net.shamansoft.cookbook.dto.YouTubeRecipeResponse;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.repository.YouTubeJob;
import net.shamansoft.cookbook.service.CloudTasksService;
import net.shamansoft.cookbook.service.youtube.YouTubeTransformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recipes/youtube")
@ConditionalOnProperty(name = "cookbook.tasks.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class YouTubeController {

    private final FirestoreYouTubeJobRepository jobRepository;
    private final CloudTasksService cloudTasksService;

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<YouTubeRecipeResponse> createYouTubeRecipe(
            @RequestBody @Valid YouTubeRecipeRequest request,
            @RequestAttribute("userId") String userId) {

        log.info("YouTube recipe request from user: {}, video: {}", userId, request.videoUrl());

        if (!isValidYouTubeUrl(request.videoUrl())) {
            log.warn("Invalid YouTube URL: {}", request.videoUrl());
            return ResponseEntity.badRequest()
                    .body(new YouTubeRecipeResponse(null, "INVALID_URL"));
        }

        String jobId = UUID.randomUUID().toString();

        YouTubeJob job = new YouTubeJob(
                jobId, userId, request.videoUrl(),
                JobStatus.PENDING, Instant.now(),
                null, null, null
        );

        jobRepository.save(job).join();

        try {
            cloudTasksService.enqueueYouTubeJob(jobId, userId, request.videoUrl());
        } catch (Exception e) {
            log.error("Failed to enqueue Cloud Task for job {}, marking as FAILED: {}", jobId, e.getMessage());
            jobRepository.updateStatus(userId, jobId, JobStatus.FAILED,
                    "Failed to enqueue processing task: " + e.getMessage()).join();
            return ResponseEntity.internalServerError()
                    .body(new YouTubeRecipeResponse(jobId, "ENQUEUE_FAILED"));
        }

        log.info("YouTube job created: {} for user: {}", jobId, userId);

        return ResponseEntity.ok(new YouTubeRecipeResponse(jobId, JobStatus.PENDING.name()));
    }

    @GetMapping(value = "/{jobId}", produces = "application/json")
    public ResponseEntity<YouTubeJobStatusResponse> getJobStatus(
            @PathVariable String jobId,
            @RequestAttribute("userId") String userId) {

        log.info("Job status request from user: {}, jobId: {}", userId, jobId);

        var jobOptional = jobRepository.findById(userId, jobId).join();

        if (jobOptional.isEmpty()) {
            log.debug("Job not found: {} for user: {}", jobId, userId);
            return ResponseEntity.notFound().build();
        }

        YouTubeJob job = jobOptional.get();

        String driveFileUrl = job.driveFileId() != null
                ? "https://drive.google.com/file/d/" + job.driveFileId() + "/view"
                : null;

        return ResponseEntity.ok(new YouTubeJobStatusResponse(
                job.jobId(),
                job.status() != null ? job.status().name() : null,
                job.driveFileId(),
                driveFileUrl,
                job.errorMessage()
        ));
    }

    static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            YouTubeTransformer.extractVideoId(url);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
