package net.shamansoft.cookbook.service.youtube;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.TranscriptException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TranscriptServiceRouter {

    private final List<TranscriptService> providers;

    public TranscriptServiceRouter(List<TranscriptService> providers) {
        this.providers = providers;
        log.info("TranscriptServiceRouter initialized with {} providers: {}",
                providers.size(),
                providers.stream().map(TranscriptService::getProviderName).toList());
    }

    /**
     * Tries each transcript provider in order until one succeeds.
     * If all providers fail, throws TranscriptException with details.
     */
    public String getTranscript(String videoId) {
        if (providers.isEmpty()) {
            throw new TranscriptException("No transcript providers configured");
        }

        TranscriptException lastException = null;

        for (TranscriptService provider : providers) {
            try {
                log.debug("Trying transcript provider: {}", provider.getProviderName());
                String transcript = provider.getTranscript(videoId);
                log.info("Successfully fetched transcript from {} for video {}",
                        provider.getProviderName(), videoId);
                return transcript;
            } catch (TranscriptException e) {
                log.warn("Provider {} failed for video {}: {}",
                        provider.getProviderName(), videoId, e.getMessage());
                lastException = e;
            }
        }

        throw new TranscriptException(
                "All transcript providers failed for video: " + videoId, lastException);
    }
}
