package net.shamansoft.cookbook.service.gemini;

import java.util.List;
import java.util.Set;

/**
 * Optional per-request overrides for Gemini generation parameters, used only by the
 * local-profile debug endpoint to support fast parameter tuning without an app restart.
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
        List<String> stopSequences
) {
    // Allow-list prevents an arbitrary string from being formatted into the Gemini request URL.
    private static final Set<String> ALLOWED_MODELS = Set.of(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-1.5-pro",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash"
    );

    private static final int MAX_STOP_SEQUENCES = 5;
    private static final int MAX_STOP_SEQUENCE_LENGTH = 100;

    public boolean isEmpty() {
        return temperature == null && topP == null && maxOutputTokens == null
                && thinkingBudget == null && model == null && topK == null && seed == null
                && presencePenalty == null && frequencyPenalty == null && stopSequences == null;
    }

    /**
     * @return a human-readable validation error, or null if all provided fields are valid.
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
        if (model != null && !ALLOWED_MODELS.contains(model)) {
            return "model must be one of: " + ALLOWED_MODELS;
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
