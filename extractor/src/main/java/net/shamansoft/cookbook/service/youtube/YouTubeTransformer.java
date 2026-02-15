package net.shamansoft.cookbook.service.youtube;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.TransformerWithFeedback;
import net.shamansoft.cookbook.service.gemini.GeminiClient;
import net.shamansoft.cookbook.service.gemini.GeminiRequest;
import net.shamansoft.cookbook.service.gemini.GeminiResponse;
import net.shamansoft.cookbook.service.gemini.RequestBuilder;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("youtubeTransformer")
@Slf4j
public class YouTubeTransformer implements TransformerWithFeedback {

    private static final Pattern YOUTUBE_LONG_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:youtube\\.com|m\\.youtube\\.com)/watch\\?.*v=([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORT_URL = Pattern.compile(
            "(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_EMBED_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORTS_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})");

    private final YouTubeDataService youTubeDataService;
    private final TranscriptServiceRouter transcriptServiceRouter;
    private final GeminiClient geminiClient;
    private final RequestBuilder requestBuilder;

    public YouTubeTransformer(
            YouTubeDataService youTubeDataService,
            TranscriptServiceRouter transcriptServiceRouter,
            GeminiClient geminiClient,
            RequestBuilder requestBuilder) {
        this.youTubeDataService = youTubeDataService;
        this.transcriptServiceRouter = transcriptServiceRouter;
        this.geminiClient = geminiClient;
        this.requestBuilder = requestBuilder;
    }

    /**
     * Transforms YouTube video content to a Recipe.
     * The sourceUrl must be a valid YouTube URL. The htmlContent parameter is ignored -
     * metadata and transcript are fetched directly from YouTube APIs.
     *
     * @param htmlContent ignored for YouTube transformation
     * @param sourceUrl   the YouTube video URL
     * @return the transformed result
     */
    @Override
    public Response transform(String htmlContent, String sourceUrl) {
        Objects.requireNonNull(sourceUrl, "sourceUrl cannot be null");

        String videoId = extractVideoId(sourceUrl);
        log.info("Starting YouTube recipe extraction for video: {} (ID: {})", sourceUrl, videoId);

        YouTubeMetadata metadata = youTubeDataService.getMetadata(videoId);
        log.debug("Fetched metadata - title: {}, channel: {}", metadata.title(), metadata.channelName());

        String transcript = transcriptServiceRouter.getTranscript(videoId);
        log.debug("Fetched transcript - length: {} chars", transcript.length());

        return transformInternal(metadata, transcript, null, null);
    }

    /**
     * Transforms YouTube content with validation feedback from a previous attempt.
     *
     * @param htmlContent     ignored for YouTube transformation
     * @param sourceUrl       the YouTube video URL
     * @param previousRecipe  the Recipe from the previous attempt that failed validation
     * @param validationError the validation error message to help correct the issue
     * @return the transformed result
     */
    @Override
    public Response transformWithFeedback(String htmlContent, String sourceUrl,
                                          Recipe previousRecipe, String validationError) {
        Objects.requireNonNull(sourceUrl, "sourceUrl cannot be null");
        Objects.requireNonNull(previousRecipe, "previousRecipe cannot be null for feedback");
        Objects.requireNonNull(validationError, "validationError cannot be null for feedback");

        String videoId = extractVideoId(sourceUrl);
        log.info("Retrying YouTube recipe extraction with validation feedback for video: {} (ID: {})",
                sourceUrl, videoId);

        YouTubeMetadata metadata = youTubeDataService.getMetadata(videoId);
        String transcript = transcriptServiceRouter.getTranscript(videoId);

        return transformInternal(metadata, transcript, previousRecipe, validationError);
    }

    private Response transformInternal(YouTubeMetadata metadata, String transcript,
                                       Recipe previousRecipe, String validationError) {
        try {
            GeminiRequest request;
            if (previousRecipe != null && validationError != null) {
                request = requestBuilder.buildYouTubeRecipeRequestWithFeedback(
                        metadata.title(), metadata.description(), transcript,
                        previousRecipe, validationError);
                log.debug("Retrying YouTube transformation with validation feedback");
            } else {
                request = requestBuilder.buildYouTubeRecipeRequest(
                        metadata.title(), metadata.description(), transcript);
                log.debug("Initial YouTube transformation request");
            }

            GeminiResponse<Recipe> geminiResponse = geminiClient.request(request, Recipe.class);

            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                log.info("YouTube recipe extraction completed - isRecipe: {}",
                        geminiResponse.data().isRecipe());
                return Response.withRawResponse(
                        geminiResponse.data().isRecipe(),
                        geminiResponse.data(),
                        geminiResponse.rawResponse()
                );
            } else {
                log.error("Gemini Client returned error code: {} for YouTube video",
                        geminiResponse.code());
                throw new ClientException("Gemini Client returned error code: " + geminiResponse.code());
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare request for Gemini API. Error: {}",
                    e.getMessage(), e);
            throw new ClientException("Failed to transform YouTube content via Gemini API", e);
        }
    }

    /**
     * Extracts a YouTube video ID from various URL formats.
     * Supports: youtube.com/watch?v=, youtu.be/, youtube.com/embed/, youtube.com/shorts/
     *
     * @param url the YouTube URL
     * @return the 11-character video ID
     * @throws IllegalArgumentException if the URL format is not recognized
     */
    public static String extractVideoId(String url) {
        Objects.requireNonNull(url, "URL cannot be null");

        for (Pattern pattern : List.of(YOUTUBE_LONG_URL, YOUTUBE_SHORT_URL, YOUTUBE_EMBED_URL, YOUTUBE_SHORTS_URL)) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        throw new IllegalArgumentException("Invalid YouTube URL: " + url);
    }

}
