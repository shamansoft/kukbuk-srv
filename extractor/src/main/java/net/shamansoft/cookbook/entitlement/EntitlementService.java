package net.shamansoft.cookbook.entitlement;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementRepository entitlementRepository;
    private final UserProfileRepository userProfileRepository;
    private final EntitlementPlanConfig planConfig;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * Check entitlement for the given user and operation.
     *
     * @param userId    the user identifier
     * @param tierHint  tier from JWT claim (null triggers profile load for FREE users)
     * @param operation the operation being requested
     * @return EntitlementResult indicating whether the operation is allowed
     */
    public EntitlementResult check(String userId, UserTier tierHint, Operation operation) {
        // Step 1-2: Determine tier (skip profile load if tier hint provided)
        UserTier tier;
        if (tierHint != null) {
            tier = tierHint;
        } else {
            tier = loadTierFromProfile(userId);
        }

        // Step 3: Paid users skip quota entirely
        if (tier != UserTier.FREE) {
            record(operation, EntitlementOutcome.ALLOWED_PAID);
            return EntitlementResult.paid();
        }

        // Step 4: FREE tier — check and increment quota
        int limit = planConfig.dailyLimit(tier, operation);
        Instant windowStart = clock.instant().truncatedTo(ChronoUnit.DAYS);

        QuotaWindow window;
        try {
            window = entitlementRepository.checkAndIncrement(userId, operation, windowStart, limit).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Quota check interrupted for userId={}, operation={}; failing open", userId, operation);
            record(operation, EntitlementOutcome.CIRCUIT_OPEN);
            return EntitlementResult.circuitOpen();
        } catch (ExecutionException e) {
            log.warn("Quota check failed for userId={}, operation={}; failing open (CIRCUIT_OPEN): {}",
                    userId, operation, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            record(operation, EntitlementOutcome.CIRCUIT_OPEN);
            return EntitlementResult.circuitOpen();
        }

        // Step 5: Within daily quota
        if (window.withinLimit()) {
            int remaining = limit < 0 ? -1 : limit - window.count();
            record(operation, EntitlementOutcome.ALLOWED_FREE_QUOTA);
            return new EntitlementResult(true, EntitlementOutcome.ALLOWED_FREE_QUOTA, remaining, null, window.resetAt());
        }

        // Step 6: Quota exhausted — try credits fast-path
        // Note: profile.credits() read will be added in Task 8 when UserProfile gains the credits field.
        // For now, deductCredit() atomically checks and deducts, returning false if no credits.
        boolean credited = false;
        try {
            credited = entitlementRepository.deductCredit(userId).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Credit deduction interrupted for userId={}", userId);
        } catch (ExecutionException e) {
            log.warn("Credit deduction failed for userId={}: {}", userId,
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }

        if (credited) {
            record(operation, EntitlementOutcome.ALLOWED_CREDIT);
            // remainingCredits=0 is a placeholder; Task 8 will populate from UserProfile.credits
            return new EntitlementResult(true, EntitlementOutcome.ALLOWED_CREDIT, 0, 0, window.resetAt());
        } else {
            record(operation, EntitlementOutcome.DENIED_QUOTA);
            return new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, 0, window.resetAt());
        }
    }

    /**
     * Load user tier from profile. Defaults to FREE on empty profile or timeout.
     * Task 8 will update this to read profile.tier() when the field is added to UserProfile.
     */
    private UserTier loadTierFromProfile(String userId) {
        try {
            return userProfileRepository.findByUserId(userId)
                    .orTimeout(300, TimeUnit.MILLISECONDS)
                    .get()
                    .map(profile -> UserTier.FREE) // TODO Task 8: replace with profile.tier()
                    .orElse(UserTier.FREE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Profile load interrupted for userId={}; defaulting to FREE", userId);
            return UserTier.FREE;
        } catch (Exception e) {
            log.warn("Failed to load tier from profile for userId={}; defaulting to FREE: {}", userId, e.getMessage());
            return UserTier.FREE;
        }
    }

    private void record(Operation operation, EntitlementOutcome outcome) {
        meterRegistry.counter("entitlement.check",
                        "operation", operation.name(),
                        "outcome", outcome.name())
                .increment();
    }
}
