package net.shamansoft.cookbook.html;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static void cleanupAttributes(Element doc) {
        for (Element el : doc.getAllElements()) {
            el.removeAttr("style");
            el.removeAttr("class");
            el.removeAttr("id");
            // Remove data-* attributes
            var dataAttrs = el.attributes().asList().stream()
                    .map(Attribute::getKey)
                    .filter(key -> key.startsWith("data-"))
                    .filter(key -> key.startsWith("on"))
                    .toList();
            dataAttrs.forEach(el::removeAttr);
        }
    }

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
            // Strategy 1: Structured Data
            if (config.getStructuredData().isEnabled()) {
                Optional<String> structured = extractStructuredData(html);
                if (structured.isPresent()) {
                    log.debug("Using STRUCTURED_DATA strategy for URL: {}", url);
                    return buildResult(structured.get(), originalSize, Strategy.STRUCTURED_DATA);
                }
            }

            // Strategy 2: Section-based extraction
            if (config.getSectionBased().isEnabled()) {
                Optional<String> sections = extractRecipeSections(html);
                if (sections.isPresent()) {
                    String sectionHtml = sections.get();
                    if (meetsConfidence(sectionHtml) &&
                            sectionHtml.length() >= config.getContentFilter().getMinOutputSize()) {
                        log.debug("Using SECTION_BASED strategy for URL: {}", url);
                        return buildResult(sectionHtml, originalSize, Strategy.SECTION_BASED);
                    }
                }
            }

            // Strategy 3: Content filtering
            String filtered = filterUnwantedContent(html);
            if (filtered.length() >= config.getContentFilter().getMinOutputSize()) {
                log.debug("Using CONTENT_FILTER strategy for URL: {}", url);
                return buildResult(filtered, originalSize, Strategy.CONTENT_FILTER);
            }

            // Fallback: Use raw HTML if output too small
            log.debug("Falling back to raw HTML for URL: {} (filtered size: {} chars)",
                    url, filtered.length());
            return buildResult(html, originalSize, Strategy.FALLBACK);

        } catch (Exception e) {
            log.error("HTML preprocessing failed for URL: {}, using raw HTML", url, e);
            meterRegistry.counter("html.preprocessing.errors",
                    "error_type", e.getClass().getSimpleName()).increment();
            return buildResult(html, originalSize, Strategy.FALLBACK);
        }
    }

    /**
     * Extract and validate JSON-LD schema.org/Recipe structured data.
     */
    private Optional<String> extractStructuredData(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Elements scripts = doc.select("script[type=application/ld+json]");

            for (Element script : scripts) {
                try {
                    JsonNode json = objectMapper.readTree(script.html());
                    List<JsonNode> candidates = findRecipeNodes(json);

                    for (JsonNode node : candidates) {
                        if (isRecipeType(node)) {
                            int completeness = scoreCompleteness(node);
                            if (completeness >= config.getStructuredData().getMinCompleteness()) {
                                log.debug("Found structured recipe data, completeness: {}%", completeness);
                                String jsonString = objectMapper.writeValueAsString(node);
                                return Optional.of(jsonString);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Invalid JSON-LD in script tag, skipping", e);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing HTML for structured data", e);
        }
        return Optional.empty();
    }

    /**
     * Find all Recipe nodes in JSON-LD (handles @graph arrays).
     */
    private List<JsonNode> findRecipeNodes(JsonNode json) {
        List<JsonNode> candidates = new ArrayList<>();

        // Handle @graph array
        if (json.has("@graph") && json.get("@graph").isArray()) {
            json.get("@graph").forEach(candidates::add);
        } else {
            candidates.add(json);
        }

        return candidates;
    }

    /**
     * Check if JSON node is a Recipe type.
     */
    private boolean isRecipeType(JsonNode node) {
        if (node.has("@type")) {
            JsonNode type = node.get("@type");
            if (type.isTextual()) {
                return "Recipe".equals(type.asText());
            } else if (type.isArray()) {
                for (JsonNode t : type) {
                    if ("Recipe".equals(t.asText())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Score completeness of structured recipe data (0-100).
     */
    private int scoreCompleteness(JsonNode recipe) {
        int score = 0;

        // Required fields (20 points each)
        if (recipe.has("name")) score += 20;
        if (recipe.has("recipeIngredient")) score += 20;
        if (recipe.has("recipeInstructions")) score += 20;

        // Nice-to-have fields (10 points each)
        if (recipe.has("totalTime")) score += 10;
        if (recipe.has("recipeYield")) score += 10;
        if (recipe.has("description")) score += 10;
        if (recipe.has("image")) score += 10;

        return Math.min(100, score);
    }

    /**
     * Check if structured data meets minimum completeness threshold.
     */
    private boolean isComplete(String structuredData) {
        // For now, just check minimum safe size
        return structuredData != null &&
                structuredData.length() >= config.getFallback().getMinSafeSize();
    }

    /**
     * Extract recipe sections using keyword scoring.
     */
    private Optional<String> extractRecipeSections(String html) {
        try {
            Document doc = Jsoup.parse(html);

            // Find all potential container elements
            Elements candidates = doc.select("article, section, div[class*=recipe], div[id*=recipe], main");

            Element bestSection = null;
            int bestScore = 0;

            for (Element candidate : candidates) {
                int score = scoreSection(candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestSection = candidate;
                }
            }

            if (bestSection != null && bestScore >= config.getSectionBased().getMinConfidence()) {
                // Clean the section but keep structure
                cleanElement(bestSection);
                String result = bestSection.html();

                if (result.length() >= config.getContentFilter().getMinOutputSize()) {
                    log.debug("Section-based extraction, score: {}, size: {} chars", bestScore, result.length());
                    return Optional.of(result);
                }
            }
        } catch (Exception e) {
            log.debug("Error during section-based extraction", e);
        }

        return Optional.empty();
    }

    /**
     * Score a section for recipe relevance (0-100).
     */
    private int scoreSection(Element element) {
        String text = element.text().toLowerCase();
        int score = 0;

        // Keyword presence (10 points each)
        for (String keyword : config.getSectionBased().getKeywords()) {
            if (text.contains(keyword)) {
                score += 10;
            }
        }

        // Structural patterns (bonus points)
        if (element.select("ul, ol").size() >= 2) score += 20; // Lists for ingredients/steps
        if (element.select("h2, h3").size() >= 2) score += 10; // Section headers
        if (text.length() > 1000) score += 10; // Substantial content

        return Math.min(100, score);
    }

    /**
     * Clean an element by removing unwanted child elements and attributes.
     */
    private void cleanElement(Element element) {
        // Remove unwanted elements
        element.select("script, style, noscript").remove();
        element.select("nav, header, footer").remove();
        element.select("iframe, embed, object").remove();
        element.select("[class*=ad], [id*=ad]").remove();
        element.select("[class*=social], [class*=share]").remove();
        element.select("[class*=comment], [id*=comment]").remove();
        element.select("[style*=display:none], [style*=visibility:hidden]").remove();
        // Clean attributes to reduce size
        cleanupAttributes(element);
    }

    /**
     * Check if section meets minimum confidence threshold.
     */
    private boolean meetsConfidence(String sectionHtml) {
        // Already checked in extractRecipeSections via bestScore comparison
        return true;
    }

    /**
     * Filter unwanted content from HTML.
     */
    private String filterUnwantedContent(String html) {
        try {
            Document doc = Jsoup.parse(html);

            // Remove non-content elements
            doc.select("script, style, noscript").remove();
            doc.select("nav, header, footer").remove();
            doc.select("iframe, embed, object").remove();

            // Remove ads and social media (by common class/id patterns)
            doc.select("[class*=ad], [id*=ad]").remove();
            doc.select("[class*=social], [class*=share]").remove();
            doc.select("[class*=comment], [id*=comment]").remove();
            doc.select("[class*=sidebar], [id*=sidebar]").remove();

            // Remove hidden elements
            doc.select("[style*=display:none], [style*=visibility:hidden]").remove();

            // Clean attributes to reduce size
            cleanupAttributes(doc);

            String cleaned = doc.body().html();
            log.debug("Content filtering applied, size: {} chars", cleaned.length());
            return cleaned;
        } catch (Exception e) {
            log.error("Error during content filtering", e);
            return html;
        }
    }

    /**
     * Build a preprocessing result with metrics.
     */
    private Results buildResult(String cleanedHtml, int originalSize, Strategy strategy) {
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
    private void emitMetrics(Strategy strategy, int originalSize, int cleanedSize, double reductionRatio) {
        // Counter: Which strategies are being used
        meterRegistry.counter("html.preprocessing.strategy",
                "type", strategy.name()).increment();

        // Gauge: Current reduction ratio (0.0-1.0)
        meterRegistry.gauge("html.preprocessing.reduction_ratio", reductionRatio);

        // Distribution summaries: HTML sizes
        meterRegistry.summary("html.preprocessing.original_size").record(originalSize);
        meterRegistry.summary("html.preprocessing.cleaned_size").record(cleanedSize);
    }

    /**
     * Strategy used for HTML preprocessing.
     */
    public enum Strategy {
        STRUCTURED_DATA,  // Found and used JSON-LD schema.org/Recipe
        SECTION_BASED,    // Extracted high-scoring recipe sections
        CONTENT_FILTER,   // Removed unwanted elements only
        FALLBACK,         // Used raw HTML (preprocessing failed)
        DISABLED          // Preprocessing disabled in config
    }

    /**
     * Preprocessing result containing cleaned HTML and metrics.
     */
    public record Results(
            String cleanedHtml,
            int originalSize,
            int cleanedSize,
            double reductionRatio,
            Strategy strategyUsed,
            String metricsMessage
    ) {
    }
}
