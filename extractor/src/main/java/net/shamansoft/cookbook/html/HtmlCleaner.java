package net.shamansoft.cookbook.html;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import net.shamansoft.cookbook.html.strategy.CleanupStrategy;
import net.shamansoft.cookbook.html.strategy.Strategy;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * HTML preprocessing service that reduces HTML size before sending to LLM.
 * Implements a hybrid strategy cascade:
 * 1. Structured data extraction (JSON-LD schema.org/Recipe)
 * 2. Section-based extraction (keyword scoring)
 * 3. Content filtering (remove unwanted elements)
 * 4. Fallback to raw HTML if all strategies fail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HtmlCleaner {

    private final HtmlCleanupConfig config;
    private final MeterRegistry meterRegistry;
    private final java.util.List<CleanupStrategy> strategies;

    /**
     * Preprocess HTML using the hybrid strategy cascade.
     *
     * @param html Raw HTML content
     * @param url  URL of the page (for logging)
     * @return PreprocessingResult with cleaned HTML and metrics
     */
    public Results process(String html, String url) {

        int originalSize = html != null ? html.length() : 0;

        // Validation
        if (html == null || html.isBlank()) {
            log.warn("Empty HTML input for URL: {}", url);
            return buildResult("", 0, Strategy.FALLBACK);
        }

        if (!config.isEnabled()) {
            return buildResult(html, originalSize, Strategy.DISABLED);
        }

        try {
            // Iterate strategy implementations in order. Each strategy returns Optional<String>
            for (var strategy : strategies) {
                try {
                    Optional<String> out = strategy.clean(html);
                    if (out.isPresent()) {
                        var s = strategy.getStrategy();
                        log.debug("Using {} strategy for URL: {}", s, url);
                        return buildResult(out.get(), originalSize, s);
                    }
                } catch (Exception e) {
                    log.debug("Strategy {} failed, continuing to next", strategy.getClass().getSimpleName(), e);
                }
            }

            // Fallback: Use raw HTML if all strategies produce nothing
            log.debug("Falling back to raw HTML for URL: {}", url);
            return buildResult(html, originalSize, Strategy.FALLBACK);

        } catch (Exception e) {
            log.error("HTML preprocessing failed for URL: {}, using raw HTML", url, e);
            meterRegistry.counter("html.preprocessing.errors",
                    "error_type", e.getClass().getSimpleName()).increment();
            return buildResult(html, originalSize, Strategy.FALLBACK);
        }
    }

    /**
     * Build a preprocessing result with metrics.
     */
    private Results buildResult(String cleanedHtml, int originalSize, net.shamansoft.cookbook.html.strategy.Strategy strategy) {
        int cleanedSize = cleanedHtml.length();
        double reductionRatio = originalSize > 0
                ? (double) (originalSize - cleanedSize) / originalSize
                : 0.0;

        String message = String.format(
                "Strategy: %s, %d → %d chars (%.1f%% reduction)",
                strategy, originalSize, cleanedSize, reductionRatio * 100
        );

        // Emit metrics
        emitMetrics(strategy, originalSize, cleanedSize, reductionRatio);

        return new Results(
                cleanedHtml,
                originalSize,
                cleanedSize,
                reductionRatio,
                strategy,
                message
        );
    }

    /**
     * Emit preprocessing metrics to the meter registry.
     */
    private void emitMetrics(net.shamansoft.cookbook.html.strategy.Strategy strategy, int originalSize, int cleanedSize, double reductionRatio) {
        // Counter: Which strategies are being used
        meterRegistry.counter("html.preprocessing.strategy",
                "type", strategy.name()).increment();

        // Gauge: Current reduction ratio (0.0-1.0)
        meterRegistry.gauge("html.preprocessing.reduction_ratio", reductionRatio);

        // Distribution summaries: HTML sizes
        meterRegistry.summary("html.preprocessing.original_size").record(originalSize);
        meterRegistry.summary("html.preprocessing.cleaned_size").record(cleanedSize);
    }

    // removed legacy HtmlCleaner.Strategy enum; use net.shamansoft.cookbook.html.strategy.Strategy

    /**
     * Preprocessing result containing cleaned HTML and metrics.
     */
    public record Results(
            String cleanedHtml,
            int originalSize,
            int cleanedSize,
            double reductionRatio,
            net.shamansoft.cookbook.html.strategy.Strategy strategyUsed,
            String metricsMessage
    ) {
    }
}
