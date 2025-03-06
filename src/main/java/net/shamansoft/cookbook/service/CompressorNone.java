package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
public class CompressorNone implements Compressor {

    @Override
    public String decompress(String content) throws IOException {
        log.debug("decompress: {}", content);
        throw new IOException("Not implemented");
    }
}
