package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.YouTubeRecipeRequest;
import net.shamansoft.cookbook.dto.YouTubeRecipeResponse;
import net.shamansoft.cookbook.repository.FirestoreYouTubeJobRepository;
import net.shamansoft.cookbook.repository.JobStatus;
import net.shamansoft.cookbook.repository.YouTubeJob;
import net.shamansoft.cookbook.service.CloudTasksService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/v1/recipes/youtube")
@RequiredArgsConstructor
@Slf4j
public class YouTubeController {

    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:youtube\\.com|m\\.youtube\\.com)/watch\\?.*v=[a-zA-Z0-9_-]{11}" +
            "|(?:https?://)?youtu\\.be/[a-zA-Z0-9_-]{11}" +
            "|(?:https?://)?(?:www\\.)?youtube\\.com/embed/[a-zA-Z0-9_-]{11}" +
            "|(?:https?://)?(?:www\\.)?youtube\\.com/shorts/[a-zA-Z0-9_-]{11}"
    );

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

        cloudTasksService.enqueueYouTubeJob(jobId, userId, request.videoUrl());

        log.info("YouTube job created: {} for user: {}", jobId, userId);

        return ResponseEntity.ok(new YouTubeRecipeResponse(jobId, JobStatus.PENDING.name()));
    }

    static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return YOUTUBE_URL_PATTERN.matcher(url).matches();
    }
}
