package net.shamansoft.cookbook.html;

import net.shamansoft.cookbook.exception.BlockedPageException;
import net.shamansoft.cookbook.exception.UrlFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HtmlExtractorTest {

    private static final String URL = "https://example.com/page";
    private static final String RECIPE_MARKDOWN = "---\ntitle: Cookies\n---\n\n# Cookies\n\n"
            + "* 1 cup flour\n".repeat(60);

    @Mock
    private HtmlFetcher htmlFetcher;

    @Mock
    private UrlContentFetcher markdownFetcher;

    private HtmlExtractor htmlExtractor;

    @BeforeEach
    void setUp() {
        htmlExtractor = new HtmlExtractor(htmlFetcher, markdownFetcher, true);
    }

    // ---- extractHtml (POST /v1/recipes) ------------------------------------

    @Test
    void returnsProvidedHtmlWhenPresent() throws IOException {
        String result = htmlExtractor.extractHtml("https://example.com", "<html>content</html>");

        assertThat(result).isEqualTo("<html>content</html>");
        verifyNoInteractions(htmlFetcher, markdownFetcher);
    }

    @Test
    void fetchesFromUrlWhenHtmlIsNull() throws IOException {
        when(htmlFetcher.fetch(URL)).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(URL, null);

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch(URL);
    }

    @Test
    void fetchesFromUrlWhenHtmlIsBlank() throws IOException {
        when(htmlFetcher.fetch(URL)).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(URL, "   ");

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch(URL);
    }

    @Test
    void fetchesFromUrlWhenHtmlIsEmptyString() throws IOException {
        when(htmlFetcher.fetch(URL)).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(URL, "");

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch(URL);
    }

    @Test
    void extractHtmlNeverFallsBackToMarkdown() throws IOException {
        when(htmlFetcher.fetch("https://example.com")).thenThrow(new IOException("fetch failed"));

        assertThatThrownBy(() -> htmlExtractor.extractHtml("https://example.com", null))
                .isInstanceOf(IOException.class)
                .hasMessage("fetch failed");
        verifyNoInteractions(markdownFetcher);
    }

    // ---- fetchWithFallback (POST /v1/recipes/url) --------------------------

    @Test
    void fetchWithFallbackReturnsHtmlWhenDirectFetchSucceeds() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenReturn("fetched-html");

        PageContent result = htmlExtractor.fetchWithFallback(URL);

        assertThat(result).isEqualTo(PageContent.html("fetched-html"));
        verifyNoInteractions(markdownFetcher);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 503})
    void fallsBackToMarkdownOnBotProtectionStatus(int status) throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new UrlFetchException(URL, status));
        when(markdownFetcher.fetch(URL)).thenReturn(RECIPE_MARKDOWN);

        PageContent result = htmlExtractor.fetchWithFallback(URL);

        assertThat(result).isEqualTo(PageContent.markdown(RECIPE_MARKDOWN));
        assertThat(result.isMarkdown()).isTrue();
    }

    @Test
    void fallsBackToMarkdownWhenPageIsAChallenge() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new BlockedPageException("bot-protection challenge page"));
        when(markdownFetcher.fetch(URL)).thenReturn(RECIPE_MARKDOWN);

        assertThat(htmlExtractor.fetchWithFallback(URL)).isEqualTo(PageContent.markdown(RECIPE_MARKDOWN));
    }

    @Test
    void fallsBackToMarkdownOnTimeout() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new SocketTimeoutException("Read timed out"));
        when(markdownFetcher.fetch(URL)).thenReturn(RECIPE_MARKDOWN);

        assertThat(htmlExtractor.fetchWithFallback(URL)).isEqualTo(PageContent.markdown(RECIPE_MARKDOWN));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 410, 500})
    void doesNotFallBackOnOtherStatuses(int status) throws IOException {
        UrlFetchException original = new UrlFetchException(URL, status);
        when(htmlFetcher.fetchUsable(URL)).thenThrow(original);

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .asInstanceOf(type(UrlFetchException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                    assertThat(e.getCause()).isSameAs(original);
                    assertThat(e.getMessage()).contains("HTTP " + status).doesNotContain("HTML content");
                });
        verifyNoInteractions(markdownFetcher);
    }

    @Test
    void notFoundMessageAsksToCheckTheLink() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new UrlFetchException(URL, 404));

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .hasMessageContaining("page was not found");
    }

    @Test
    void networkErrorBecomesUrlFetchExceptionWithoutFallback() throws IOException {
        UnknownHostException original = new UnknownHostException("exmaple.com");
        when(htmlFetcher.fetchUsable(URL)).thenThrow(original);

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .asInstanceOf(type(UrlFetchException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isZero();
                    assertThat(e.getCause()).isSameAs(original);
                    assertThat(e.getMessage()).contains("could not be reached");
                });
        verifyNoInteractions(markdownFetcher);
    }

    @Test
    void rejectsMarkdownThatIsItselfAChallengePage() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new UrlFetchException(URL, 403));
        when(markdownFetcher.fetch(URL)).thenReturn("---\ntitle: Just a moment...\n---\n\nChecking your browser.");

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .asInstanceOf(type(UrlFetchException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(403);
                    assertThat(e.getSuppressed()).singleElement().isInstanceOf(BlockedPageException.class);
                    assertThat(e.getMessage()).contains("markdown.new also failed");
                });
    }

    @Test
    void dropsMarkdownNotFoundPageAndReportsNotFound() throws IOException {
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new UrlFetchException(URL, 403));
        when(markdownFetcher.fetch(URL)).thenReturn(
                "---\ntitle: \"Page not found - Preppy Kitchen\"\n---\n\n" + "Sorry, there isn't a page here. ".repeat(40));

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .asInstanceOf(type(UrlFetchException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(404);
                    assertThat(e.getMessage()).contains("page was not found").doesNotContain("markdown.new");
                });
    }

    @Test
    void reportsRateLimitWhenFallbackIsThrottled() throws IOException {
        UrlFetchException fallbackError = new UrlFetchException(429, "rate limited");
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new BlockedPageException("challenge"));
        when(markdownFetcher.fetch(URL)).thenThrow(fallbackError);

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .asInstanceOf(type(UrlFetchException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(429);
                    assertThat(e.getMessage()).contains("try again later");
                    assertThat(e.getSuppressed()).containsExactly(fallbackError);
                });
    }

    @Test
    void doesNotFallBackWhenDisabled() throws IOException {
        htmlExtractor = new HtmlExtractor(htmlFetcher, markdownFetcher, false);
        when(htmlFetcher.fetchUsable(URL)).thenThrow(new UrlFetchException(URL, 403));

        assertThatThrownBy(() -> htmlExtractor.fetchWithFallback(URL))
                .isInstanceOf(UrlFetchException.class)
                .hasMessageContaining("HTTP 403");
        verifyNoInteractions(markdownFetcher);
    }
}
