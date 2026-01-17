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

@Service("geminiTransformer")
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
        return transformInternal(htmlContent, null, null);
    }

    /**
     * Transforms HTML content to YAML with validation feedback from a previous attempt.
     *
     * @param htmlContent the HTML string to transform
     * @param previousYaml the YAML from the previous attempt that failed validation
     * @param validationError the validation error message to help correct the issue
     * @return the transformed result
     */
    public Response transformWithFeedback(String htmlContent, String previousYaml, String validationError) {
        return transformInternal(htmlContent, previousYaml, validationError);
    }

    private Response transformInternal(String htmlContent, String previousYaml, String validationError) {
        try {
            log.info("Calling Gemini API - Model: {}, Has feedback: {}, HTML length: {} chars",
                model,
                previousYaml != null,
                htmlContent.length());

            String url = "/models/%s:generateContent?key=%s".formatted(model, apiKey);
            String body;
            if (previousYaml != null && validationError != null) {
                body = requestBuilder.buildBodyStringWithFeedback(htmlContent, previousYaml, validationError);
                log.debug("Retrying transformation with validation feedback. Request body size: {} chars", body.length());
            } else {
                body = requestBuilder.buildBodyString(htmlContent);
                log.debug("Initial transformation request. Request body size: {} chars", body.length());
            }

            JsonNode response = geminiWebClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            log.debug("Gemini API response received - Has candidates: {}",
                response != null && response.has("candidates"));
            if (response != null) {
                log.debug("Full Gemini response: {}", response.toPrettyString());
            }

            if (response != null && response.has("candidates")) {
                JsonNode candidate = response.get("candidates").get(0);

                // Log finish reason to detect truncation
                if (candidate.has("finishReason")) {
                    String finishReason = candidate.get("finishReason").asText();
                    log.info("Gemini finishReason: {}", finishReason);
                    if (!"STOP".equals(finishReason)) {
                        log.warn("Gemini response may be truncated - finishReason: {}", finishReason);
                    }
                }

                // Log safety ratings if present
                if (candidate.has("safetyRatings")) {
                    log.info("Gemini safetyRatings: {}", candidate.get("safetyRatings").toPrettyString());
                }

                // Check if response was blocked
                if (response.has("promptFeedback")) {
                    JsonNode feedback = response.get("promptFeedback");
                    log.warn("Gemini promptFeedback: {}", feedback.toPrettyString());
                    if (feedback.has("blockReason")) {
                        log.error("Gemini response BLOCKED - Reason: {}", feedback.get("blockReason").asText());
                    }
                }

                // Check how many parts are in the response
                JsonNode parts = candidate.get("content").get("parts");
                int partCount = parts.size();
                log.info("Gemini response has {} part(s)", partCount);

                // Concatenate all parts if there are multiple
                StringBuilder rawTextBuilder = new StringBuilder();
                for (int i = 0; i < partCount; i++) {
                    if (parts.get(i).has("text")) {
                        String partText = parts.get(i).get("text").asText();
                        rawTextBuilder.append(partText);
                        log.info("Part[{}] text - Length: {} chars", i, partText.length());
                    }
                }

                String rawText = rawTextBuilder.toString();
                log.info("Combined raw text from Gemini (before cleanup) - Total length: {} chars, Last 100 chars: {}",
                    rawText.length(),
                    rawText.substring(Math.max(0, rawText.length() - 100)));

                String yamlContent = cleanup(rawText);

                log.info("Extracted YAML from Gemini (after cleanup) - Length: {} chars, First 200 chars: {}",
                    yamlContent.length(),
                    yamlContent.substring(0, Math.min(200, yamlContent.length())));

                // Check if the YAML indicates that it is not a recipe
                boolean isRecipe = !yamlContent.contains("is_recipe: false");
                log.warn("Gemini recipe classification - Is Recipe: {}, Contains 'is_recipe: false': {}",
                    isRecipe,
                    yamlContent.contains("is_recipe: false"));
                return new Response(isRecipe, yamlContent);
            } else {
                log.error("Invalid Gemini API response structure: {}", response != null ? response.toPrettyString() : "null");
                throw new ClientException("Invalid Gemini response: %s".formatted(response));
            }
        } catch (Exception e) {
            log.error("Failed to transform content via Gemini API - Model: {}, HTML length: {}, Error: {}",
                model,
                htmlContent.length(),
                e.getMessage(),
                e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
    }

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }
}