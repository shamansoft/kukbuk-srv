package net.shamansoft.cookbook.service;

import java.util.List;

/**
 * Optional per-request overrides for LLM generation parameters, used only by the
 * local-profile debug endpoint to support fast parameter tuning without an app restart.
 * <p>
 * This is the union of what the supported providers accept. {@link #validationError()} checks
 * value ranges only; which fields and models a given provider supports is checked by that
 * provider's {@link TunableTransformer#validateOverrides(GenerationOverrides)}.
 * <p>
 * {@code safetyThreshold}, {@code responseMimeType}/{@code responseSchema}, and the prompt
 * text itself are intentionally NOT overridable here — see docs/specs/gemini-tuning.md for why.
 */
public record GenerationOverrides(
        Float temperature,
        Double topP,
        Integer maxOutputTokens,
        Integer thinkingBudget,
        String model,
        Integer topK,
        Integer seed,
        Float presencePenalty,
        Float frequencyPenalty,
        List<String> stopSequences,
        String reasoningEffort
) {
    private static final int MAX_STOP_SEQUENCES = 5;
    private static final int MAX_STOP_SEQUENCE_LENGTH = 100;

    public boolean isEmpty() {
        return temperature == null && topP == null && maxOutputTokens == null
                && thinkingBudget == null && model == null && topK == null && seed == null
                && presencePenalty == null && frequencyPenalty == null && stopSequences == null
                && reasoningEffort == null;
    }

    /**
     * @return a human-readable validation error, or null if all provided fields are in range.
     */
    public String validationError() {
        if (temperature != null && (temperature < 0f || temperature > 2f)) {
            return "temperature must be between 0.0 and 2.0";
        }
        if (topP != null && (topP < 0.0 || topP > 1.0)) {
            return "topP must be between 0.0 and 1.0";
        }
        if (maxOutputTokens != null && (maxOutputTokens < 1 || maxOutputTokens > 65536)) {
            return "maxOutputTokens must be between 1 and 65536";
        }
        if (thinkingBudget != null && thinkingBudget < -1) {
            return "thinkingBudget must be -1 (unlimited) or >= 0";
        }
        if (topK != null && (topK < 1 || topK > 100)) {
            return "topK must be between 1 and 100";
        }
        if (presencePenalty != null && (presencePenalty < -2f || presencePenalty > 2f)) {
            return "presencePenalty must be between -2.0 and 2.0";
        }
        if (frequencyPenalty != null && (frequencyPenalty < -2f || frequencyPenalty > 2f)) {
            return "frequencyPenalty must be between -2.0 and 2.0";
        }
        if (stopSequences != null) {
            if (stopSequences.size() > MAX_STOP_SEQUENCES) {
                return "stopSequences must contain at most " + MAX_STOP_SEQUENCES + " entries";
            }
            for (String s : stopSequences) {
                if (s == null || s.length() > MAX_STOP_SEQUENCE_LENGTH) {
                    return "stopSequences entries must be non-null and at most "
                            + MAX_STOP_SEQUENCE_LENGTH + " characters";
                }
            }
        }
        return null;
    }
}
