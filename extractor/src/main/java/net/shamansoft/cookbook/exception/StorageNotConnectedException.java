package net.shamansoft.cookbook.exception;

/**
 * Thrown when user tries to save a recipe but hasn't connected storage
 */
public class StorageNotConnectedException extends RuntimeException {

    public StorageNotConnectedException(String message) {
        super(message);
    }

}
