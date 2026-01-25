package net.shamansoft.cookbook.html.strategy;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@Order(2)
public class SectionBasedStrategy implements CleanupStrategy {

    private final HtmlCleanupConfig config;

    public SectionBasedStrategy(HtmlCleanupConfig config) {
        this.config = config;
    }

    @Override
    public Optional<String> clean(String html) {
        if (!config.getSectionBased().isEnabled()) return Optional.empty();
        try {
            Document doc = Jsoup.parse(html);
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
                // use the local helper to clean element
                HtmlCleanupUtils.cleanElement(bestSection);
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

    @Override
    public Strategy getStrategy() {
        return Strategy.SECTION_BASED;
    }

    private int scoreSection(Element element) {
        String text = element.text().toLowerCase();
        int score = 0;
        for (String keyword : config.getSectionBased().getKeywords()) {
            if (text.contains(keyword)) score += 10;
        }
        if (element.select("ul, ol").size() >= 2) score += 20;
        if (element.select("h2, h3").size() >= 2) score += 10;
        if (text.length() > 1000) score += 10;
        return Math.min(100, score);
    }
}
