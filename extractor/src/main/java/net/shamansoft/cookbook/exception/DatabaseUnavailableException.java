package net.shamansoft.cookbook.exception;

/**
 * Thrown when database operations fail due to connectivity or timeout issues
 */
public class DatabaseUnavailableException extends RuntimeException {

    public DatabaseUnavailableException(String message) {
        super(message);
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

