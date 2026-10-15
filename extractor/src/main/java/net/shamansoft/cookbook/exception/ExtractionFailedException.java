package net.shamansoft.cookbook.exception;

import net.shamansoft.cookbook.client.ClientException;

/**
 * Thrown when the model gave no usable answer for a page even after a retry: it returned no content
 * (finishReason RECITATION, SAFETY, ...) or its output was cut off at the token limit.
 * The page itself is the problem, so it is reported as 422, not as a server error.
 */
public class ExtractionFailedException extends ClientException {
    public ExtractionFailedException(String message) {
        super(message);
    }
}
