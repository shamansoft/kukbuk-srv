package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GeminiRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesGeminiRequestWithAllFields() throws Exception {
        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text("Sample text")
                                                .build()))
                                .build()))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .temperature(0.7f)
                        .topP(0.8)
                        .maxOutputTokens(1024)
                        .build())
                .safetySettings(List.of(
                        GeminiRequest.SafetySetting.builder()
                                .category("HARM_CATEGORY")
                                .threshold("BLOCK_LOW_AND_ABOVE")
                                .build()))
                .build();

        String json = objectMapper.writeValueAsString(request);
        assertThat(json).contains("\"contents\":");
        assertThat(json).contains("\"generationConfig\":");
        assertThat(json).contains("\"safetySettings\":");
        assertThat(json).contains("\"text\":\"Sample text\"");
    }

    @Test
    void serializesGeminiRequestWithMinimalFields() throws Exception {
        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text("Simple prompt")
                                                .build()))
                                .build()))
                .build();

        String json = objectMapper.writeValueAsString(request);
        assertThat(json).contains("\"contents\":");
        assertThat(json).contains("\"text\":\"Simple prompt\"");
    }

    @Test
    void deserializesGeminiRequestFromJson() throws Exception {
        String json = """
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "text": "Sample text"
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.7,
                    "topP": 0.8,
                    "maxOutputTokens": 1024
                  }
                }
                """;

        GeminiRequest request = objectMapper.readValue(json, GeminiRequest.class);

        assertThat(request.contents()).hasSize(1);
        assertThat(request.contents().get(0).parts().get(0).text()).isEqualTo("Sample text");
        assertThat(request.generationConfig().temperature()).isEqualTo(0.7f);
    }
}