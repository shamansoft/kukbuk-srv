package net.shamansoft.cookbook.config;

import net.shamansoft.cookbook.dto.Compression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringToCompressionConverterTest {

    private final StringToCompressionConverter converter = new StringToCompressionConverter();

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "GZIP", "Gzip", "GZip"})
    void shouldMapGzipVariantsToBase64Gzip(String input) {
        assertThat(converter.convert(input)).isEqualTo(Compression.BASE64_GZIP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "NONE", "base64", "BASE64", "abc", "  ", ""})
    void shouldMapAnythingElseToNone(String input) {
        assertThat(converter.convert(input)).isEqualTo(Compression.NONE);
    }

    @Test
    void shouldMapNullToNone() {
        assertThat(converter.convert(null)).isEqualTo(Compression.NONE);
    }
}
