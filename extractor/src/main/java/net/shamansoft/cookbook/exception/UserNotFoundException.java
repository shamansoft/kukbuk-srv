package net.shamansoft.cookbook.exception;

/**
 * Thrown when a user profile is not found in the database
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

