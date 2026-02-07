package net.shamansoft.cookbook.service.youtube;

/**
 * Input data for YouTube recipe extraction.
 *
 * @param videoUrl the full YouTube URL
 * @param videoId  the extracted video ID (11 characters)
 */
public record YouTubeInput(
        String videoUrl,
        String videoId
) {
}
