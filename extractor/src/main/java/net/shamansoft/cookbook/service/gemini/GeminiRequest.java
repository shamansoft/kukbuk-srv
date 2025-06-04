package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRequest {
    @JsonProperty
    private final List<Content> contents;
    @JsonProperty
    private final GenerationConfig generationConfig;
    @JsonProperty
    private final List<SafetySetting> safetySettings;

    @Builder
    @Data
    public static final class Content {
        @JsonProperty
        private final List<Part> parts;
    }

    @Builder
    @Data
    public static final class Part {
        @JsonProperty
        private final String text;
    }

    @Builder
    @Data
    public static final class GenerationConfig {
        @JsonProperty
        private final float temperature;
        @JsonProperty
        private final double topP;
        @JsonProperty
        private final int maxOutputTokens;
    }

    @Builder
    @Data
    public static final class SafetySetting {
        @JsonProperty
        private final String category;
        @JsonProperty
        private final String threshold;
    }
}