package net.shamansoft.cookbook.dto;

public enum Compression {
    BASE64_GZIP,  // Base64+GZIP compressed content (default when compression param is absent)
    NONE;    // Raw content, no decompression needed
}
