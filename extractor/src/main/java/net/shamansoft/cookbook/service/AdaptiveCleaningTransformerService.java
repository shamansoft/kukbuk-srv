package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.Strategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Adaptive HTML cleaning transformer that implements a feedback loop between
 * the LLM and HTML cleaning strategies.
 *
 * <p>When the LLM returns {@code is_recipe=false} but with a high confidence that
 * the page might contain a recipe (i.e., {@code recipe_confidence >= threshold}),
 * this service retries with progressively less restrictive HTML cleaning strategies:
 * <pre>
 *   STRUCTURED_DATA (most restrictive)
 *     → SECTION_BASED
 *       → CONTENT_FILTER
 *         → FALLBACK (raw HTML, least restrictive)
 * </pre>
 *
 * <p>This solves the problem where over-aggressive cleaning removes the recipe content,
 * causing the LLM to incorrectly conclude the page has no recipe.
 *
 * <p>This service is {@link Primary} and owns the cleaning step.
 * It accepts <b>raw</b> (uncleaned) HTML and handles cleaning internally.
 * {@link ValidatingTransformerService} is used as the inner transformer for
 * validation + LLM retry logic on top of cleaned HTML.
 */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class AdaptiveCleaningTransformerService implements Transformer {

    private final ValidatingTransformerService innerTransformer;
    private final HtmlCleaner htmlCleaner;

    @Value("${recipe.adaptive-cleaning.enabled:true}")
    private boolean enabled;

    @Value("${recipe.adaptive-cleaning.confidence-threshold:0.5}")
    private double confidenceThreshold;

    /**
     * Transforms raw HTML to Recipe objects using an adaptive cleaning strategy loop.
     *
     * <p>Accepts raw (uncleaned) HTML. Cleaning is performed internally by this service.
     *
     * @param rawHtml   raw HTML content (not pre-cleaned)
     * @param sourceUrl the source URL of the recipe
     * @return the transformed result; may contain multiple recipes
     */
    @Override
    public Response transform(String rawHtml, String sourceUrl) {
        // Initial clean using the full cascade (picks the best strategy)
        HtmlCleaner.Results initial = htmlCleaner.process(rawHtml, sourceUrl);
        log.info("Initial HTML cleaning - URL: {}, {}", sourceUrl, initial.metricsMessage());

        Response result = innerTransformer.transform(initial.cleanedHtml(), sourceUrl);

        if (!enabled) {
            return result;
        }

        if (result.isRecipe()) {
            return result;
        }

        // If confidence is below threshold, LLM is sure it's not a recipe — no point retrying
        if (result.confidence() < confidenceThreshold) {
            log.debug("Confidence {:.2f} < threshold {:.2f} after {} strategy — not retrying",
                    result.confidence(), confidenceThreshold, initial.strategyUsed());
            return result;
        }

        // Adaptive retry: step through strategies in order, starting after the one already used
        List<Strategy> order = Strategy.ADAPTIVE_ORDER;
        int startIdx = order.indexOf(initial.strategyUsed()) + 1;

        for (int i = startIdx; i < order.size(); i++) {
            Strategy nextStrategy = order.get(i);

            log.info("Adaptive cleaning retry: confidence={:.2f} >= threshold={:.2f}, trying {} strategy for URL: {}",
                    result.confidence(), confidenceThreshold, nextStrategy, sourceUrl);

            HtmlCleaner.Results retryClean = htmlCleaner.processWithStrategy(rawHtml, sourceUrl, nextStrategy);
            result = innerTransformer.transform(retryClean.cleanedHtml(), sourceUrl);

            if (result.isRecipe()) {
                log.info("Recipe found after {} strategy for URL: {}", nextStrategy, sourceUrl);
                return result;
            }

            if (result.confidence() < confidenceThreshold) {
                log.info("Confidence {:.2f} dropped below threshold after {} strategy — stopping",
                        result.confidence(), nextStrategy);
                return result;
            }
        }

        log.info("All {} cleaning strategies exhausted for URL: {}", Strategy.ADAPTIVE_ORDER.size(), sourceUrl);
        return result;
    }
}
