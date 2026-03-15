package net.shamansoft.cookbook.entitlement;

/**
 * Operations that consume user quota.
 * RECIPE_EXTRACTION covers both HTML and custom-description operations (see RFC §2.4).
 */
public enum Operation {
    RECIPE_EXTRACTION,
    YOUTUBE_EXTRACTION
}
