package net.shamansoft.cookbook.exception;

/**
 * The page was fetched (HTTP 200) but carries no usable content: a bot-protection
 * challenge page or a JavaScript-rendered shell.
 */
public class BlockedPageException extends UrlFetchException {

    public BlockedPageException(String reason) {
        super(200, "Fetched page is unusable: " + reason);
    }
}
