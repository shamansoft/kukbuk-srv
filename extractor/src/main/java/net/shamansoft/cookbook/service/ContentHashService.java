package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class ContentHashService {

    private static final List<String> TRACKING_PARAMETERS = Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "dclid", "msclkid", "twclid", "ref", "source"
    );

    private final ConcurrentMap<String, String> hashCache = new ConcurrentHashMap<>();

    @NonNull
    public String generateContentHash(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        String cachedHash = hashCache.get(url);
        if (cachedHash != null) {
            log.debug("Cache hit for URL: {}", url);
            return cachedHash;
        }

        long startTime = System.currentTimeMillis();
        
        try {
            String normalizedUrl = normalizeUrl(url);
            String hash = generateSha256Hash(normalizedUrl);
            
            hashCache.put(url, hash);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("Generated hash for URL in {}ms: {}", processingTime, url);
            
            return hash;
        } catch (Exception e) {
            log.error("Failed to generate hash for URL: {}", url, e);
            throw new RuntimeException("Failed to generate content hash", e);
        }
    }

    String normalizeUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        
        StringBuilder normalizedQuery = new StringBuilder();
        if (uri.getQuery() != null) {
            String[] queryParams = uri.getQuery().split("&");
            for (String param : queryParams) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length > 0) {
                    String key = keyValue[0].toLowerCase();
                    if (!TRACKING_PARAMETERS.contains(key)) {
                        if (normalizedQuery.length() > 0) {
                            normalizedQuery.append("&");
                        }
                        normalizedQuery.append(param);
                    }
                }
            }
        }
        
        String queryString = normalizedQuery.length() > 0 ? normalizedQuery.toString() : null;
        
        URI normalizedUri = new URI(
                uri.getScheme() != null ? uri.getScheme().toLowerCase() : null,
                uri.getAuthority() != null ? uri.getAuthority().toLowerCase() : null,
                uri.getPath(),
                queryString,
                null // Remove fragment
        );
        
        return normalizedUri.toString();
    }

    private String generateSha256Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    public void clearCache() {
        hashCache.clear();
        log.debug("Hash cache cleared");
    }

    public int getCacheSize() {
        return hashCache.size();
    }
}