package net.shamansoft.cookbook.service.youtube;

import java.time.Instant;

/**
 * Metadata extracted from a YouTube video via the YouTube Data API v3.
 */
public record YouTubeMetadata(
        String title,
        String description,
        String channelName,
        Instant publishedAt
) {
}
