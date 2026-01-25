package net.shamansoft.cookbook.html.strategy;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@Order(3)
public class ContentFilterStrategy implements CleanupStrategy {

    private final HtmlCleanupConfig config;

    public ContentFilterStrategy(HtmlCleanupConfig config) {
        this.config = config;
    }

    @Override
    public Optional<String> clean(String html) {
        try {
            Document doc = Jsoup.parse(html);
            doc.select("script, style, noscript").remove();
            doc.select("nav, header, footer").remove();
            doc.select("iframe, embed, object").remove();
            doc.select("[class*=ad], [id*=ad]").remove();
            doc.select("[class*=social], [class*=share]").remove();
            doc.select("[class*=comment], [id*=comment]").remove();
            doc.select("[class*=sidebar], [id*=sidebar]").remove();
            doc.select("[style*=display:none], [style*=visibility:hidden]").remove();
            // recursively clean elements and attributes from the body
            HtmlCleanupUtils.cleanElement(doc.body());
            String cleaned = doc.body().html();
            try {
                log.debug("Content filtering applied, size: {} chars", cleaned.length());
            } catch (Throwable ignored) {
            }
            if (cleaned.length() >= config.getContentFilter().getMinOutputSize()) {
                return Optional.of(cleaned);
            }
        } catch (Exception ignored) {
            try {
                log.debug("Error during content filtering", ignored);
            } catch (Throwable ignored2) {
            }
        }
        return Optional.empty();
    }

    @Override
    public Strategy getStrategy() {
        return Strategy.CONTENT_FILTER;
    }
}
