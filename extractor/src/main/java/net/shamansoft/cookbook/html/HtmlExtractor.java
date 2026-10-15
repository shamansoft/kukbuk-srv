package net.shamansoft.cookbook.html;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.BlockedPageException;
import net.shamansoft.cookbook.exception.UrlFetchException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class HtmlExtractor {

    /** Origin statuses that bot protection typically answers with; other errors are not retried. */
    private static final Set<Integer> BLOCKED_STATUSES = Set.of(401, 403, 429, 503);

    private final HtmlFetcher htmlFetcher;
    private final UrlContentFetcher markdownFetcher;
    private final boolean markdownFallbackEnabled;

    public HtmlExtractor(HtmlFetcher htmlFetcher,
                         @Qualifier("markdownNewClientImpl") UrlContentFetcher markdownFetcher,
                         @Value("${cookbook.fetcher.markdown-new.enabled:true}") boolean markdownFallbackEnabled) {
        this.htmlFetcher = htmlFetcher;
        this.markdownFetcher = markdownFetcher;
        this.markdownFallbackEnabled = markdownFallbackEnabled;
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

    /**
     * Fetches the page directly first. If the site blocks the fetch (bot-protection status,
     * challenge page, timeout), fetches it as Markdown via markdown.new instead.
     *
     * @throws UrlFetchException if the page cannot be fetched either way
     */
    public PageContent fetchWithFallback(String url) {
        Exception directError;
        try {
            return PageContent.html(htmlFetcher.fetchUsable(url));
        } catch (IOException | UrlFetchException e) {
            directError = e;
        }
        if (!markdownFallbackEnabled || !looksBlocked(directError)) {
            throw unreachable(directError, null);
        }

        log.info("Direct fetch blocked for URL: {} ({}), falling back to markdown.new", url, directError.getMessage());
        try {
            String markdown = markdownFetcher.fetch(url);
            Optional<BlockedPageDetector.Finding> finding = BlockedPageDetector.detectMarkdown(markdown);
            if (finding.isPresent()) {
                // Dropped here so a not-found or challenge page never reaches the LLM
                throw finding.get().kind() == BlockedPageDetector.Kind.NOT_FOUND
                        ? new UrlFetchException(url, 404)
                        : new BlockedPageException(finding.get().reason());
            }
            log.info("Fetched markdown via markdown.new - URL: {}, length: {} chars", url, markdown.length());
            return PageContent.markdown(markdown);
        } catch (IOException | RuntimeException fallbackError) {
            log.warn("markdown.new fallback failed for URL: {}: {}", url, fallbackError.getMessage());
            throw unreachable(directError, fallbackError);
        }
    }

    private static boolean looksBlocked(Exception e) {
        return e instanceof BlockedPageException
                || e instanceof SocketTimeoutException
                || e instanceof UrlFetchException ufe && BLOCKED_STATUSES.contains(ufe.getHttpStatus());
    }

    /**
     * Builds the error for POST /v1/recipes/url. The client only has the URL, so the message
     * says what went wrong instead of asking for HTML.
     */
    private static UrlFetchException unreachable(Exception directError, Exception fallbackError) {
        int status = directError instanceof UrlFetchException ufe ? ufe.getHttpStatus() : 0;
        int fallbackStatus = fallbackError instanceof UrlFetchException fe ? fe.getHttpStatus() : 0;
        String message;
        if (fallbackStatus == 429) {
            status = 429;
            message = "Cannot fetch URL: the site blocks automated access and the backup fetcher " +
                    "is over its daily limit. Please try again later.";
        } else if (fallbackStatus == 404) {
            // The site hid its 404 behind bot protection; markdown.new rendered the not-found page
            status = 404;
            fallbackError = null;
            message = "Cannot fetch URL (HTTP 404): the page was not found. Please check the link.";
        } else if (status == 404 || status == 410) {
            message = "Cannot fetch URL (HTTP " + status + "): the page was not found. Please check the link.";
        } else if (directError instanceof BlockedPageException) {
            message = "Cannot fetch URL: the site returned a bot-protection page instead of the recipe.";
        } else if (status > 0) {
            message = "Cannot fetch URL (HTTP " + status + ").";
        } else {
            message = "Cannot fetch URL: the site could not be reached (" + directError.getClass().getSimpleName() + ").";
        }
        if (fallbackError != null && status != 429) {
            message += " Fetching it through markdown.new also failed.";
        }
        UrlFetchException error = new UrlFetchException(status, message, directError);
        if (fallbackError != null) {
            error.addSuppressed(fallbackError);
        }
        return error;
    }
}
