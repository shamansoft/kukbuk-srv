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
        String expected = "example: value\n";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void removeYamlSign_shouldReturnContent_whenContentDoesNotStartWithYaml() {
        String content = "example: value\n";

        String result = cleanupService.removeYamlSign(content);

        assertThat(result).isEqualTo(content);
    }
}