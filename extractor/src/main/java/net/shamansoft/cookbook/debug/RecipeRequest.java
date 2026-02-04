package net.shamansoft.cookbook.debug;

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
        Boolean dumpResultYaml
) {
    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }

    public String getReturnFormat() {
        return returnFormat != null ? returnFormat.toLowerCase() : "yaml";
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
}
