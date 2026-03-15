package net.shamansoft.cookbook.entitlement;

import java.time.Instant;

/**
 * Represents a per-user, per-operation quota tracking window.
 *
 * @param userId      the user identifier
 * @param operation   the operation being tracked
 * @param windowKey   the date-based key (YYYYMMDD) identifying this window
 * @param count       current usage count within the window
 * @param limit       the maximum allowed count (-1 = unlimited)
 * @param resetAt     when this window resets
 * @param withinLimit whether the current count is within the allowed limit
 */
public record QuotaWindow(
        String userId,
        Operation operation,
        String windowKey,
        int count,
        int limit,
        Instant resetAt,
        boolean withinLimit
) {
}
