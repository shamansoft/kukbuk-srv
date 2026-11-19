package net.shamansoft.cookbook.exception;

/** A recipe write cannot safely proceed and may be retried by the client. */
public class RecipeWriteUnavailableException extends RuntimeException {

    public RecipeWriteUnavailableException(String message) {
        super(message);
    }

    public RecipeWriteUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
