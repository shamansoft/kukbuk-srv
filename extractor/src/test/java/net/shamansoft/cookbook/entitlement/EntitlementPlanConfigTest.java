package net.shamansoft.cookbook.entitlement;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitlementPlanConfigTest {

    private static EntitlementPlanConfig fullConfig() {
        Map<Operation, EntitlementPlanConfig.PlanLimit> freePlans = new EnumMap<>(Operation.class);
        freePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(5));
        freePlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(1));

        Map<Operation, EntitlementPlanConfig.PlanLimit> proPlans = new EnumMap<>(Operation.class);
        proPlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));
        proPlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));

        Map<Operation, EntitlementPlanConfig.PlanLimit> enterprisePlans = new EnumMap<>(Operation.class);
        enterprisePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));
        enterprisePlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));

        Map<UserTier, Map<Operation, EntitlementPlanConfig.PlanLimit>> plans = new EnumMap<>(UserTier.class);
        plans.put(UserTier.FREE, freePlans);
        plans.put(UserTier.PRO, proPlans);
        plans.put(UserTier.ENTERPRISE, enterprisePlans);

        return new EntitlementPlanConfig(
                plans,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );
    }

    @Test
    void dailyLimit_freeRecipeExtraction_returns5() {
        EntitlementPlanConfig config = fullConfig();
        assertThat(config.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).isEqualTo(5);
    }

    @Test
    void dailyLimit_freeYoutubeExtraction_returns1() {
        EntitlementPlanConfig config = fullConfig();
        assertThat(config.dailyLimit(UserTier.FREE, Operation.YOUTUBE_EXTRACTION)).isEqualTo(1);
    }

    @Test
    void dailyLimit_proRecipeExtraction_returnsUnlimited() {
        EntitlementPlanConfig config = fullConfig();
        assertThat(config.dailyLimit(UserTier.PRO, Operation.RECIPE_EXTRACTION)).isEqualTo(-1);
    }

    @Test
    void dailyLimit_enterpriseYoutubeExtraction_returnsUnlimited() {
        EntitlementPlanConfig config = fullConfig();
        assertThat(config.dailyLimit(UserTier.ENTERPRISE, Operation.YOUTUBE_EXTRACTION)).isEqualTo(-1);
    }

    @Test
    void dailyLimit_missingTier_returnsZero() {
        // Config with only FREE plans
        Map<Operation, EntitlementPlanConfig.PlanLimit> freePlans = new EnumMap<>(Operation.class);
        freePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(5));
        freePlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(1));

        Map<UserTier, Map<Operation, EntitlementPlanConfig.PlanLimit>> plans = new EnumMap<>(UserTier.class);
        plans.put(UserTier.FREE, freePlans);
        // PRO not configured

        EntitlementPlanConfig config = new EntitlementPlanConfig(
                plans,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );

        assertThat(config.dailyLimit(UserTier.PRO, Operation.RECIPE_EXTRACTION)).isEqualTo(0);
    }

    @Test
    void dailyLimit_nullPlans_returnsZero() {
        EntitlementPlanConfig config = new EntitlementPlanConfig(
                null,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );

        assertThat(config.dailyLimit(UserTier.FREE, Operation.RECIPE_EXTRACTION)).isEqualTo(0);
    }

    @Test
    void validate_completeConfig_passes() {
        EntitlementPlanConfig config = fullConfig();
        // Should not throw
        config.validate();
    }

    @Test
    void validate_missingTierEntry_throwsIllegalStateException() {
        // Config missing ENTERPRISE
        Map<Operation, EntitlementPlanConfig.PlanLimit> freePlans = new EnumMap<>(Operation.class);
        freePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(5));
        freePlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(1));

        Map<Operation, EntitlementPlanConfig.PlanLimit> proPlans = new EnumMap<>(Operation.class);
        proPlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));
        proPlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));

        Map<UserTier, Map<Operation, EntitlementPlanConfig.PlanLimit>> plans = new EnumMap<>(UserTier.class);
        plans.put(UserTier.FREE, freePlans);
        plans.put(UserTier.PRO, proPlans);
        // ENTERPRISE missing

        EntitlementPlanConfig config = new EntitlementPlanConfig(
                plans,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENTERPRISE");
    }

    @Test
    void validate_missingOperationEntry_throwsIllegalStateException() {
        // Config with FREE missing YOUTUBE_EXTRACTION
        Map<Operation, EntitlementPlanConfig.PlanLimit> freePlans = new EnumMap<>(Operation.class);
        freePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(5));
        // YOUTUBE_EXTRACTION missing for FREE

        Map<Operation, EntitlementPlanConfig.PlanLimit> proPlans = new EnumMap<>(Operation.class);
        proPlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));
        proPlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));

        Map<Operation, EntitlementPlanConfig.PlanLimit> enterprisePlans = new EnumMap<>(Operation.class);
        enterprisePlans.put(Operation.RECIPE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));
        enterprisePlans.put(Operation.YOUTUBE_EXTRACTION, new EntitlementPlanConfig.PlanLimit(-1));

        Map<UserTier, Map<Operation, EntitlementPlanConfig.PlanLimit>> plans = new EnumMap<>(UserTier.class);
        plans.put(UserTier.FREE, freePlans);
        plans.put(UserTier.PRO, proPlans);
        plans.put(UserTier.ENTERPRISE, enterprisePlans);

        EntitlementPlanConfig config = new EntitlementPlanConfig(
                plans,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FREE")
                .hasMessageContaining("YOUTUBE_EXTRACTION");
    }

    @Test
    void validate_nullPlans_throwsIllegalStateException() {
        EntitlementPlanConfig config = new EntitlementPlanConfig(
                null,
                new EntitlementPlanConfig.Window("UTC"),
                new EntitlementPlanConfig.Timeouts(500, 1000)
        );

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class);
    }
}
