package net.shamansoft.cookbook.entitlement;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaWindowTest {

    @Test
    void construction_withinLimit() {
        Instant resetAt = Instant.parse("2026-03-09T00:00:00Z");
        QuotaWindow window = new QuotaWindow("user123", Operation.RECIPE_EXTRACTION, "20260308", 3, 5, resetAt, true);

        assertThat(window.userId()).isEqualTo("user123");
        assertThat(window.operation()).isEqualTo(Operation.RECIPE_EXTRACTION);
        assertThat(window.windowKey()).isEqualTo("20260308");
        assertThat(window.count()).isEqualTo(3);
        assertThat(window.limit()).isEqualTo(5);
        assertThat(window.resetAt()).isEqualTo(resetAt);
        assertThat(window.withinLimit()).isTrue();
    }

    @Test
    void construction_limitExceeded() {
        Instant resetAt = Instant.parse("2026-03-09T00:00:00Z");
        QuotaWindow window = new QuotaWindow("user456", Operation.YOUTUBE_EXTRACTION, "20260308", 1, 1, resetAt, false);

        assertThat(window.userId()).isEqualTo("user456");
        assertThat(window.operation()).isEqualTo(Operation.YOUTUBE_EXTRACTION);
        assertThat(window.withinLimit()).isFalse();
        assertThat(window.count()).isEqualTo(1);
        assertThat(window.limit()).isEqualTo(1);
    }

    @Test
    void construction_firstRequest_countZero() {
        Instant resetAt = Instant.parse("2026-03-09T00:00:00Z");
        QuotaWindow window = new QuotaWindow("user789", Operation.RECIPE_EXTRACTION, "20260308", 0, 5, resetAt, true);

        assertThat(window.count()).isEqualTo(0);
        assertThat(window.withinLimit()).isTrue();
    }
}
