package net.shamansoft.cookbook.html;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CompressorHTMLBase64Test {

    private CompressorHTMLBase64 compressor;

    @BeforeEach
    void setUp() {
        compressor = new CompressorHTMLBase64();
    }

    @Test
    void decompressEmptyContent() throws IOException {
        String compressed = "H4sIAAAAAAAA/wEAAP//AAAAAAAAAAA="; // Empty string gzipped and base64 encoded
        String decompressed = compressor.decompress(compressed);
        assertThat(decompressed).isEmpty();
    }

    @Test
    void decompressLongHtmlContent() throws IOException {
        String html = "<html><body><h1>Test</h1><p>Long content</p></body></html>";
        String compressed = Base64.getEncoder().encodeToString(gzip(html));
        String decompressed = compressor.decompress(compressed);
        assertThat(decompressed).isEqualTo(html);
    }

    @Test
    void invalidBase64ThrowsIOException() {
        String invalidBase64 = "not-base64-content";
        assertThatThrownBy(() -> compressor.decompress(invalidBase64))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Content isn't Base64 encoded");
    }

    @Test
    void invalidGzipContentThrowsIOException() {
        String invalidGzip = Base64.getEncoder().encodeToString("not-gzipped".getBytes());
        assertThatThrownBy(() -> compressor.decompress(invalidGzip))
            .isInstanceOf(IOException.class);
    }

    private byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }
}