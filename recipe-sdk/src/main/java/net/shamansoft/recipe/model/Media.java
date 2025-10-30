package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface representing media content (images or videos) in recipe instructions.
 * Uses Jackson polymorphic deserialization based on the "type" field.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ImageMedia.class, name = "image"),
        @JsonSubTypes.Type(value = VideoMedia.class, name = "video")
})
public sealed interface Media permits ImageMedia, VideoMedia {
    String type();
    String path();
}
