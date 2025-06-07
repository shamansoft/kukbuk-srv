package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRequest {
    @JsonProperty
    private List<Content> contents;
    @JsonProperty
    private GenerationConfig generationConfig;
    @JsonProperty
    private List<SafetySetting> safetySettings;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Content {
        @JsonProperty
        private List<Part> parts;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Part {
        @JsonProperty
        private String text;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class GenerationConfig {
        @JsonProperty
        private float temperature;
        @JsonProperty
        private double topP;
        @JsonProperty
        private int maxOutputTokens;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SafetySetting {
        @JsonProperty
        private String category;
        @JsonProperty
        private String threshold;
    }
}