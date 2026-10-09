package net.shamansoft.cookbook.debug;

import net.shamansoft.cookbook.service.GenerationOverrides;

import java.util.List;

/**
 * Request DTO for debug/test endpoint - mirrors production flow with configurable options.
 * Only available in non-production environments (local/dev profiles).
 */
public record RecipeRequest(
        // Input (required: either url OR text)
        String url,
        String text,
        String compression,

        // Output format (optional, default: yaml)
        String returnFormat,  // "yaml" or "json"

        // HTML preprocessing strategy (optional, default: auto)
        String cleanHtml,  // "auto", "structured", "section", "content", "raw", "disabled"

        // Processing options (optional, default: false)
        Boolean skipCache,
        Boolean verbose,

        // Debug dump flags (optional, default: false)
        Boolean dumpRawHtml,
        Boolean dumpExtractedHtml,
        Boolean dumpCleanedHtml,
        Boolean dumpLLMResponse,
        Boolean dumpResultJson,
        Boolean dumpResultYaml,

        // Generation parameter overrides for tuning (optional; omitted fields fall back to
        // configured defaults). safetyThreshold is deliberately not overridable here.
        // When any of these is set, the request bypasses caching entirely (no read, no write)
        // and routes directly to the selected provider's transformer, skipping the
        // adaptive-cleaning/validation retry chain used by production traffic.
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

        // LLM provider to call (optional, default: gemini): "gemini" or "openai".
        // Any provider other than the default is treated like an override: one raw call, no cache.
        String provider,
        // OpenAI reasoning effort override, e.g. "none", "low", "medium", "high"
        String reasoningEffort
) {
    public static final String DEFAULT_PROVIDER = "gemini";

    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }

    public String getReturnFormat() {
        return returnFormat != null ? returnFormat.toLowerCase() : "yaml";
    }

    public String getProvider() {
        return provider != null && !provider.isBlank() ? provider.trim().toLowerCase() : DEFAULT_PROVIDER;
    }

    public String getCleanHtml() {
        return cleanHtml != null ? cleanHtml.toLowerCase() : "auto";
    }

    public boolean isSkipCache() {
        return skipCache != null && skipCache;
    }

    public boolean isVerbose() {
        return verbose != null && verbose;
    }

    public boolean isDumpRawHtml() {
        return dumpRawHtml != null && dumpRawHtml;
    }

    public boolean isDumpExtractedHtml() {
        return dumpExtractedHtml != null && dumpExtractedHtml;
    }

    public boolean isDumpCleanedHtml() {
        return dumpCleanedHtml != null && dumpCleanedHtml;
    }

    public boolean isDumpLLMResponse() {
        return dumpLLMResponse != null && dumpLLMResponse;
    }

    public boolean isDumpResultJson() {
        return dumpResultJson != null && dumpResultJson;
    }

    public boolean isDumpResultYaml() {
        return dumpResultYaml != null && dumpResultYaml;
    }

    public GenerationOverrides overrides() {
        return new GenerationOverrides(temperature, topP, maxOutputTokens, thinkingBudget, model,
                topK, seed, presencePenalty, frequencyPenalty, stopSequences, reasoningEffort);
    }
}
