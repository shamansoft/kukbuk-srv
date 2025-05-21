package net.shamansoft.cookbook.service.gemini;

import lombok.Builder;

import java.util.List;

@Builder
public record GeminiRequest(List<Content> contents, GenerationConfig generationConfig,
                            List<SafetySetting> safetySettings) {

    @Builder
    public record Content(List<Part> parts) {
    }

    @Builder
    public record Part(String text) {
    }

    @Builder
    public record GenerationConfig(float temperature, double topP, int maxOutputTokens) {
    }

    @Builder
    public record SafetySetting(String category, String threshold) {
    }
}