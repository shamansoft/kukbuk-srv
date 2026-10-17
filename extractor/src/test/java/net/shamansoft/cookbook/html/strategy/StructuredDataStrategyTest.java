package net.shamansoft.cookbook.html.strategy;

import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

    private static final String RECIPE =
            "{\"@type\":\"Recipe\",\"name\":\"Cake\",\"recipeIngredient\":[\"a\"],\"recipeInstructions\":\"do\"}";

    private static String page(String jsonLd) {
        return "<html><head><script type=\"application/ld+json\">" + jsonLd
                + "</script></head><body></body></html>";
    }

    @Test
    void shouldFindRecipeFirstInArrayRoot() {
        Optional<String> out = strategy.clean(page("[" + RECIPE + ",{\"@type\":\"WebSite\",\"name\":\"Site\"}]"));

        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake").doesNotContain("Site");
    }

    @Test
    void shouldFindRecipeAfterOtherNodesInArrayRoot() {
        Optional<String> out = strategy.clean(page("[{\"@type\":\"WebSite\",\"name\":\"Site\"},"
                + "{\"@type\":\"BreadcrumbList\",\"itemListElement\":[]}," + RECIPE + "]"));

        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake").doesNotContain("Site");
    }

    @Test
    void shouldFindRecipeInGraphInsideArrayElement() {
        Optional<String> out = strategy.clean(page("[{\"@type\":\"Organization\",\"name\":\"Org\"},"
                + "{\"@context\":\"https://schema.org\",\"@graph\":[{\"@type\":\"WebPage\"}," + RECIPE + "]}]"));

        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake").doesNotContain("Org");
    }

    @Test
    void shouldFindRecipeUnderMainEntityObject() {
        Optional<String> out = strategy.clean(page(
                "{\"@type\":\"WebPage\",\"name\":\"Page\",\"mainEntity\":" + RECIPE + "}"));

        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake").doesNotContain("Page");
    }

    @Test
    void shouldFindRecipeUnderMainEntityArray() {
        Optional<String> out = strategy.clean(page(
                "{\"@type\":\"WebPage\",\"name\":\"Page\",\"mainEntity\":[{\"@type\":\"Person\",\"name\":\"Ann\"},"
                        + RECIPE + "]}"));

        assertThat(out).isPresent();
        assertThat(out.get()).contains("Cake").doesNotContain("Page").doesNotContain("Ann");
    }

    @Test
    void shouldParseRawControlCharactersInStrings() {
        Optional<String> out = strategy.clean(page("{\"@type\":\"Recipe\",\"name\":\"Cake\","
                + "\"description\":\"line one\r\nline two\",\"recipeIngredient\":[\"a\"],\"recipeInstructions\":\"do\"}"));

        assertThat(out).isPresent();
        // re-serialised output is strict JSON with the control characters escaped
        assertThat(out.get()).doesNotContain("\r").doesNotContain("\n");
        assertThat(objectMapper.readTree(out.get()).get("description").asText()).isEqualTo("line one\r\nline two");
    }

    @Test
    void shouldReturnEmptyForArrayWithoutRecipe() {
        Optional<String> out = strategy.clean(page(
                "[{\"@type\":\"WebSite\",\"name\":\"Site\"},{\"@type\":\"BreadcrumbList\",\"itemListElement\":[]}]"));

        assertThat(out).isEmpty();
    }
}
