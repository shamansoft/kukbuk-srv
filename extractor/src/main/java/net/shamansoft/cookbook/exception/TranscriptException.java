package net.shamansoft.cookbook.exception;

public class TranscriptException extends RuntimeException {

    public TranscriptException(String message) {
        super(message);
    }

    public TranscriptException(String message, Throwable cause) {
        super(message, cause);
    }
}
