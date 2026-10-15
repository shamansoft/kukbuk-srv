package net.shamansoft.cookbook.service.gemini;

public record GeminiResponse<T>(Code code, T data, String errorMessage, String rawResponse) {

    public static <T> GeminiResponse<T> success(T data, String rawResponse) {
        return new GeminiResponse<>(Code.SUCCESS, data, null, rawResponse);
    }

    public static <T> GeminiResponse<T> failure(Code code, String errorMessage) {
        return new GeminiResponse<>(code, null, errorMessage, null);
    }

    public enum Code {
        SUCCESS,
        BLOCKED,
        PARSE_ERROR,
        /**
         * No usable content: the candidate has no text, or it stopped early (finishReason RECITATION, SAFETY,
         * OTHER, ...) and the partial text is not valid JSON.
         */
        NO_CONTENT,
        /** Output hit maxOutputTokens and the JSON is cut off. */
        TRUNCATED,
        OTHER;

        /** Failures where the same input can succeed with different sampling or a larger output budget. */
        public boolean isRetryable() {
            return this == NO_CONTENT || this == TRUNCATED;
        }
    }
}
