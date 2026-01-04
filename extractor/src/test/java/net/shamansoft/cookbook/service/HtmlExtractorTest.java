package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.shamansoft.cookbook.service.HtmlExtractor.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HtmlExtractorTest {

    @Mock
    private Compressor compressor;

    @Mock
    private RawContentService rawContentService;

    private HtmlExtractor htmlExtractor;

    @BeforeEach
    void setUp() {
        htmlExtractor = new HtmlExtractor(compressor, rawContentService);
    }

    @Test
    void decompressesHtmlFromRequestWhenCompressionRequested() throws IOException {
        Request request = new Request("compressed-html", "Title", "https://example.com");
        when(compressor.decompress("compressed-html")).thenReturn("decompressed-html");

        String result = htmlExtractor.extractHtml(request, "gzip");

        assertThat(result).isEqualTo("decompressed-html");
        verify(compressor).decompress("compressed-html");
        verifyNoInteractions(rawContentService);
    }

    @Test
    void returnsRawHtmlWhenCompressionExplicitlyDisabled() throws IOException {
        Request request = new Request("<html></html>", "Title", "https://example.com");

        String result = htmlExtractor.extractHtml(request, NONE);

        assertThat(result).isEqualTo("<html></html>");
        verifyNoInteractions(compressor, rawContentService);
    }

    @Test
    void fetchesHtmlFromUrlWhenRequestHtmlMissing() throws IOException {
        Request request = new Request(null, "Title", "https://example.com/page");
        when(rawContentService.fetch("https://example.com/page")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(request, "gzip");

        assertThat(result).isEqualTo("fetched-html");
        verify(rawContentService).fetch("https://example.com/page");
        verifyNoInteractions(compressor);
    }

    @Test
    void fetchesHtmlFromUrlWhenRequestHtmlEmptyString() throws IOException {
        Request request = new Request("", "Title", "https://example.com/page");
        when(rawContentService.fetch("https://example.com/page")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(request, "gzip");

        assertThat(result).isEqualTo("fetched-html");
        verify(rawContentService).fetch("https://example.com/page");
        verifyNoInteractions(compressor);
    }

    @Test
    void fallsBackToUrlFetchWhenDecompressionFailsButUrlProvided() throws IOException {
        Request request = new Request("broken", "Title", "https://example.com");
        IOException original = new IOException("boom");
        when(compressor.decompress("broken")).thenThrow(original);
        when(rawContentService.fetch("https://example.com")).thenReturn("fetched-html");

        String result = htmlExtractor.extractHtml(request, "gzip");

        assertThat(result).isEqualTo("fetched-html");
        verify(compressor).decompress("broken");
        verify(rawContentService).fetch("https://example.com");
    }

    @Test
    void fallsBackToUrlFetchWhenDecompressionFailsEvenIfUrlEmpty() throws IOException {
        Request request = new Request("broken", "Title", "");
        IOException decompressionError = new IOException("boom");
        IOException fetchError = new IOException("URL fetch failed");
        when(compressor.decompress("broken")).thenThrow(decompressionError);
        when(rawContentService.fetch("")).thenThrow(fetchError);

        assertThatThrownBy(() -> htmlExtractor.extractHtml(request, "gzip"))
                .isSameAs(fetchError);

        verify(compressor).decompress("broken");
        verify(rawContentService).fetch("");
    }
}