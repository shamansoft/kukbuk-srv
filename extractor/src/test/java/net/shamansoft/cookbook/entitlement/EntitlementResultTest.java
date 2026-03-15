package net.shamansoft.cookbook.entitlement;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementResultTest {

    @Test
    void paid_returnsAllowedWithCorrectOutcome() {
        EntitlementResult result = EntitlementResult.paid();

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_PAID);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
    }

    @Test
    void circuitOpen_returnsAllowedWithCorrectOutcome() {
        EntitlementResult result = EntitlementResult.circuitOpen();

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.CIRCUIT_OPEN);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
    }

    @Test
    void directConstruction_allowedFreeQuota() {
        Instant resetsAt = Instant.parse("2026-03-09T00:00:00Z");
        EntitlementResult result = new EntitlementResult(true, EntitlementOutcome.ALLOWED_FREE_QUOTA, 4, null, resetsAt);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_FREE_QUOTA);
        assertThat(result.remainingQuota()).isEqualTo(4);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isEqualTo(resetsAt);
    }

    @Test
    void directConstruction_allowedCredit() {
        Instant resetsAt = Instant.parse("2026-03-09T00:00:00Z");
        EntitlementResult result = new EntitlementResult(true, EntitlementOutcome.ALLOWED_CREDIT, 0, 5, resetsAt);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_CREDIT);
        assertThat(result.remainingQuota()).isEqualTo(0);
        assertThat(result.remainingCredits()).isEqualTo(5);
        assertThat(result.resetsAt()).isEqualTo(resetsAt);
    }

    @Test
    void directConstruction_deniedQuota() {
        Instant resetsAt = Instant.parse("2026-03-09T00:00:00Z");
        EntitlementResult result = new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, 0, resetsAt);

        assertThat(result.allowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.DENIED_QUOTA);
        assertThat(result.remainingQuota()).isEqualTo(0);
        assertThat(result.remainingCredits()).isEqualTo(0);
        assertThat(result.resetsAt()).isEqualTo(resetsAt);
    }
}
