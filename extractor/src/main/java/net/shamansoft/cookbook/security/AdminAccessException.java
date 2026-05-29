package net.shamansoft.cookbook.security;

public class AdminAccessException extends RuntimeException {

    public AdminAccessException(String message) {
        super(message);
    }
}
