package net.shamansoft.cookbook.html;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class HtmlExtractor {

    private final UrlContentFetcher htmlFetcher;

    public HtmlExtractor(UrlContentFetcher htmlFetcher) {
        this.htmlFetcher = htmlFetcher;
    }

    /**
     * Returns the provided HTML if non-blank, otherwise fetches from the URL.
     * Decompression is the caller's responsibility and must happen before this call.
     */
    public String extractHtml(String url, String html) throws IOException {
        if (html != null && !html.isBlank()) {
            log.debug("Using provided HTML - length: {} chars", html.length());
            return html;
        }
        log.debug("No HTML provided, fetching from URL: {}", url);
        return htmlFetcher.fetch(url);
    }
}
