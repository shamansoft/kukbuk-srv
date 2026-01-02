package net.shamansoft.cookbook.exception;

/**
 * Exception thrown when a recipe YAML file cannot be parsed.
 * This typically indicates malformed YAML or missing required fields.
 */
public class InvalidRecipeFormatException extends RuntimeException {
    public InvalidRecipeFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidRecipeFormatException(String message) {
        super(message);
    }
}
