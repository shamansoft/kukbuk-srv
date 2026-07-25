package net.shamansoft.cookbook.service.gemini;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationOverridesTest {

    private static final GenerationOverrides EMPTY =
            new GenerationOverrides(null, null, null, null, null, null, null, null, null, null);

    @Test
    void emptyOverridesHasNoValidationError() {
        assertThat(EMPTY.validationError()).isNull();
    }

    @Test
    void isEmptyReturnsTrueWhenAllFieldsNull() {
        assertThat(EMPTY.isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenAnyFieldSet() {
        assertThat(new GenerationOverrides(0.5f, null, null, null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void acceptsValidTemperature() {
        GenerationOverrides overrides = new GenerationOverrides(1.0f, null, null, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsNegativeTemperature() {
        GenerationOverrides overrides = new GenerationOverrides(-0.1f, null, null, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("temperature");
    }

    @Test
    void rejectsTemperatureAboveTwo() {
        GenerationOverrides overrides = new GenerationOverrides(2.1f, null, null, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("temperature");
    }

    @Test
    void rejectsTopPBelowZero() {
        GenerationOverrides overrides = new GenerationOverrides(null, -0.01, null, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("topP");
    }

    @Test
    void rejectsTopPAboveOne() {
        GenerationOverrides overrides = new GenerationOverrides(null, 1.01, null, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("topP");
    }

    @Test
    void rejectsMaxOutputTokensBelowOne() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, 0, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("maxOutputTokens");
    }

    @Test
    void rejectsMaxOutputTokensAboveCap() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, 100_000, null, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("maxOutputTokens");
    }

    @Test
    void acceptsThinkingBudgetOfMinusOneAsUnlimited() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, -1, null, null, null, null, null, null);
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsThinkingBudgetBelowMinusOne() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, -2, null, null, null, null, null, null);
        assertThat(overrides.validationError()).contains("thinkingBudget");
    }

    @Test
    void acceptsAllowListedModel() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, "gemini-2.5-flash", null, null, null, null, null);
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsModelNotInAllowList() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, "../../v1beta2/other", null, null, null, null, null);
        assertThat(overrides.validationError()).contains("model");
    }

    @Test
    void acceptsGemini3Family() {
        assertThat(new GenerationOverrides(null, null, null, null, "gemini-3.1-flash-lite", null, null, null, null, null).validationError()).isNull();
        assertThat(new GenerationOverrides(null, null, null, null, "gemini-3.5-flash-lite", null, null, null, null, null).validationError()).isNull();
        assertThat(new GenerationOverrides(null, null, null, null, "gemini-3.6-flash", null, null, null, null, null).validationError()).isNull();
    }

    @Test
    void acceptsValidTopK() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, null, 40, null, null, null, null);
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsTopKBelowOne() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, null, 0, null, null, null, null);
        assertThat(overrides.validationError()).contains("topK");
    }

    @Test
    void rejectsTopKAboveCap() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, null, 101, null, null, null, null);
        assertThat(overrides.validationError()).contains("topK");
    }

    @Test
    void acceptsAnySeedValue() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, null, null, 42, null, null, null);
        assertThat(overrides.validationError()).isNull();
        GenerationOverrides negativeSeed = new GenerationOverrides(null, null, null, null, null, null, -1, null, null, null);
        assertThat(negativeSeed.validationError()).isNull();
    }

    @Test
    void acceptsValidPresencePenalty() {
        GenerationOverrides overrides = new GenerationOverrides(null, null, null, null, null, null, null, 1.0f, null, null);
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsPresencePenaltyOutOfRange() {
        assertThat(new GenerationOverrides(null, null, null, null, null, null, null, -2.1f, null, null).validationError())
                .contains("presencePenalty");
        assertThat(new GenerationOverrides(null, null, null, null, null, null, null, 2.1f, null, null).validationError())
                .contains("presencePenalty");
    }

    @Test
    void rejectsFrequencyPenaltyOutOfRange() {
        assertThat(new GenerationOverrides(null, null, null, null, null, null, null, null, -2.1f, null).validationError())
                .contains("frequencyPenalty");
        assertThat(new GenerationOverrides(null, null, null, null, null, null, null, null, 2.1f, null).validationError())
                .contains("frequencyPenalty");
    }

    @Test
    void acceptsValidStopSequences() {
        GenerationOverrides overrides = new GenerationOverrides(
                null, null, null, null, null, null, null, null, null, List.of("END", "STOP"));
        assertThat(overrides.validationError()).isNull();
    }

    @Test
    void rejectsTooManyStopSequences() {
        GenerationOverrides overrides = new GenerationOverrides(
                null, null, null, null, null, null, null, null, null,
                List.of("a", "b", "c", "d", "e", "f"));
        assertThat(overrides.validationError()).contains("stopSequences");
    }

    @Test
    void rejectsStopSequenceTooLong() {
        GenerationOverrides overrides = new GenerationOverrides(
                null, null, null, null, null, null, null, null, null,
                List.of("x".repeat(101)));
        assertThat(overrides.validationError()).contains("stopSequences");
    }
}
