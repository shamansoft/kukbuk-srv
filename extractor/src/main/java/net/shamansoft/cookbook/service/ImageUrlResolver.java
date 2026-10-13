package net.shamansoft.cookbook.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Resolves recipe image paths to absolute http(s) URLs.
 * The LLM sometimes returns image paths exactly as they appear in the page HTML
 * (e.g. "/assets/a.png"), which a client cannot load without the page URL.
 */
public final class ImageUrlResolver {

    // Characters java.net.URI rejects but browsers accept and percent-encode.
    private static final String ENCODED_CHARS = " \"<>\\^`{|}";

    private ImageUrlResolver() {
    }

    /**
     * Resolve an image path against the URL of the page it came from.
     *
     * @param path      image path as stored in the recipe: absolute URL, protocol-relative,
     *                  root-relative or relative to the page
     * @param sourceUrl URL of the recipe page; only needed when the path is not already absolute
     * @return absolute http(s) URL, or empty if the path cannot be turned into one
     * (blank, non-http scheme such as data:, malformed, or relative with no usable source URL)
     */
    public static Optional<String> resolve(String path, String sourceUrl) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String trimmed = path.trim();
        if (isHttpUrl(trimmed)) {
            return Optional.of(trimmed);
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI relative = new URI(encodeIllegalChars(trimmed));
            if (relative.isAbsolute()) {
                return Optional.empty(); // data:, blob:, file:, ...
            }

            URI base = new URI(encodeIllegalChars(stripQueryAndFragment(sourceUrl.trim())));
            if (!isHttpUrl(base.toString()) || base.getHost() == null) {
                return Optional.empty();
            }
            if (base.getRawPath().isEmpty()) {
                // URI.resolve drops the separator when the base has no path at all
                base = base.resolve("/");
            }

            URI resolved = base.resolve(relative).normalize();
            if (resolved.getHost() == null) {
                return Optional.empty();
            }
            return Optional.of(toUrl(resolved));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static String stripQueryAndFragment(String url) {
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') {
                return url.substring(0, i);
            }
        }
        return url;
    }

    private static String encodeIllegalChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || ENCODED_CHARS.indexOf(c) >= 0) {
                sb.append('%').append(String.format("%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toUrl(URI uri) {
        String path = uri.getRawPath();
        // normalize() keeps ".." segments that climb above the root; browsers discard them
        while (path.startsWith("/../")) {
            path = path.substring(3);
        }
        StringBuilder sb = new StringBuilder()
                .append(uri.getScheme()).append("://").append(uri.getRawAuthority()).append(path);
        if (uri.getRawQuery() != null) {
            sb.append('?').append(uri.getRawQuery());
        }
        return sb.toString();
    }
}
