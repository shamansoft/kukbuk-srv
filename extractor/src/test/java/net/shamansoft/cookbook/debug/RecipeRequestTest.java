package net.shamansoft.cookbook.debug;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecipeRequest tests")
class RecipeRequestTest {

    @Test
    @DisplayName("hasUrl returns true when url is set")
    void hasUrlTrue() {
        RecipeRequest request = new RecipeRequest("https://example.com", null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasUrl()).isTrue();
    }

    @Test
    @DisplayName("hasUrl returns false when url is null")
    void hasUrlFalseNull() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasUrl()).isFalse();
    }

    @Test
    @DisplayName("hasUrl returns false when url is empty")
    void hasUrlFalseEmpty() {
        RecipeRequest request = new RecipeRequest("", "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasUrl()).isFalse();
    }

    @Test
    @DisplayName("hasText returns true when text is set")
    void hasTextTrue() {
        RecipeRequest request = new RecipeRequest(null, "<html>content</html>", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasText()).isTrue();
    }

    @Test
    @DisplayName("hasText returns false when text is null")
    void hasTextFalseNull() {
        RecipeRequest request = new RecipeRequest("https://example.com", null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasText()).isFalse();
    }

    @Test
    @DisplayName("hasText returns false when text is empty")
    void hasTextFalseEmpty() {
        RecipeRequest request = new RecipeRequest("https://example.com", "", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.hasText()).isFalse();
    }

    @Test
    @DisplayName("getReturnFormat defaults to 'yaml' when null")
    void getReturnFormatDefaultYaml() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.getReturnFormat()).isEqualTo("yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"YAML", "Yaml", "YaMl"})
    @DisplayName("getReturnFormat normalizes to lowercase")
    void getReturnFormatLowercase(String format) {
        RecipeRequest request = new RecipeRequest(null, "text", null, format, null, null, null, null, null, null, null, null, null);
        assertThat(request.getReturnFormat()).isEqualTo("yaml");
    }

    @Test
    @DisplayName("getReturnFormat returns 'json' in lowercase")
    void getReturnFormatJson() {
        RecipeRequest request = new RecipeRequest(null, "text", null, "JSON", null, null, null, null, null, null, null, null, null);
        assertThat(request.getReturnFormat()).isEqualTo("json");
    }

    @Test
    @DisplayName("getCleanHtml defaults to 'auto' when null")
    void getCleanHtmlDefaultAuto() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.getCleanHtml()).isEqualTo("auto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTO", "Auto", "AuTo"})
    @DisplayName("getCleanHtml normalizes to lowercase")
    void getCleanHtmlLowercase(String strategy) {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, strategy, null, null, null, null, null, null, null, null);
        assertThat(request.getCleanHtml()).isEqualTo("auto");
    }

    @ParameterizedTest
    @CsvSource({
            "raw, raw",
            "RAW, raw",
            "structured, structured",
            "STRUCTURED, structured",
            "section, section",
            "SECTION, section",
            "content, content",
            "CONTENT, content",
            "disabled, disabled",
            "DISABLED, disabled"
    })
    @DisplayName("getCleanHtml handles all valid strategies")
    void getCleanHtmlStrategies(String input, String expected) {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, input, null, null, null, null, null, null, null, null);
        assertThat(request.getCleanHtml()).isEqualTo(expected);
    }

    @Test
    @DisplayName("isSkipCache returns false when null")
    void isSkipCacheFalseNull() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.isSkipCache()).isFalse();
    }

    @Test
    @DisplayName("isSkipCache returns true when true")
    void isSkipCacheTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, true, null, null, null, null, null, null, null);
        assertThat(request.isSkipCache()).isTrue();
    }

    @Test
    @DisplayName("isSkipCache returns false when false")
    void isSkipCacheFalse() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, false, null, null, null, null, null, null, null);
        assertThat(request.isSkipCache()).isFalse();
    }

    @Test
    @DisplayName("isVerbose returns false when null")
    void isVerboseFalseNull() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(request.isVerbose()).isFalse();
    }

    @Test
    @DisplayName("isVerbose returns true when true")
    void isVerboseTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, true, null, null, null, null, null, null);
        assertThat(request.isVerbose()).isTrue();
    }

    @Test
    @DisplayName("isDumpRawHtml returns true when true")
    void isDumpRawHtmlTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, true, null, null, null, null, null);
        assertThat(request.isDumpRawHtml()).isTrue();
    }

    @Test
    @DisplayName("isDumpExtractedHtml returns true when true")
    void isDumpExtractedHtmlTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, true, null, null, null, null);
        assertThat(request.isDumpExtractedHtml()).isTrue();
    }

    @Test
    @DisplayName("isDumpCleanedHtml returns true when true")
    void isDumpCleanedHtmlTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, true, null, null, null);
        assertThat(request.isDumpCleanedHtml()).isTrue();
    }

    @Test
    @DisplayName("isDumpLLMResponse returns true when true")
    void isDumpLLMResponseTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, true, null, null);
        assertThat(request.isDumpLLMResponse()).isTrue();
    }

    @Test
    @DisplayName("isDumpResultJson returns true when true")
    void isDumpResultJsonTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, true, null);
        assertThat(request.isDumpResultJson()).isTrue();
    }

    @Test
    @DisplayName("isDumpResultYaml returns true when true")
    void isDumpResultYamlTrue() {
        RecipeRequest request = new RecipeRequest(null, "text", null, null, null, null, null, null, null, null, null, null, true);
        assertThat(request.isDumpResultYaml()).isTrue();
    }

    @Test
    @DisplayName("All dump flags default to false when null")
    void allDumpFlagsDefaultFalse() {
        RecipeRequest request = new RecipeRequest("url", "text", null, "json", "raw", true, true, null, null, null, null, null, null);
        assertThat(request.isDumpRawHtml()).isFalse();
        assertThat(request.isDumpExtractedHtml()).isFalse();
        assertThat(request.isDumpCleanedHtml()).isFalse();
        assertThat(request.isDumpLLMResponse()).isFalse();
        assertThat(request.isDumpResultJson()).isFalse();
        assertThat(request.isDumpResultYaml()).isFalse();
    }

    @Test
    @DisplayName("All boolean fields can be set independently")
    void allBooleanFieldsIndependent() {
        RecipeRequest request = new RecipeRequest(
                "https://example.com", null, null, "json", "raw",
                true, true, true, false, true, false, null, null
        );

        assertThat(request.isSkipCache()).isTrue();
        assertThat(request.isVerbose()).isTrue();
        assertThat(request.isDumpRawHtml()).isTrue();
        assertThat(request.isDumpExtractedHtml()).isFalse();
        assertThat(request.isDumpCleanedHtml()).isTrue();
        assertThat(request.isDumpLLMResponse()).isFalse();
    }
}
