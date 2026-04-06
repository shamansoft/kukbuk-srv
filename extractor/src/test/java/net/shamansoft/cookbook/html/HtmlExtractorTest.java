package net.shamansoft.cookbook.html;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HtmlExtractorTest {

    @Mock
    private UrlContentFetcher htmlFetcher;

    private HtmlExtractor htmlExtractor;

    @BeforeEach
    void setUp() {
        htmlExtractor = new HtmlExtractor(htmlFetcher);
    }

    @Test
    void returnsProvidedHtmlWhenPresent() throws IOException {
        String result = htmlExtractor.extractHtml("https://example.com", "<html>content</html>");

        assertThat(result).isEqualTo("<html>content</html>");
        verifyNoInteractions(htmlFetcher);
    }

    @Test
    void fetchesFromUrlWhenHtmlIsNull() throws IOException {
        when(htmlFetcher.fetch("https://example.com/page")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml("https://example.com/page", null);

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch("https://example.com/page");
    }

    @Test
    void fetchesFromUrlWhenHtmlIsBlank() throws IOException {
        when(htmlFetcher.fetch("https://example.com/page")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml("https://example.com/page", "   ");

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch("https://example.com/page");
    }

    @Test
    void fetchesFromUrlWhenHtmlIsEmptyString() throws IOException {
        when(htmlFetcher.fetch("https://example.com/page")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml("https://example.com/page", "");

        assertThat(result).isEqualTo("fetched-html");
        verify(htmlFetcher).fetch("https://example.com/page");
    }

    @Test
    void propagatesExceptionFromFetcher() throws IOException {
        when(htmlFetcher.fetch("https://example.com")).thenThrow(new IOException("fetch failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> htmlExtractor.extractHtml("https://example.com", null))
                .isInstanceOf(IOException.class)
                .hasMessage("fetch failed");
    }
}
