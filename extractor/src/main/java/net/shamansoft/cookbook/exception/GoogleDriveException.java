package net.shamansoft.cookbook.exception;

/**
 * Exception thrown when Google Drive API operations fail.
 * This typically indicates issues with the Google Drive service or network connectivity.
 */
public class GoogleDriveException extends RuntimeException {

    public GoogleDriveException(String message) {
        super(message);
    }

    public GoogleDriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
