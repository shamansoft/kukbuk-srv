package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.CleanupService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final WebClient geminiWebClient;
    private final CleanupService cleanupService;
    private final RequestBuilder requestBuilder;

    @Value("${cookbook.gemini.api-key}")
    private String apiKey;
    @Value("${cookbook.gemini.model}")
    private String model;

    @Override
    public Response transform(String htmlContent) {
        try {
            String url = "/models/%s:generateContent?key=%s".formatted(model, apiKey);
            String body = requestBuilder.buildBodyString(htmlContent);
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
            } else {
                throw new ClientException("Invalid Gemini response: %s".formatted(response));
            }
        } catch (Exception e) {
            log.error("Failed to transform content via Gemini API", e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
    }

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }
}