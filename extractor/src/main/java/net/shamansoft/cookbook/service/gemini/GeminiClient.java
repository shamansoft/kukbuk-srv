package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.annotation.PostConstruct;
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
        // Null safety checks
        Objects.requireNonNull(geminiRequest, "geminiRequest cannot be null");
        Objects.requireNonNull(clazz, "clazz cannot be null");

        JsonNode response;
        try {
            response = geminiRestClient.post()
                    .uri(url)
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

        if (response != null && response.has("candidates") && !response.get("candidates").isEmpty()) {
            JsonNode candidate = response.get("candidates").get(0);

            // Log finish reason to detect truncation
            if (candidate.has("finishReason")) {
                String finishReason = candidate.get("finishReason").asText();
                log.debug("Gemini finishReason: {}", finishReason);
                if (!"STOP".equals(finishReason)) {
                    log.warn("Gemini response may be truncated - finishReason: {}", finishReason);
                }
            }

            // Log safety ratings if present
            if (candidate.has("safetyRatings")) {
                log.debug("Gemini safetyRatings: {}", candidate.get("safetyRatings").toPrettyString());
            }

            // Check if response was blocked
            if (response.has("promptFeedback")) {
                JsonNode feedback = response.get("promptFeedback");
                log.warn("Gemini promptFeedback: {}", feedback.toPrettyString());
                if (feedback.has("blockReason")) {
                    log.error("Gemini response BLOCKED - Reason: {}", feedback.get("blockReason").asText());
                    return GeminiResponse.failure(GeminiResponse.Code.BLOCKED, feedback.get("blockReason").asText());
                }
            }

            // Check how many parts are in the response
            JsonNode parts = candidate.get("content").get("parts");
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
            log.debug("Combined JSON from Gemini - Total length: {} chars, First 200 chars: {}",
                    jsonContent.length(),
                    jsonContent.substring(0, Math.min(200, jsonContent.length())));
            log.info("Gemini API request successful - Response length: {} chars", jsonContent.length());
            try {
                return GeminiResponse.success(objectMapper.readValue(jsonContent, clazz));
            } catch (JsonProcessingException e) {
                log.error("Invalid JSON structure received from Gemini API", e);
                return GeminiResponse.failure(GeminiResponse.Code.PARSE_ERROR,
                        "Invalid JSON structure received from Gemini API: " + e.getMessage());
            }
        }
        return GeminiResponse.failure(GeminiResponse.Code.OTHER, "No candidates in Gemini response");
    }
}
