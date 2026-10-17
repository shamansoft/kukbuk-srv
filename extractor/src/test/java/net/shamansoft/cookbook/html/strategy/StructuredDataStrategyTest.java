package net.shamansoft.cookbook.html.strategy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        strategy = new StructuredDataStrategy(config, objectMapper, new SimpleMeterRegistry());
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

    @Test
    void shouldAppendIngredientCardWithSectionHeaders() {
        String html = "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"Recipe\",\"name\":\"Pie\",\"recipeIngredient\":[\"500 g flour\",\"300 g onions\"],"
                + "\"recipeInstructions\":\"do\"}</script></head><body>"
                + "<div id=\"ingredients\"><h3 class=\"title\">Dough</h3><ul><li>500 g flour</li></ul>"
                + "<h3>Filling</h3><ul><li>300 g onions</li></ul></div></body></html>";

        Optional<String> out = strategy.clean(html);

        assertThat(out).isPresent();
        String json = out.get().substring(0, out.get().indexOf("\n\n<RECIPE_CARD_INGREDIENTS>"));
        assertThat(objectMapper.readTree(json).get("name").asText()).isEqualTo("Pie");
        assertThat(out.get()).endsWith("</RECIPE_CARD_INGREDIENTS>")
                .contains("<h3>Dough</h3>", "<h3>Filling</h3>");
    }

    @Test
    void shouldMatchIngredientCardByPropertyValueIngredients() {
        String html = "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"Recipe\",\"name\":\"Pie\",\"recipeInstructions\":\"do\",\"recipeIngredient\":["
                + "{\"@type\":\"PropertyValue\",\"name\":\"flour\",\"value\":\"500 g\"},"
                + "{\"@type\":\"PropertyValue\",\"name\":\"onions\",\"value\":\"300 g\"}]}"
                + "</script></head><body><ul class=\"ingredients\"><li>flour 500 g</li><li>onions 300 g</li></ul>"
                + "</body></html>";

        assertThat(strategy.clean(html)).hasValueSatisfying(out ->
                assertThat(out).contains("<RECIPE_CARD_INGREDIENTS>"));
    }

    @Test
    void shouldReturnJsonOnlyWhenIngredientCardDisabled() {
        config.getStructuredData().setIncludeIngredientCard(false);
        String html = "<html><head><script type=\"application/ld+json\">" + RECIPE + "</script></head>"
                + "<body><ul class=\"ingredients\"><li>a</li></ul></body></html>";

        assertThat(strategy.clean(html)).hasValueSatisfying(out ->
                assertThat(objectMapper.readTree(out).get("name").asText()).isEqualTo("Cake"));
    }
}
