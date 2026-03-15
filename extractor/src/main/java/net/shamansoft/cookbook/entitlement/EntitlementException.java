package net.shamansoft.cookbook.entitlement;

public class EntitlementException extends RuntimeException {

    private final EntitlementResult result;

    public EntitlementException(EntitlementResult result) {
        super("Entitlement check failed: " + result.outcome());
        this.result = result;
    }

    public EntitlementResult getResult() {
        return result;
    }
}
