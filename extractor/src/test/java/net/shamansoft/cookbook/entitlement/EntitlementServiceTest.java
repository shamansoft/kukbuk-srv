package net.shamansoft.cookbook.entitlement;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock
    EntitlementRepository entitlementRepository;
    @Mock
    UserProfileRepository userProfileRepository;
    @Mock
    EntitlementPlanConfig planConfig;

    private SimpleMeterRegistry meterRegistry;
    private EntitlementService service;

    private static final String USER_ID = "user123";
    private static final Instant FIXED_NOW = Instant.parse("2026-03-08T10:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-03-08T00:00:00Z");
    private static final Instant WINDOW_RESET = Instant.parse("2026-03-09T00:00:00Z");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        lenient().when(planConfig.timeouts()).thenReturn(new EntitlementPlanConfig.Timeouts(500, 1000));
        service = new EntitlementService(entitlementRepository, userProfileRepository, planConfig, meterRegistry, clock);
    }

    // --- ALLOWED_PAID ---

    @Test
    void check_paidTierHint_returnsPaid_noProfileLoad() {
        EntitlementResult result = service.check(USER_ID, UserTier.PRO, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_PAID);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
        verify(userProfileRepository, never()).findByUserId(any());
    }

    @Test
    void check_paidTierHint_enterprise_returnsPaid() {
        EntitlementResult result = service.check(USER_ID, UserTier.ENTERPRISE, Operation.YOUTUBE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_PAID);
        verify(userProfileRepository, never()).findByUserId(any());
    }

    @Test
    void check_paidTierHint_emitsAllowedPaidCounter() {
        service.check(USER_ID, UserTier.PRO, Operation.RECIPE_EXTRACTION);

        Counter counter = meterRegistry.counter("entitlement.check",
                "operation", "RECIPE_EXTRACTION",
                "outcome", "ALLOWED_PAID");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // --- ALLOWED_FREE_QUOTA ---

    @Test
    void check_nullTierHint_quotaAvailable_returnsAllowedFreeQuota() throws Exception {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                1, 5, WINDOW_RESET, true);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_FREE_QUOTA);
        assertThat(result.remainingQuota()).isEqualTo(4); // limit(5) - count(1)
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isEqualTo(WINDOW_RESET);
    }

    @Test
    void check_freeTierQuota_emitsCorrectCounter() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                2, 5, WINDOW_RESET, true);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));

        service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        Counter counter = meterRegistry.counter("entitlement.check",
                "operation", "RECIPE_EXTRACTION",
                "outcome", "ALLOWED_FREE_QUOTA");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void check_freeTierHint_skipsProfileLoad_checksQuota() {
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                1, 5, WINDOW_RESET, true);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));

        EntitlementResult result = service.check(USER_ID, UserTier.FREE, Operation.RECIPE_EXTRACTION);

        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_FREE_QUOTA);
        verify(userProfileRepository, never()).findByUserId(any());
    }

    // --- ALLOWED_CREDIT ---

    @Test
    void check_quotaExhausted_creditAvailable_returnsAllowedCredit() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        when(entitlementRepository.deductCredit(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(OptionalInt.of(2)));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_CREDIT);
        assertThat(result.remainingQuota()).isEqualTo(0);
        assertThat(result.remainingCredits()).isEqualTo(2); // actual remaining credits returned
        assertThat(result.resetsAt()).isEqualTo(WINDOW_RESET);
    }

    @Test
    void check_allowedCredit_emitsCorrectCounter() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        when(entitlementRepository.deductCredit(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(OptionalInt.of(2)));

        service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        Counter counter = meterRegistry.counter("entitlement.check",
                "operation", "RECIPE_EXTRACTION",
                "outcome", "ALLOWED_CREDIT");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // --- DENIED_QUOTA ---

    @Test
    void check_quotaExhausted_noCredits_returnsDeniedQuota() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        when(entitlementRepository.deductCredit(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(OptionalInt.empty()));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.DENIED_QUOTA);
        assertThat(result.remainingQuota()).isEqualTo(0);
        assertThat(result.resetsAt()).isEqualTo(WINDOW_RESET);
    }

    @Test
    void check_deniedQuota_emitsCorrectCounter() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        when(entitlementRepository.deductCredit(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(OptionalInt.empty()));

        service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        Counter counter = meterRegistry.counter("entitlement.check",
                "operation", "RECIPE_EXTRACTION",
                "outcome", "DENIED_QUOTA");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // --- CIRCUIT_OPEN ---

    @Test
    void check_repoThrowsException_returnsCircuitOpen() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        CompletableFuture<QuotaWindow> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Firestore timeout"));
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(failedFuture);

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.CIRCUIT_OPEN);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
    }

    @Test
    void check_circuitOpen_emitsCorrectCounter() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        CompletableFuture<QuotaWindow> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Firestore timeout"));
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(failedFuture);

        service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        Counter counter = meterRegistry.counter("entitlement.check",
                "operation", "RECIPE_EXTRACTION",
                "outcome", "CIRCUIT_OPEN");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // --- Profile load edge cases ---

    @Test
    void check_profileLoadTimesOut_defaultsToFree_checksQuota() {
        CompletableFuture<Optional<net.shamansoft.cookbook.repository.firestore.model.UserProfile>> neverCompletes =
                new CompletableFuture<>();
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(neverCompletes);
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                1, 5, WINDOW_RESET, true);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        // Even with profile timeout, should default to FREE and check quota
        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_FREE_QUOTA);
    }

    @Test
    void check_nullTierHint_callsUserProfileRepository() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                1, 5, WINDOW_RESET, true);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));

        service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        verify(userProfileRepository).findByUserId(USER_ID);
    }

    // --- deductCredit error paths yield DENIED_QUOTA (not CIRCUIT_OPEN) ---

    @Test
    void check_deductCreditThrowsExecutionException_returnsDeniedQuota() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        CompletableFuture<OptionalInt> failedCredit = new CompletableFuture<>();
        failedCredit.completeExceptionally(new RuntimeException("Firestore timeout on credit"));
        when(entitlementRepository.deductCredit(USER_ID)).thenReturn(failedCredit);

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        // deductCredit failure → DENIED_QUOTA (not CIRCUIT_OPEN)
        assertThat(result.allowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.DENIED_QUOTA);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isEqualTo(WINDOW_RESET);
    }

    @Test
    void check_deductCreditInterrupted_returnsDeniedQuota() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        // Simulate InterruptedException by overriding get() on the future
        CompletableFuture<OptionalInt> interruptedFuture = new CompletableFuture<>() {
            @Override
            public OptionalInt get() throws InterruptedException {
                throw new InterruptedException("credit deduction interrupted");
            }
        };
        when(entitlementRepository.deductCredit(USER_ID)).thenReturn(interruptedFuture);

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        // InterruptedException → DENIED_QUOTA (not CIRCUIT_OPEN)
        assertThat(result.allowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.DENIED_QUOTA);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isEqualTo(WINDOW_RESET);
    }

    @Test
    void check_checkAndIncrementInterrupted_returnsCircuitOpen() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        CompletableFuture<QuotaWindow> interruptedFuture = new CompletableFuture<>() {
            @Override
            public QuotaWindow get() throws InterruptedException {
                throw new InterruptedException("quota check interrupted");
            }
        };
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(interruptedFuture);

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        // InterruptedException on checkAndIncrement → CIRCUIT_OPEN (fail-open, unlike deductCredit)
        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.CIRCUIT_OPEN);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
    }

    @Test
    void check_profileResolvesToPaidTier_returnsPaidNoQuotaCheck() {
        UserProfile proProfile = UserProfile.builder().tier(UserTier.PRO).build();
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(proProfile)));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_PAID);
        assertThat(result.remainingQuota()).isEqualTo(-1);
        assertThat(result.remainingCredits()).isNull();
        assertThat(result.resetsAt()).isNull();
        verify(entitlementRepository, never()).checkAndIncrement(any(), any(), any(), anyInt());
    }

    @Test
    void check_allowedCredit_remainingCreditsIsReturned() {
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(planConfig.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).thenReturn(5);
        QuotaWindow window = new QuotaWindow(USER_ID, Operation.RECIPE_EXTRACTION, "20260308",
                5, 5, WINDOW_RESET, false);
        when(entitlementRepository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, 5))
                .thenReturn(CompletableFuture.completedFuture(window));
        when(entitlementRepository.deductCredit(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(OptionalInt.of(4)));

        EntitlementResult result = service.check(USER_ID, null, Operation.RECIPE_EXTRACTION);

        assertThat(result.allowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(EntitlementOutcome.ALLOWED_CREDIT);
        assertThat(result.remainingCredits()).isNotNull();
        assertThat(result.remainingCredits()).isEqualTo(4);
    }
}
