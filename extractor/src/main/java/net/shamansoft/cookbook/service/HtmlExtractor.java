package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.Request;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class HtmlExtractor {

    private final Compressor compressor;
    public static final String NONE = "none";
    private final RawContentService rawContentService;

    public HtmlExtractor(Compressor compressor, RawContentService rawContentService) {
        this.compressor = compressor;
        this.rawContentService = rawContentService;
    }

    public String extractHtml(Request request, String compression) throws IOException {
        String html = "";
        if (request.html() != null && !request.html().isEmpty()) {
            try {
                if (NONE.equals(compression)) {
                    html = request.html();
                    log.debug("Skipping decompression, using HTML from request");
                } else {
                    html = compressor.decompress(request.html());
                    log.debug("Successfully decompressed HTML from request");
                }
            } catch (IOException e) {
                log.warn("Failed to decompress HTML from request: {}", e.getMessage(), e);
                if (request.url() == null || request.url().isEmpty()) {
                    log.error("Cannot fall back to URL as it's not provided or empty");
                    throw new IOException("Failed to decompress HTML and no valid URL provided as fallback", e);
                }
                throw e;
            }
        } else {
            log.debug("No HTML in request, fetching from URL: {}", request.url());
            html = rawContentService.fetch(request.url());
        }
        return html;
    }
}
