package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;

public record YouTubeRecipeRequest(
        @NotBlank(message = "videoUrl is required") String videoUrl
) {
}
