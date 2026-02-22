package net.shamansoft.cookbook.exception;

/**
 * Thrown when the server cannot fetch HTML from a URL (e.g. 403 bot protection).
 * Clients should retry the request with HTML content provided directly.
 */
public class UrlFetchException extends RuntimeException {

    private final int httpStatus;

    public UrlFetchException(String url, int httpStatus) {
        super(String.format(
                "Cannot fetch URL (HTTP %d). The page may be protected against automated access. " +
                "Please submit the recipe with HTML content provided directly.", httpStatus));
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
