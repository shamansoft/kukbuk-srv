package net.shamansoft.cookbook.entitlement;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for entitlement plans.
 * Registered via @EnableConfigurationProperties in EntitlementConfig.
 *
 * Validates on startup that every UserTier × Operation pair has an explicit
 * config entry — turns silent zero-quota into a loud startup failure.
 */
@ConfigurationProperties(prefix = "entitlement")
public record EntitlementPlanConfig(
        Map<UserTier, Map<Operation, PlanLimit>> plans,
        Timeouts timeouts
) {

    public record PlanLimit(int daily) {}

    public record Timeouts(int checkMs, int incrementMs) {}

    /**
     * Returns the daily limit for the given tier and operation.
     * Returns 0 if the combination is not configured (should not happen after startup validation).
     *
     * @param tier      the user's tier
     * @param operation the operation to check
     * @return daily limit (-1 for unlimited, 0 if missing config)
     */
    public int dailyLimit(UserTier tier, Operation operation) {
        if (plans == null) return 0;
        Map<Operation, PlanLimit> tierPlans = plans.get(tier);
        if (tierPlans == null) return 0;
        PlanLimit limit = tierPlans.get(operation);
        return limit == null ? 0 : limit.daily();
    }

    /**
     * Validates that every UserTier × Operation pair has an explicit config entry.
     * Throws IllegalStateException on startup if any combination is missing.
     */
    @PostConstruct
    public void validate() {
        for (UserTier tier : UserTier.values()) {
            for (Operation op : Operation.values()) {
                Map<Operation, PlanLimit> tierPlans = plans == null ? null : plans.get(tier);
                if (tierPlans == null || !tierPlans.containsKey(op)) {
                    throw new IllegalStateException(
                            "Missing entitlement configuration for tier=" + tier +
                            " operation=" + op +
                            ". Add an entry under entitlement.plans." + tier + "." + op);
                }
            }
        }
    }
}
