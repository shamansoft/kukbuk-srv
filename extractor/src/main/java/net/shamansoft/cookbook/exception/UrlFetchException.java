package net.shamansoft.cookbook.exception;

/**
 * Thrown when the server cannot fetch a page from a URL (e.g. 403 bot protection).
 * {@code httpStatus} is the origin's HTTP status, or 0 when no HTTP response was received.
 */
public class UrlFetchException extends RuntimeException {

    private final int httpStatus;
    /** Where the request ended up after redirects, when known; {@code null} otherwise. */
    private String finalUrl;

    public UrlFetchException(String url, int httpStatus) {
        super(String.format(
                "Cannot fetch URL (HTTP %d). The page may be protected against automated access. " +
                "Please submit the recipe with HTML content provided directly.", httpStatus));
        this.httpStatus = httpStatus;
    }

    public UrlFetchException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public UrlFetchException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    /** Records the URL the request ended up at after redirects (e.g. a t.co link's target). */
    public UrlFetchException withFinalUrl(String finalUrl) {
        this.finalUrl = finalUrl;
        return this;
    }
}
