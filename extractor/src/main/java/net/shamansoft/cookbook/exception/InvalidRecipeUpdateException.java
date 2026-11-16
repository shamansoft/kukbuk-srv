package net.shamansoft.cookbook.exception;

/**
 * Thrown when a {@code PATCH /v1/recipes/{id}} body is empty or breaks a limit. Mapped to 400.
 */
public class InvalidRecipeUpdateException extends RuntimeException {
    public InvalidRecipeUpdateException(String message) {
        super(message);
    }
}
