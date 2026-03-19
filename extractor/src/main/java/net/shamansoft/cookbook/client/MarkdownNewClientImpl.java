package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.UrlFetchException;
import net.shamansoft.cookbook.html.UrlContentFetcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "cookbook.fetcher.type", havingValue = "markdown-new")
public class MarkdownNewClientImpl implements UrlContentFetcher {

    private final RestClient restClient;

    public MarkdownNewClientImpl(@Qualifier("markdownNewRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String fetch(String url) throws IOException {
        log.debug("Fetching markdown from URL via markdown.new: {}", url);
        try {
            String result = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url))
                    .retrieve()
                    .body(String.class);
            if (result == null) {
                throw new UrlFetchException(204, "markdown.new returned empty response for URL: " + url);
            }
            log.debug("Successfully fetched markdown for URL: {}", url);
            return result;
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
