package net.shamansoft.cookbook.html.strategy;

import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SectionBasedStrategyTest {

    private HtmlCleanupConfig config;
    private SectionBasedStrategy strategy;

    @BeforeEach
    void setUp() {
        config = new HtmlCleanupConfig();
        // HtmlCleanupConfig uses default values; ensure nested objects are configured
        config.getSectionBased().setEnabled(true);
        config.getSectionBased().setMinConfidence(30);
        config.getSectionBased().setKeywords(java.util.List.of("ingredient", "recipe"));
        config.getContentFilter().setMinOutputSize(10);

        strategy = new SectionBasedStrategy(config);
    }

    @Test
    void shouldExtractSectionWithHighScore() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<article>");
        sb.append("<h2>Recipe</h2><h3>Ingredients</h3>");
        sb.append("<ul><li>one</li><li>two</li></ul>");
        // add some text to increase length
        for (int i = 0; i < 200; i++) sb.append(" word");
        sb.append("</article>");
        sb.append("</body></html>");

        Optional<String> out = strategy.clean(sb.toString());
        assertThat(out).isPresent();
        assertThat(out.get()).contains("Recipe");
    }

    @Test
    void shouldReturnEmptyWhenScoreTooLow() {
        String html = "<html><body><div><p>Short text without keywords</p></div></body></html>";
        Optional<String> out = strategy.clean(html);
        assertThat(out).isEmpty();
    }
}
