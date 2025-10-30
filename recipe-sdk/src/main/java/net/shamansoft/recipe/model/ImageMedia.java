package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents an image in recipe instructions.
 *
 * @param path URL or path to the image
 * @param alt Alternative text for the image
 */
@JsonTypeName("image")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageMedia(
        @NotBlank
        @JsonProperty("path")
        String path,

        @JsonProperty("alt")
        String alt
) implements Media {

    public ImageMedia {
        if (alt == null) {
            alt = "";
        }
    }

    @Override
    @JsonProperty("type")
    public String type() {
        return "image";
    }
}
