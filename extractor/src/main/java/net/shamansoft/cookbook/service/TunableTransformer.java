package net.shamansoft.cookbook.service;

/**
 * A {@link Transformer} backed by one LLM provider that the local-profile debug endpoint can
 * select by name and call with per-request generation overrides.
 * <p>
 * {@link #transformWithOverrides} makes exactly one raw LLM call: no caching, no adaptive
 * cleaning and no validation retry, so a tuning result reflects exactly the given parameters.
 */
public interface TunableTransformer extends Transformer {

    /**
     * @return the provider name the debug endpoint selects this transformer by, e.g. "gemini"
     */
    String provider();

    /**
     * @return the model used when a request does not override it
     */
    String defaultModel();

    /**
     * Checks what {@link GenerationOverrides#validationError()} cannot: whether this provider
     * supports the given model and fields, and whether the provider is configured at all.
     *
     * @return a human-readable error, or null if the overrides can be sent to this provider
     */
    String validateOverrides(GenerationOverrides overrides);

    /**
     * @param content   the (already cleaned) HTML or text to extract a recipe from
     * @param overrides generation overrides; may be null or empty to use configured defaults
     */
    Response transformWithOverrides(String content, GenerationOverrides overrides);
}
