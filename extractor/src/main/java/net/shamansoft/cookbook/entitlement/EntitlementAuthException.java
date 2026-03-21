package net.shamansoft.cookbook.entitlement;

/**
 * Thrown by {@link EntitlementAspect} when the request reaches a protected method
 * without a userId in the request context (unauthenticated request).
 * Mapped to HTTP 401 by {@link net.shamansoft.cookbook.CookbookExceptionHandler}.
 */
public class EntitlementAuthException extends RuntimeException {

    public EntitlementAuthException(String message) {
        super(message);
    }
}
