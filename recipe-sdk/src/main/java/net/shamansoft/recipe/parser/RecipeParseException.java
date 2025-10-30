package net.shamansoft.recipe.parser;

/**
 * Exception thrown when recipe parsing fails.
 */
public class RecipeParseException extends Exception {

    public RecipeParseException(String message) {
        super(message);
    }

    public RecipeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
