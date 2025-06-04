package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.CleanupService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final WebClient geminiWebClient;
    private final CleanupService cleanupService;
    private final Prompt prompt;
    private final ObjectMapper objectMapper;
    @Value("${cookbook.gemini.api-key}")
    private String apiKey;
    @Value("${cookbook.gemini.model}")
    private String model;
    @Value("${cookbook.gemini.temperature}")
    private float temperature;
    @Value("${cookbook.gemini.top-p}")
    private float topP;

    @Override
    public Response transform(String htmlContent) {
        try {
            String url = "/models/%s:generateContent?key=%s".formatted(model, apiKey);
            String body = buildRequestJson(htmlContent);
            JsonNode response = geminiWebClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("candidates")) {
                String yamlContent = cleanup(response.get("candidates").get(0)
                        .get("content").get("parts").get(0)
                        .get("text").asText());

                // Check if the YAML indicates that it is not a recipe
                boolean isRecipe = !yamlContent.contains("is_recipe: false");

                return new Response(isRecipe, yamlContent);
            }
        } catch (Exception e) {
            log.error("Failed to transform content via Gemini API", e);
        }
        return new Response(false, "Could not transform content. Try again later.");
    }

    String buildRequestJson(String htmlContent) throws JsonProcessingException {
        String fullPrompt = prompt.withHtml(htmlContent);
        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text(fullPrompt)
                                                .build()
                                ))
                                .build()
                ))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .temperature(temperature)
                        .topP(topP)
                        .maxOutputTokens(4096)
                        .build())
                .safetySettings(List.of(
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT").threshold("BLOCK_NONE").build()
                ))
                .build();
        return objectMapper.writeValueAsString(request);
    }

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }
}