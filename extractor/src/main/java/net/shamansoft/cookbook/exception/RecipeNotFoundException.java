package net.shamansoft.cookbook.exception;

/**
 * Exception thrown when a requested recipe cannot be found in Google Drive.
 * This may indicate the file was deleted or the user lacks permission.
 */
public class RecipeNotFoundException extends RuntimeException {
    public RecipeNotFoundException(String message) {
        super(message);
    }

    public RecipeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
