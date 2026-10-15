package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.UrlFetchException;
import net.shamansoft.cookbook.html.UrlContentFetcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Map;

/**
 * Fetches a page as Markdown via markdown.new, which renders the page in a real browser
 * (Cloudflare Browser Rendering) and so gets past most bot protection.
 *
 * <p>Response body: {@code {"success": true, "url": ..., "title": ..., "content": "<markdown>", ...}}.
 * Parsed as a {@code Map} so no reflection hints are needed for the native image.
 */
@Slf4j
@Service
public class MarkdownNewClientImpl implements UrlContentFetcher {

    private final RestClient restClient;

    public MarkdownNewClientImpl(@Qualifier("markdownNewRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String fetch(String url) throws IOException {
        log.debug("Fetching markdown from URL via markdown.new: {}", url);
        try {
            Map<String, Object> result = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (result == null
                    || !Boolean.TRUE.equals(result.get("success"))
                    || !(result.get("content") instanceof String content)
                    || content.isBlank()) {
                throw new UrlFetchException(502, "markdown.new returned no content for URL: " + url);
            }
            log.debug("Successfully fetched markdown for URL: {}, length: {} chars", url, content.length());
            return content;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new UrlFetchException(429,
                        "Rate limit exceeded for markdown.new (500 req/day per IP). Please try again later.");
            }
            throw new UrlFetchException(url, e.getStatusCode().value());
        } catch (RestClientResponseException e) {
            throw new UrlFetchException(url, e.getStatusCode().value());
        }
    }
}
