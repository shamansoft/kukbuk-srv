package net.shamansoft.cookbook.html.strategy;

import tools.jackson.databind.ObjectMapper;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredDataStrategyTest {

    private HtmlCleanupConfig config;
    private ObjectMapper objectMapper;
    private StructuredDataStrategy strategy;

    @BeforeEach
    void setUp() {
        config = new HtmlCleanupConfig();
        config.setEnabled(true);
        config.getStructuredData().setEnabled(true);
        config.getStructuredData().setMinCompleteness(50);
        config.getFallback().setMinSafeSize(10);

        objectMapper = new ObjectMapper();
        strategy = new StructuredDataStrategy(config, objectMapper);
    }

    @Test
    void shouldExtractRecipeJsonLd() {
        String html = "<html><head>"
                + "<script type=\"application/ld+json\">"
                + "{\"@type\":\"Recipe\",\"name\":\"Cake\",\"recipeIngredient\":[\"a\"],\"recipeInstructions\":\"do\"}"
                + "</script></head><body></body></html>";

        Optional<String> out = strategy.clean(html);
        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake");
    }
}
