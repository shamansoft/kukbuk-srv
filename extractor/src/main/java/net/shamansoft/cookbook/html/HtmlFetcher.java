package net.shamansoft.cookbook.html;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.UrlFetchException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class HtmlFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;

    public String fetch(String url) throws IOException {
        log.debug("Fetching HTML from URL: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            Elements scripts = doc.getElementsByTag("script");
            for (Element script : scripts) {
                script.remove();
            }
            String html = doc.body().html();
            log.debug("Successfully fetched HTML from URL: {}, length: {} chars", url, html.length());
            return html;
        } catch (HttpStatusException e) {
            log.warn("Failed to fetch URL (HTTP {}): {}", e.getStatusCode(), url);
            throw new UrlFetchException(url, e.getStatusCode());
        }
    }
}
