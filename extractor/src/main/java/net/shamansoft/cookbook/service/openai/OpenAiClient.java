package net.shamansoft.cookbook.service.openai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal client for the OpenAI Responses API ({@code POST /responses}).
 * Local profile only — see {@link OpenAiRestTransformer}.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class OpenAiClient {

    private final RestClient openAiRestClient;
    private final ObjectMapper objectMapper;
    @Value("${cookbook.openai.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Sends one Responses API request and returns the model's output text.
     * Never throws for API-level failures; the reason is carried in {@link Result#errorMessage()}.
     */
    public Result request(Map<String, Object> body) {
        Objects.requireNonNull(body, "body cannot be null");

        JsonNode response;
        try {
            response = openAiRestClient.post()
                    .uri("/responses")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("OpenAI API HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Result.failure("OpenAI API error " + e.getStatusCode().value() + ": "
                    + errorMessage(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            return Result.failure("Network error: " + e.getMessage());
        }

        if (response == null) {
            return Result.failure("Empty response from OpenAI API");
        }
        log.debug("Full OpenAI response: {}", response.toPrettyString());

        String status = response.path("status").asText("");
        if (!"completed".equals(status)) {
            String reason = response.path("incomplete_details").path("reason").asText("unknown");
            log.warn("OpenAI response not completed - status: {}, reason: {}", status, reason);
            return Result.failure("OpenAI response status '" + status + "', reason: " + reason);
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue; // e.g. "reasoning" items
            }
            for (JsonNode part : item.path("content")) {
                String type = part.path("type").asText();
                if ("refusal".equals(type)) {
                    String refusal = part.path("refusal").asText();
                    log.error("OpenAI refused the request: {}", refusal);
                    return Result.failure("OpenAI refused the request: " + refusal);
                }
                if ("output_text".equals(type)) {
                    text.append(part.path("text").asText());
                }
            }
        }

        if (text.isEmpty()) {
            return Result.failure("No output text in OpenAI response");
        }
        log.info("OpenAI API request successful - Response length: {} chars", text.length());
        return Result.success(text.toString());
    }

    /** Pulls {@code error.message} out of an OpenAI error body, falling back to the raw body. */
    private String errorMessage(String responseBody) {
        try {
            String message = objectMapper.readTree(responseBody).path("error").path("message").asText("");
            return message.isEmpty() ? responseBody : message;
        } catch (Exception e) {
            return responseBody;
        }
    }

    public record Result(boolean success, String text, String errorMessage) {

        static Result success(String text) {
            return new Result(true, text, null);
        }

        static Result failure(String errorMessage) {
            return new Result(false, null, errorMessage);
        }
    }
}
