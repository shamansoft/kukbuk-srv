package net.shamansoft.cookbook.entitlement;

import java.time.Instant;

/**
 * Result of an entitlement check.
 *
 * @param allowed          whether the operation is allowed
 * @param outcome          the specific outcome classification
 * @param remainingQuota   remaining daily quota for FREE users; -1 for unlimited (paid); -1 for circuit-open
 * @param remainingCredits remaining credits (null for paid users)
 * @param resetsAt         when the quota window resets (null for unlimited/circuit-open)
 */
public record EntitlementResult(
        boolean allowed,
        EntitlementOutcome outcome,
        int remainingQuota,
        Integer remainingCredits,
        Instant resetsAt
) {
    /**
     * Factory for paid-tier users: always allowed, no quota tracking.
     */
    public static EntitlementResult paid() {
        return new EntitlementResult(true, EntitlementOutcome.ALLOWED_PAID, -1, null, null);
    }

    /**
     * Factory for circuit-open (Firestore timeout): fail open, no quota info.
     */
    public static EntitlementResult circuitOpen() {
        return new EntitlementResult(true, EntitlementOutcome.CIRCUIT_OPEN, -1, null, null);
    }
}
