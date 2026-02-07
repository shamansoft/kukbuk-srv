package net.shamansoft.cookbook.service.youtube;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.TranscriptException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Service
@Slf4j
public class SupadataTranscriptService implements TranscriptService {

    static final int MAX_POLL_ATTEMPTS = 60;
    static final long POLL_INTERVAL_MS = 2000;

    private final RestClient restClient;
    private final String apiKey;

    public SupadataTranscriptService(
            @Qualifier("supadataRestClient") RestClient restClient,
            @Value("${cookbook.transcript.supadata.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public String getTranscript(String videoId) {
        log.debug("Fetching transcript from supadata.ai for video: {}", videoId);

        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

        JsonNode response;
        HttpStatusCode statusCode;
        try {
            var responseEntity = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/transcript")
                            .queryParam("url", videoUrl)
                            .queryParam("text", true)
                            .build())
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .toEntity(JsonNode.class);
            response = responseEntity.getBody();
            statusCode = responseEntity.getStatusCode();
        } catch (RestClientResponseException e) {
            log.error("supadata.ai API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TranscriptException(
                    "supadata.ai API error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to call supadata.ai API", e);
            throw new TranscriptException(
                    "supadata.ai network error: " + e.getMessage(), e);
        }

        // Handle async job (HTTP 202)
        if (statusCode.value() == 202) {
            return handleAsyncJob(response, videoId);
        }

        return extractContent(response, videoId);
    }

    @Override
    public String getProviderName() {
        return "supadata.ai";
    }

    private String handleAsyncJob(JsonNode response, String videoId) {
        if (response == null || !response.has("jobId")) {
            throw new TranscriptException(
                    "supadata.ai returned 202 but no jobId for video: " + videoId);
        }

        String jobId = response.get("jobId").asText();
        log.info("supadata.ai async job created: {} for video: {}", jobId, videoId);

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranscriptException(
                        "Interrupted while waiting for supadata.ai job: " + jobId, e);
            }

            JsonNode jobResponse;
            try {
                jobResponse = restClient.get()
                        .uri("/v1/transcript/{jobId}", jobId)
                        .header("x-api-key", apiKey)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                log.warn("supadata.ai job poll error: {} for job: {}", e.getStatusCode(), jobId);
                continue;
            } catch (Exception e) {
                log.warn("supadata.ai job poll network error for job: {}", jobId, e);
                continue;
            }

            if (jobResponse == null || !jobResponse.has("status")) {
                continue;
            }

            String status = jobResponse.get("status").asText();
            log.debug("supadata.ai job {} status: {} (attempt {})", jobId, status, attempt + 1);

            switch (status) {
                case "completed" -> {
                    return extractContent(jobResponse, videoId);
                }
                case "failed" -> throw new TranscriptException(
                        "supadata.ai job failed for video: " + videoId);
                default -> {
                    // queued or active - keep polling
                }
            }
        }

        throw new TranscriptException(
                "supadata.ai job timed out after " + MAX_POLL_ATTEMPTS + " attempts for video: " + videoId);
    }

    private String extractContent(JsonNode response, String videoId) {
        if (response == null || !response.has("content")) {
            throw new TranscriptException(
                    "No content in supadata.ai response for video: " + videoId);
        }

        String content = response.get("content").asText();
        if (content == null || content.isBlank()) {
            throw new TranscriptException(
                    "Empty transcript content from supadata.ai for video: " + videoId);
        }

        log.info("Retrieved transcript from supadata.ai for video {} ({} chars)",
                videoId, content.length());
        return content;
    }
}
