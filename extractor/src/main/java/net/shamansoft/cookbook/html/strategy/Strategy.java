package net.shamansoft.cookbook.html.strategy;

import java.util.List;

public enum Strategy {
    STRUCTURED_DATA,
    SECTION_BASED,
    CONTENT_FILTER,
    FALLBACK,
    DISABLED;

    /**
     * Strategies ordered from most restrictive to least restrictive.
     * Used by AdaptiveCleaningTransformerService to step through strategies
     * when the LLM signals the HTML may be over-cleaned.
     */
    public static final List<Strategy> ADAPTIVE_ORDER =
            List.of(STRUCTURED_DATA, SECTION_BASED, CONTENT_FILTER, FALLBACK);
}
