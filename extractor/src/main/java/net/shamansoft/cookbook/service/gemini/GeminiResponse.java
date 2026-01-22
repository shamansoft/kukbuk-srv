package net.shamansoft.cookbook.service.gemini;

public record GeminiResponse<T>(Code code, T data, String errorMessage) {

    public static <T> GeminiResponse<T> success(T data) {
        return new GeminiResponse<>(Code.SUCCESS, data, null);
    }

    public static <T> GeminiResponse<T> failure(Code code, String errorMessage) {
        return new GeminiResponse<>(code, null, errorMessage);
    }

    public enum Code {
        SUCCESS,
        BLOCKED,
        OTHER
    }
}
