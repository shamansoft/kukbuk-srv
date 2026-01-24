package net.shamansoft.cookbook.dto;

/**
 * TEMPORARY: Request DTO for testing Gemini transformation without authentication.
 * TODO: Remove this before production deployment.
 */
public record TestTransformRequest(
        String url,
        String text
) {
    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }
}
