package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UrlRecipeRequest(
        @NotBlank(message = "URL is required")
        @Pattern(regexp = "(?i)https?://[^\\s/?#]+[^\\s]*", message = "URL must be an absolute http(s) URL")
        String url,
        String title
) {
}
