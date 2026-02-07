package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.exception.TranscriptException;

/**
 * Abstraction for fetching YouTube video transcripts from external APIs.
 */
public interface TranscriptService {

    /**
     * Fetches the transcript text for a given YouTube video.
     *
     * @param videoId the YouTube video ID (11 characters)
     * @return the transcript text
     * @throws TranscriptException if the transcript cannot be retrieved
     */
    String getTranscript(String videoId);

    /**
     * Returns the provider name for logging and diagnostics.
     */
    String getProviderName();
}
