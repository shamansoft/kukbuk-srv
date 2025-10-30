package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a cover image for a recipe.
 *
 * @param path URL or path to the image
 * @param alt Alternative text for the image
 */
public record CoverImage(
        @NotBlank
        @JsonProperty("path")
        String path,

        @JsonProperty("alt")
        String alt
) {
    public CoverImage {
        if (alt == null) {
            alt = "";
        }
    }
}
