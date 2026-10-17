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
    private static final String JSON_LD = "application/ld+json";

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
        String html = toBodyHtml(doc);
        log.debug("Successfully fetched HTML from URL: {}, length: {} chars", url, html.length());
        return html;
    }

    /**
     * Serialises the page body without executable scripts. schema.org JSON-LD blocks are kept, and
     * those in {@code <head>} are moved to the end of the body, so {@code StructuredDataStrategy}
     * can still find the page's {@code Recipe} data.
     */
    static String toBodyHtml(Document doc) {
        doc.select("script:not([type=" + JSON_LD + "])").remove();
        Elements headJsonLd = doc.head().select("script[type=" + JSON_LD + "]");
        for (Element script : headJsonLd) {
            doc.body().appendChild(script);
        }
        return doc.body().html();
    }
}
