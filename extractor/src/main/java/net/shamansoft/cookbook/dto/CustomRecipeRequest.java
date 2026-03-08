package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomRecipeRequest(
        @NotBlank(message = "Description is required") String description,
        String title,
        String url
) {
}
