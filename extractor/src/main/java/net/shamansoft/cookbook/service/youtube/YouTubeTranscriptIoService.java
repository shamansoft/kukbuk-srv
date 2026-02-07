package net.shamansoft.cookbook.service.youtube;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.TranscriptException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Service
@Order(1)
@Slf4j
public class YouTubeTranscriptIoService implements TranscriptService {

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeTranscriptIoService(
            @Qualifier("transcriptIoRestClient") RestClient restClient,
            @Value("${cookbook.transcript.youtube-transcript-io.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public String getTranscript(String videoId) {
        log.debug("Fetching transcript from youtube-transcript.io for video: {}", videoId);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/api/transcripts")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + apiKey)
                    .body(Map.of("ids", List.of(videoId)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("youtube-transcript.io API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TranscriptException(
                    "youtube-transcript.io API error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to call youtube-transcript.io API", e);
            throw new TranscriptException(
                    "youtube-transcript.io network error: " + e.getMessage(), e);
        }

        return extractTranscriptText(response, videoId);
    }

    @Override
    public String getProviderName() {
        return "youtube-transcript.io";
    }

    private String extractTranscriptText(JsonNode response, String videoId) {
        if (response == null || !response.isArray() || response.isEmpty()) {
            throw new TranscriptException(
                    "Empty response from youtube-transcript.io for video: " + videoId);
        }

        JsonNode videoData = response.get(0);
        if (!videoData.has("tracks") || videoData.get("tracks").isEmpty()) {
            throw new TranscriptException(
                    "No transcript tracks available for video: " + videoId);
        }

        // Use the first available track
        JsonNode track = videoData.get("tracks").get(0);
        if (!track.has("transcript") || track.get("transcript").isEmpty()) {
            throw new TranscriptException(
                    "Empty transcript in track for video: " + videoId);
        }

        StringBuilder transcript = new StringBuilder();
        JsonNode segments = track.get("transcript");
        for (int i = 0; i < segments.size(); i++) {
            JsonNode segment = segments.get(i);
            if (segment.has("text")) {
                if (!transcript.isEmpty()) {
                    transcript.append(" ");
                }
                transcript.append(segment.get("text").asText());
            }
        }

        String result = transcript.toString().trim();
        if (result.isEmpty()) {
            throw new TranscriptException(
                    "Transcript text is empty for video: " + videoId);
        }

        log.info("Retrieved transcript from youtube-transcript.io for video {} ({} chars)",
                videoId, result.length());
        return result;
    }
}
