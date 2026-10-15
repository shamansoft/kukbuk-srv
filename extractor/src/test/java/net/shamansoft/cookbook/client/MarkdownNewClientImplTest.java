package net.shamansoft.cookbook.client;

import net.shamansoft.cookbook.exception.UrlFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class MarkdownNewClientImplTest {

    @Mock
    private RestClient restClient;

    private MarkdownNewClientImpl client;

    @Mock
    private RestClient.RequestBodyUriSpec postSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        client = new MarkdownNewClientImpl(restClient);
        when(restClient.post()).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void fetch_success_returnsMarkdownString() throws IOException {
        when(responseSpec.body(String.class)).thenReturn("# Recipe Title\nSome markdown content");

        String result = client.fetch("https://example.com/recipe");

        assertThat(result).isEqualTo("# Recipe Title\nSome markdown content");
        verify(postSpec).contentType(MediaType.APPLICATION_JSON);
        verify(postSpec).body(Map.of("url", "https://example.com/recipe"));
    }

    @Test
    void fetch_http429_throwsUrlFetchExceptionWithRateLimitMessage() {
        when(responseSpec.body(String.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetch("https://example.com/recipe"))
                .isInstanceOf(UrlFetchException.class)
                .hasMessageContaining("Rate limit exceeded for markdown.new")
                .hasMessageContaining("500 req/day per IP");
    }

    @Test
    void fetch_http429_setsHttpStatus429() {
        when(responseSpec.body(String.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetch("https://example.com/recipe"))
                .isInstanceOf(UrlFetchException.class)
                .satisfies(ex -> assertThat(((UrlFetchException) ex).getHttpStatus()).isEqualTo(429));
    }

    @Test
    void fetch_other4xx_throwsUrlFetchExceptionWithStatusCode() {
        when(responseSpec.body(String.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetch("https://example.com/recipe"))
                .isInstanceOf(UrlFetchException.class)
                .satisfies(ex -> assertThat(((UrlFetchException) ex).getHttpStatus()).isEqualTo(403))
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void fetch_5xxError_throwsUrlFetchExceptionWithStatusCode() {
        when(responseSpec.body(String.class))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetch("https://example.com/recipe"))
                .isInstanceOf(UrlFetchException.class)
                .satisfies(ex -> assertThat(((UrlFetchException) ex).getHttpStatus()).isEqualTo(500))
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void fetch_nullResponse_throwsUrlFetchException() {
        when(responseSpec.body(String.class)).thenReturn(null);

        assertThatThrownBy(() -> client.fetch("https://example.com/recipe"))
                .isInstanceOf(UrlFetchException.class)
                .satisfies(ex -> assertThat(((UrlFetchException) ex).getHttpStatus()).isEqualTo(204));
    }
}
