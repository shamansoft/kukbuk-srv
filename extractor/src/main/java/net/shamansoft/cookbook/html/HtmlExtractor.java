package net.shamansoft.cookbook.html;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class HtmlExtractor {

    private final Compressor compressor;
    public static final String NONE = "none";
    private final HtmlFetcher htmlFetcher;

    public HtmlExtractor(Compressor compressor, HtmlFetcher htmlFetcher) {
        this.compressor = compressor;
        this.htmlFetcher = htmlFetcher;
    }

    public String extractHtml(Request request, String compression) throws IOException {
        return extractHtml(request.url(), request.html(), compression);
    }

    public String extractHtml(String url, String source, String compression) throws IOException {
        if (source != null && !source.isBlank()) {
            try {
                if (NONE.equals(compression)) {
                    log.debug("Skipping decompression, using HTML from request");
                    return source;
                } else {
                    log.debug("Successfully decompressed HTML from request");
                    return compressor.decompress(source);
                }
            } catch (IOException e) {
                log.warn("Failed to decompress HTML from request: {}, try to fetch from source url", e.getMessage(), e);
            }
        }
        log.debug("No HTML in request, fetching from URL: {}", url);
        return htmlFetcher.fetch(url);
    }
}
