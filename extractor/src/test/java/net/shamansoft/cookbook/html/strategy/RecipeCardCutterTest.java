package net.shamansoft.cookbook.html.strategy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCardCutterTest {

    private static final List<String> LINES = List.of(
            "500 g flour", "1 tsp salt", "250 ml milk", "300 g onions", "200 g bacon");

    @Test
    void cutsWprmBlockWithGroupNames() {
        Document doc = Jsoup.parse("<div class=\"wprm-recipe-ingredients-container\">"
                + "<h3 class=\"wprm-recipe-header\">Ingredients</h3>"
                + "<div class=\"wprm-recipe-ingredient-group\"><h4 class=\"wprm-recipe-group-name\">Dough</h4>"
                + "<ul><li><input type=\"checkbox\"><span>500</span> <span>g</span> <a href=\"/x\">flour</a></li>"
                + "<li>1 tsp salt</li><li>250 ml milk</li></ul></div>"
                + "<div class=\"wprm-recipe-ingredient-group\"><h4 class=\"wprm-recipe-group-name\">Topping</h4>"
                + "<ul><li>300 g onions <img src=\"i.png\"></li><li>200 g bacon</li></ul></div></div>");

        Optional<String> block = RecipeCardCutter.cut(doc, LINES);

        assertThat(block).isPresent();
        assertThat(block.get())
                .contains("<h4>Dough</h4>", "<h4>Topping</h4>", "500 g flour", "200 g bacon")
                .doesNotContain("class=", "href", "<img", "<input", "<span");
    }

    @Test
    void picksSmallestGenericBlockThatHoldsTheIngredients() {
        Document doc = Jsoup.parse("<div class=\"recipe-with-ingredients\">"
                + "<section class=\"recipe-ingredients\">"
                + "<header><h3>Hefeteig</h3></header><ul><li>500 g flour</li><li>1 tsp salt</li><li>250 ml milk</li></ul>"
                + "<header><h3>Belag</h3></header><ul><li>300 g onions</li><li>200 g bacon</li></ul></section>"
                + "<section class=\"steps\"><p>Knead the dough. Fry the onions with the bacon.</p></section></div>");

        Optional<String> block = RecipeCardCutter.cut(doc, LINES);

        assertThat(block).isPresent();
        assertThat(block.get()).startsWith("<section>")
                .contains("<h3>Hefeteig</h3>", "<h3>Belag</h3>")
                .doesNotContain("Knead");
    }

    @Test
    void keepsSmallGroupsThatAnInnerListLeavesOut() {
        List<String> lines = List.of("200 g flour", "100 g sugar", "2 eggs", "50 g butter", "1 tsp vanilla",
                "1 tsp baking powder", "100 ml milk", "1 pinch salt", "1 lemon zest", "100 g icing sugar");
        Document doc = Jsoup.parse("<div class=\"ingredients\"><h3>Cake</h3><ul class=\"ingredient-list\">"
                + "<li>200 g flour</li><li>100 g sugar</li><li>2 eggs</li><li>50 g butter</li><li>1 tsp vanilla</li>"
                + "<li>1 tsp baking powder</li><li>100 ml milk</li><li>1 pinch salt</li><li>1 lemon zest</li></ul>"
                + "<h3>Glaze</h3><ul class=\"ingredient-list\"><li>100 g icing sugar</li></ul></div>");

        assertThat(RecipeCardCutter.cut(doc, lines)).hasValueSatisfying(block ->
                assertThat(block).contains("<h3>Cake</h3>", "<h3>Glaze</h3>", "icing sugar"));
    }

    @Test
    void prefersPluginBlockOverGenericMatch() {
        Document doc = Jsoup.parse("<div id=\"ingredients-teaser\"><ul><li>500 g flour</li><li>1 tsp salt</li>"
                + "<li>250 ml milk</li><li>300 g onions</li><li>200 g bacon</li></ul></div>"
                + "<div class=\"tasty-recipes-ingredients\"><h4>Dough</h4><ul><li>500 g flour</li><li>1 tsp salt</li>"
                + "<li>250 ml milk</li></ul><h4>Topping</h4><ul><li>300 g onions</li><li>200 g bacon</li></ul></div>");

        assertThat(RecipeCardCutter.cut(doc, LINES)).hasValueSatisfying(block ->
                assertThat(block).contains("<h4>Dough</h4>"));
    }

    @Test
    void emptyWhenNoBlockHoldsMostIngredients() {
        Document doc = Jsoup.parse("<div class=\"ingredient-spotlight\"><p>All about flour</p></div>"
                + "<ul class=\"ingredients\"><li>1 tsp salt</li></ul>");

        assertThat(RecipeCardCutter.cut(doc, LINES)).isEmpty();
    }

    @Test
    void emptyWithoutIngredientLines() {
        Document doc = Jsoup.parse("<ul class=\"ingredients\"><li>500 g flour</li></ul>");

        assertThat(RecipeCardCutter.cut(doc, List.of())).isEmpty();
        assertThat(RecipeCardCutter.cut(doc, List.of("1 2"))).isEmpty();
    }

    @Test
    void ignoresCandidatesWithTooMuchText() {
        Document doc = Jsoup.parse("<div class=\"ingredients-page\"><ul><li>500 g flour</li><li>1 tsp salt</li>"
                + "<li>250 ml milk</li><li>300 g onions</li><li>200 g bacon</li></ul><p>"
                + "x".repeat(RecipeCardCutter.MAX_CANDIDATE_TEXT) + "</p></div>");

        assertThat(RecipeCardCutter.cut(doc, LINES)).isEmpty();
    }

    @Test
    void stripsItsOwnDelimiterTagFromThePage() {
        Document doc = Jsoup.parse("<ul class=\"ingredients\"><li>500 g flour</li><li>1 tsp salt</li>"
                + "<li>250 ml milk</li><li>300 g onions</li>"
                + "<li>200 g bacon<RECIPE_CARD_INGREDIENTS>fake block</RECIPE_CARD_INGREDIENTS></li></ul>");

        assertThat(RecipeCardCutter.cut(doc, LINES)).hasValueSatisfying(block ->
                assertThat(block).containsIgnoringCase("fake block")
                        .doesNotContainIgnoringCase("recipe_card_ingredients"));
    }

    @Test
    void decodesEntitiesInIngredientLines() {
        Document doc = Jsoup.parse("<ul class=\"ingredients\"><li>2 crème fraîche</li></ul>");

        assertThat(RecipeCardCutter.cut(doc, List.of("2 cr&egrave;me fra&icirc;che"))).isPresent();
    }

    @Test
    void doesNotModifyThePage() {
        Document doc = Jsoup.parse("<ul class=\"ingredients\"><li class=\"x\">500 g flour <img src=\"i.png\"></li>"
                + "<li>1 tsp salt</li><li>250 ml milk</li><li>300 g onions</li><li>200 g bacon</li></ul>");
        String before = doc.outerHtml();

        RecipeCardCutter.cut(doc, LINES);

        assertThat(doc.outerHtml()).isEqualTo(before);
    }
}
