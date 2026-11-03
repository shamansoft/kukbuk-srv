package net.shamansoft.cookbook.service.gemini;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    @Value("${cookbook.gemini.api-key}")
    private String apiKey;
    @Value("${cookbook.gemini.model}")
    private String model;

    private String url;

    @PostConstruct
    public void init() {
        this.url = "/models/%s:generateContent".formatted(model);
    }

    public <T> GeminiResponse<T> request(GeminiRequest geminiRequest, Class<T> clazz) {
        return request(geminiRequest, clazz, null);
    }

    /**
     * Debug-only entry point: same as {@link #request(GeminiRequest, Class)} but allows
     * overriding the target model for tuning. Resolves the URL into a local variable rather
     * than mutating the shared {@code url}/{@code model} fields, since this bean is a singleton
     * and requests may run concurrently.
     */
    public <T> GeminiResponse<T> request(GeminiRequest geminiRequest, Class<T> clazz, String modelOverride) {
        // Null safety checks
        Objects.requireNonNull(geminiRequest, "geminiRequest cannot be null");
        Objects.requireNonNull(clazz, "clazz cannot be null");

        String requestUrl = modelOverride != null
                ? "/models/%s:generateContent".formatted(modelOverride)
                : this.url;

        JsonNode response;
        try {
            response = geminiRestClient.post()
                    .uri(requestUrl)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .body(objectMapper.writeValueAsString(geminiRequest))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Gemini API HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return GeminiResponse.failure(GeminiResponse.Code.OTHER,
                    "Gemini API error: " + e.getStatusCode() + " - " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to call Gemini API", e);
            return GeminiResponse.failure(GeminiResponse.Code.OTHER,
                    "Network error: " + e.getMessage());
        }

        log.debug("Gemini API response received - Has candidates: {}",
                response != null && response.has("candidates"));
        if (response != null) {
            log.debug("Full Gemini response: {}", response.toPrettyString());
        }

        // A blocked prompt comes back with promptFeedback and usually no candidates
        if (response != null && response.has("promptFeedback")) {
            JsonNode feedback = response.get("promptFeedback");
            log.warn("Gemini promptFeedback: {}", feedback.toPrettyString());
            if (feedback.has("blockReason")) {
                log.error("Gemini response BLOCKED - Reason: {}", feedback.get("blockReason").asText());
                return GeminiResponse.failure(GeminiResponse.Code.BLOCKED, feedback.get("blockReason").asText());
            }
        }

        if (response != null && response.has("candidates") && !response.get("candidates").isEmpty()) {
            JsonNode candidate = response.get("candidates").get(0);

            String finishReason = candidate.path("finishReason").asText("");
            log.debug("Gemini finishReason: {}", finishReason);
            if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
                log.warn("Gemini response may be incomplete - finishReason: {}", finishReason);
            }

            // Log safety ratings if present
            if (candidate.has("safetyRatings")) {
                log.debug("Gemini safetyRatings: {}", candidate.get("safetyRatings").toPrettyString());
            }

            // RECITATION, SAFETY and some OTHER stops return a candidate without content
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                log.error("Gemini returned no content - finishReason: {}", finishReason);
                return GeminiResponse.failure(GeminiResponse.Code.NO_CONTENT,
                        "Gemini returned no content (finishReason: " + finishReason + ")");
            }
            int partCount = parts.size();
            log.debug("Gemini response has {} part(s)", partCount);

            // Concatenate all parts if there are multiple
            StringBuilder jsonBuilder = new StringBuilder();
            for (int i = 0; i < partCount; i++) {
                if (parts.get(i).has("text")) {
                    String partText = parts.get(i).get("text").asText();
                    jsonBuilder.append(partText);
                    log.debug("Part[{}] text - Length: {} chars", i, partText.length());
                }
            }

            String jsonContent = jsonBuilder.toString();
            if (jsonContent.isBlank()) {
                log.error("Gemini returned only empty text - finishReason: {}", finishReason);
                return GeminiResponse.failure(GeminiResponse.Code.NO_CONTENT,
                        "Gemini returned empty text (finishReason: " + finishReason + ")");
            }
            log.debug("Combined JSON from Gemini - Total length: {} chars, First 200 chars: {}",
                    jsonContent.length(),
                    jsonContent.substring(0, Math.min(200, jsonContent.length())));
            JsonNode usage = response.path("usageMetadata");
            log.info("Gemini API request successful - Response length: {} chars, tokens in/out: {}/{}",
                    jsonContent.length(), usage.path("promptTokenCount").asInt(-1),
                    usage.path("candidatesTokenCount").asInt(-1));
            try {
                return GeminiResponse.success(objectMapper.readValue(jsonContent, clazz), jsonContent);
            } catch (JacksonException e) {
                if ("MAX_TOKENS".equals(finishReason)) {
                    log.error("Gemini output cut off at the token limit ({} chars)", jsonContent.length());
                    return GeminiResponse.failure(GeminiResponse.Code.TRUNCATED,
                            "Gemini output hit the token limit and is incomplete (" + jsonContent.length() + " chars)");
                }
                if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
                    log.error("Gemini stopped early ({}) with incomplete output ({} chars)", finishReason, jsonContent.length());
                    return GeminiResponse.failure(GeminiResponse.Code.NO_CONTENT,
                            "Gemini stopped early (finishReason: " + finishReason + ") and the output is incomplete");
                }
                log.error("Invalid JSON structure received from Gemini API", e);
                return GeminiResponse.failure(GeminiResponse.Code.PARSE_ERROR,
                        "Invalid JSON structure received from Gemini API: " + e.getMessage());
            }
        }
        return GeminiResponse.failure(GeminiResponse.Code.OTHER, "No candidates in Gemini response");
    }
}
