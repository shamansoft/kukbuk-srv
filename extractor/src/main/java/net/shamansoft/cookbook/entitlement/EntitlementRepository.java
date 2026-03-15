package net.shamansoft.cookbook.entitlement;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for entitlement quota management.
 * V1 implementation: Firestore. Future: Redis.
 */
public interface EntitlementRepository {

    /**
     * Atomically check and increment the quota counter.
     * Returns the window state after the attempt (count reflects post-increment
     * value if allowed, pre-attempt value if denied).
     *
     * @param userId      the user identifier
     * @param operation   the operation being checked
     * @param windowStart the start of the current time window
     * @param limit       daily limit (-1 for unlimited)
     * @return CompletableFuture of QuotaWindow after check; may throw on timeout
     */
    CompletableFuture<QuotaWindow> checkAndIncrement(
            String userId, Operation operation, Instant windowStart, int limit);

    /**
     * Atomically decrement credits by 1 from users/{userId}.
     * Returns true if a credit was available and deducted.
     *
     * @param userId the user identifier
     * @return CompletableFuture of true if credit deducted, false if no credits or timeout
     */
    CompletableFuture<Boolean> deductCredit(String userId);

    /**
     * Admin: set tier and/or credits on users/{userId}.
     *
     * @param userId  the user identifier
     * @param tier    the new tier to set
     * @param credits the new credit balance
     * @return CompletableFuture that completes when update is done
     */
    CompletableFuture<Void> updateTierAndCredits(String userId, UserTier tier, int credits);
}
