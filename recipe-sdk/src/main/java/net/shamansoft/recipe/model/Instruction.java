package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Represents a recipe instruction step.
 *
 * @param step Step number (integer starting from 1)
 * @param description Markdown-formatted instruction text (required)
 * @param time Duration for this step (e.g., "15m", "2h", "1h 30m")
 * @param temperature Temperature for this step (e.g., "180°C", "350°F")
 * @param media List of images or videos for this step
 */
public record Instruction(
        @Min(1)
        @JsonProperty("step")
        Integer step,

        @NotBlank
        @JsonProperty("description")
        String description,

        @Pattern(regexp = "^(\\d+d\\s*)?(\\d+h\\s*)?(\\d+m)?$", message = "Time must match pattern like '15m', '2h', '1h 30m'")
        @JsonProperty("time")
        String time,

        @JsonProperty("temperature")
        String temperature,

        @Valid
        @JsonProperty("media")
        List<Media> media
) {
}
