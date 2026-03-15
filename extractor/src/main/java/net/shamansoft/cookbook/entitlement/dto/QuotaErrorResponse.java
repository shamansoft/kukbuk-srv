package net.shamansoft.cookbook.entitlement.dto;

import java.time.Instant;

/**
 * Error response body returned with HTTP 429 Too Many Requests when quota is exhausted.
 *
 * @param error            always "QUOTA_EXCEEDED"
 * @param remainingQuota   remaining daily quota (0 when exhausted)
 * @param remainingCredits remaining credits (null for paid users)
 * @param resetsAt         when the quota window resets (null for circuit-open)
 */
public record QuotaErrorResponse(
        String error,
        int remainingQuota,
        Integer remainingCredits,
        Instant resetsAt
) {}
