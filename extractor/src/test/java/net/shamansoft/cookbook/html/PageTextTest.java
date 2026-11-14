package net.shamansoft.cookbook.html;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageTextTest {

    @Test
    void blocksAndListItemsGetTheirOwnLines() {
        String html = """
                <div><h2>Ingredients</h2>
                <ul><li>200 g  flour</li><li><p>2 eggs</p></li></ul>
                <p>Mix and <b>bake</b> for 20 minutes.<br>Serve warm.</p></div>
                """;

        assertThat(PageText.of(html)).isEqualTo("""
                Ingredients
                - 200 g flour
                - 2 eggs
                Mix and bake for 20 minutes.
                Serve warm.""");
    }

    @Test
    void jsonPassesThroughAsText() {
        String json = "{\"@type\":\"Recipe\",\"recipeIngredient\":[\"1 cup rice\"]}";

        assertThat(PageText.of(json)).isEqualTo(json);
    }

    @Test
    void jsonLdWithIngredientCard() {
        String cleaned = "{\"@type\":\"Recipe\"}\n\n<recipe-card>\n<ul><li>1 cup rice</li></ul>\n</recipe-card>";

        assertThat(PageText.of(cleaned)).isEqualTo("{\"@type\":\"Recipe\"}\n- 1 cup rice");
    }

    @Test
    void emptyListItemDoesNotDoubleTheBullet() {
        assertThat(PageText.of("<ul><li></li><li>salt</li></ul>")).isEqualTo("- salt");
    }

    @Test
    void scriptsAndBlankInputGiveNoText() {
        assertThat(PageText.of("<script>var a = 1;</script>")).isEmpty();
        assertThat(PageText.of("  ")).isEmpty();
        assertThat(PageText.of(null)).isEmpty();
    }
}
