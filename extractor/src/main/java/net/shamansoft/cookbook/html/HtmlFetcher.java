package net.shamansoft.cookbook.html;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.BlockedPageException;
import net.shamansoft.cookbook.exception.UrlFetchException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service
public class HtmlFetcher implements UrlContentFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;

    public String fetch(String url) throws IOException {
        return bodyHtml(url, load(url));
    }

    /**
     * Like {@link #fetch(String)}, but rejects an HTTP 200 page that carries no usable content:
     * a soft-404 page ({@link UrlFetchException} with status 404) or a bot-protection challenge /
     * JavaScript-rendered shell ({@link BlockedPageException}).
     */
    public String fetchUsable(String url) throws IOException {
        Document doc = load(url);
        Optional<BlockedPageDetector.Finding> finding = BlockedPageDetector.detect(doc);
        if (finding.isPresent()) {
            log.warn("Fetched page is unusable ({}): {}", finding.get().reason(), url);
            throw finding.get().kind() == BlockedPageDetector.Kind.NOT_FOUND
                    ? new UrlFetchException(url, 404)
                    : new BlockedPageException(finding.get().reason());
        }
        return bodyHtml(url, doc);
    }

    private Document load(String url) throws IOException {
        log.debug("Fetching HTML from URL: {}", url);
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (HttpStatusException e) {
            log.warn("Failed to fetch URL (HTTP {}): {}", e.getStatusCode(), url);
            throw new UrlFetchException(url, e.getStatusCode());
        }
    }

    private String bodyHtml(String url, Document doc) {
        Elements scripts = doc.getElementsByTag("script");
        for (Element script : scripts) {
            script.remove();
        }
        String html = doc.body().html();
        log.debug("Successfully fetched HTML from URL: {}, length: {} chars", url, html.length());
        return html;
    }
}
