package net.shamansoft.recipe.parser;

/**
 * Exception thrown when recipe serialization fails.
 */
public class RecipeSerializeException extends Exception {

    public RecipeSerializeException(String message) {
        super(message);
    }

    public RecipeSerializeException(String message, Throwable cause) {
        super(message, cause);
    }
}
