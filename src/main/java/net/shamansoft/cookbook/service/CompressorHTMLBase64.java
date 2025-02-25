package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

//@Service
public class CompressorHTMLBase64 implements Compressor {

    @Override
    public String decompress(String content) throws IOException {
        byte[] compressedBytes = Base64.getDecoder().decode(content);

        // Decompress with GZIP
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedBytes);
             GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            // Convert to string
            return outputStream.toString("UTF-8");
        }
    }
}
