package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Represents a video in recipe instructions.
 *
 * @param path URL or path to the video
 * @param thumbnail URL or path to the thumbnail image
 * @param duration Duration in MM:SS format (e.g., "2:30")
 */
@JsonTypeName("video")
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoMedia(
        @NotBlank
        @JsonProperty("path")
        String path,

        @JsonProperty("thumbnail")
        String thumbnail,

        @Pattern(regexp = "^\\d+:\\d{2}$", message = "Duration must be in MM:SS format")
        @JsonProperty("duration")
        String duration
) implements Media {

    @Override
    @JsonProperty("type")
    public String type() {
        return "video";
    }
}
