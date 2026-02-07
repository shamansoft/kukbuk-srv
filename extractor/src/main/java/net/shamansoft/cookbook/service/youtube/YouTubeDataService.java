package net.shamansoft.cookbook.service.youtube;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.YouTubeApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

@Service
@Slf4j
public class YouTubeDataService {

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeDataService(
            @Qualifier("youtubeApiRestClient") RestClient restClient,
            @Value("${cookbook.youtube.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    /**
     * Fetches video metadata from the YouTube Data API v3.
     *
     * @param videoId the YouTube video ID (11 characters)
     * @return metadata for the video
     * @throws YouTubeApiException if the metadata cannot be retrieved
     */
    public YouTubeMetadata getMetadata(String videoId) {
        log.debug("Fetching metadata from YouTube Data API for video: {}", videoId);

        if (videoId == null || videoId.isBlank()) {
            throw new YouTubeApiException("Video ID must not be null or blank");
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/videos")
                            .queryParam("id", videoId)
                            .queryParam("part", "snippet")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("YouTube Data API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new YouTubeApiException(
                    "YouTube Data API error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Failed to call YouTube Data API", e);
            throw new YouTubeApiException(
                    "YouTube Data API network error: " + e.getMessage(), e);
        }

        return extractMetadata(response, videoId);
    }

    private YouTubeMetadata extractMetadata(JsonNode response, String videoId) {
        if (response == null || !response.has("items")) {
            throw new YouTubeApiException(
                    "Invalid response from YouTube Data API for video: " + videoId);
        }

        JsonNode items = response.get("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new YouTubeApiException(
                    "Video not found: " + videoId);
        }

        JsonNode snippet = items.get(0).get("snippet");
        if (snippet == null) {
            throw new YouTubeApiException(
                    "No snippet data for video: " + videoId);
        }

        String title = snippet.has("title") ? snippet.get("title").asText() : "";
        String description = snippet.has("description") ? snippet.get("description").asText() : "";
        String channelName = snippet.has("channelTitle") ? snippet.get("channelTitle").asText() : "";
        Instant publishedAt = snippet.has("publishedAt")
                ? Instant.parse(snippet.get("publishedAt").asText())
                : null;

        log.info("Retrieved metadata from YouTube Data API for video {} (title: {})",
                videoId, title);

        return new YouTubeMetadata(title, description, channelName, publishedAt);
    }
}
