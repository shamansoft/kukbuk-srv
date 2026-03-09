package net.shamansoft.cookbook.config;

import net.shamansoft.cookbook.dto.Compression;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Allows case-insensitive binding of the compression query parameter.
 * e.g. ?compression=none, ?compression=NONE, ?compression=gzip all work.
 */
@Component
public class StringToCompressionConverter implements Converter<String, Compression> {

    @Override
    public Compression convert(String source) {
        if ("gzip".equalsIgnoreCase(source)) {
            return Compression.BASE64_GZIP;
        }
        return Compression.NONE;
    }
}
