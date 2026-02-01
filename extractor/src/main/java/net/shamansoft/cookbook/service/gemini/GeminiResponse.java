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
        OTHER
    }
}
