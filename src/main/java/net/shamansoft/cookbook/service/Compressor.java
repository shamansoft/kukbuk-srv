package net.shamansoft.cookbook.service;

import java.io.IOException;

public interface Compressor {

    String decompress(String content) throws IOException;
}
