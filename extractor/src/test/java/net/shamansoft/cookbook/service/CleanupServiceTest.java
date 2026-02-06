package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupServiceTest {

    private final CleanupService cleanupService = new CleanupService();

    @Test
    void removeYamlSign_shouldRemoveYamlSign_whenContentStartsWithYaml() {
        String content = "```yaml\n" +
                "example: value\n" +
                "```";
        String expected = "example: value";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void removeYamlSign_shouldReturnContent_whenContentDoesNotStartWithYaml() {
        String content = "example: value\n";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value");
    }

    @Test
    void removeYamlSign_shouldReturnContent_whenContentContainsYamlSignButDoesNotStartWithIt() {
        String content = "example: ```yaml\nvalue\n```";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void removeYamlSign_shouldHandleContentWithoutClosingFence() {
        String content = "```yaml\nexample: value\nmore: data";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value\nmore: data");
    }

    @Test
    void removeYamlSign_shouldHandleContentWithWhitespace() {
        String content = "  ```yaml\n  example: value\n  ```  ";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value");
    }

    @Test
    void removeYamlSign_shouldHandleContentWithOnlyBackticks() {
        String content = "```yaml";

        String result = cleanupService.removeYamlSign(content);

        // Should return the content without the yaml prefix
        assertThat(result).isEqualTo("");
    }

    @Test
    void removeYamlSign_shouldHandleContentWithMultipleFences() {
        String content = "```yaml\nexample: value\ncode: |\n  ```\n  nested\n  ```\n```";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value\ncode: |\n  ```\n  nested\n  ```");
    }

    @Test
    void removeYamlSign_shouldHandleFencesWithoutLanguageIdentifier() {
        String content = "```\nexample: value\nmore: data\n```";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value\nmore: data");
    }

    @Test
    void removeYamlSign_shouldHandleFencesWithoutLanguageIdentifierAndWhitespace() {
        String content = "  ```\n  example: value\n  ```  ";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo("example: value");
    }

    @Test
    void removeYamlSign_shouldReturnNull_whenContentIsNull() {
        String result = cleanupService.removeYamlSign(null);
        assertThat(result).isNull();
    }

    @Test
    void removeYamlSign_shouldReturnEmptyString_whenContentIsEmpty() {
        String result = cleanupService.removeYamlSign("");
        assertThat(result).isEmpty();
    }

    @Test
    void removeYamlSign_shouldReturnOriginal_whenContentIsOnlyThreeBackticks() {
        String result = cleanupService.removeYamlSign("```");
        assertThat(result).isEqualTo("```");
    }

    @Test
    void removeYamlSign_shouldHandleNonYamlLanguageTag() {
        String result = cleanupService.removeYamlSign("```json");
        assertThat(result).isEqualTo("json");
    }

    @Test
    void removeYamlSign_shouldHandleWindowsLineEndings() {
        String content = "```yaml\r\nexample: value\r\n```";
        String result = cleanupService.removeYamlSign(content);
        assertThat(result).isEqualTo("example: value");
    }
}