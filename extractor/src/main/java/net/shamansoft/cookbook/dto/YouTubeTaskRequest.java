package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;

public record YouTubeTaskRequest(
        @NotBlank(message = "jobId is required") String jobId,
        @NotBlank(message = "userId is required") String userId,
        @NotBlank(message = "videoUrl is required") String videoUrl
) {
}
