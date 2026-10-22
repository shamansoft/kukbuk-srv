package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter.Row;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardSectionAssignerTest {

    private static final String URL = "https://example.com/r";
    private static final String JSON_LD = "{\"@type\":\"Recipe\",\"recipeIngredient\":"
            + "[\"3 cups cooked rice\",\"2 cups shredded chicken\",\"1/2 cup soy sauce\",\"salt and pepper\"]}";
    private static final String CARD = "<div><h3>Ingredients</h3><ul><li>3 cups cooked rice</li>"
            + "<li>2 cups shredded chicken</li></ul><h4>Teriyaki Sauce</h4><ul><li>1/2 cup soy sauce</li>"
            + "<li>salt and pepper</li></ul></div>";

    private SimpleMeterRegistry meterRegistry;
    private CardSectionAssigner assigner;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        assigner = new CardSectionAssigner(new ObjectMapper(), meterRegistry);
        ReflectionTestUtils.setField(assigner, "enabled", true);
    }

    @Test
    void takesEachIngredientsGroupFromTheCard() {
        Transformer.Response response = response(
                ingredient("cooked rice", null, "main"),
                ingredient("shredded chicken", null, "Teriyaki Sauce"),
                ingredient("soy sauce", null, "Teriyaki Sauce"),
                ingredient("salt", "to taste", "main"),
                ingredient("pepper", "to taste", "main"));

        Transformer.Response result = assigner.apply(response, cleaned(JSON_LD, CARD), URL);

        assertThat(result.recipe().ingredients()).extracting(Ingredient::component)
                .containsExactly("main", "main", "Teriyaki Sauce", "Teriyaki Sauce", "Teriyaki Sauce");
        assertThat(result.recipe().ingredients().get(3).notes()).isEqualTo("to taste");
        assertThat(meterRegistry.counter("recipe.jsonld.sections", "outcome", "applied").count()).isEqualTo(1);
    }

    @Test
    void keepsTheModelsAnswerWhenCardLinesDifferFromJsonLd() {
        String card = "<div><ul><li>3 cups cooked rice</li></ul><h4>Sauce</h4><ul><li>1/2 cup soy sauce</li></ul></div>";
        Transformer.Response response = response(ingredient("soy sauce", null, "main"));

        assertThat(assigner.apply(response, cleaned(JSON_LD, card), URL)).isSameAs(response);
        assertThat(meterRegistry.counter("recipe.jsonld.sections", "outcome", "lines_differ").count()).isEqualTo(1);
    }

    @Test
    void keepsTheModelsAnswerWhenCardHasNoGroups() {
        String card = "<ul><li>3 cups cooked rice</li><li>2 cups shredded chicken</li><li>1/2 cup soy sauce</li>"
                + "<li>salt and pepper</li></ul>";
        Transformer.Response response = response(ingredient("soy sauce", null, "Sauce"));

        assertThat(assigner.apply(response, cleaned(JSON_LD, card), URL)).isSameAs(response);
        assertThat(meterRegistry.counter("recipe.jsonld.sections", "outcome", "no_groups").count()).isEqualTo(1);
    }

    @Test
    void keepsTheModelsAnswerWithoutCard() {
        Transformer.Response response = response(ingredient("soy sauce", null, "Sauce"));

        assertThat(assigner.apply(response, results(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isSameAs(response);
        assertThat(meterRegistry.counter("recipe.jsonld.sections", "outcome", "no_card").count()).isEqualTo(1);
    }

    @Test
    void leavesOtherPathsAlone() {
        Transformer.Response response = response(ingredient("soy sauce", null, "main"));

        assertThat(assigner.apply(response, results(JSON_LD + CARD, Strategy.CONTENT_FILTER), URL)).isSameAs(response);
        assertThat(meterRegistry.find("recipe.jsonld.sections").counters()).isEmpty();
    }

    @Test
    void leavesMultiRecipeAnswersAlone() {
        Recipe one = recipe(List.of(ingredient("soy sauce", null, "main")));
        Transformer.Response response = Transformer.Response.recipes(List.of(one, one));

        assertThat(assigner.apply(response, cleaned(JSON_LD, CARD), URL)).isSameAs(response);
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(assigner, "enabled", false);
        Transformer.Response response = response(ingredient("soy sauce", null, "main"));

        assertThat(assigner.apply(response, cleaned(JSON_LD, CARD), URL)).isSameAs(response);
    }

    @Test
    void ingredientMatchingNoLineKeepsItsComponent() {
        List<Row> rows = List.of(new Row(null, "2 cups flour"), new Row("Glaze", "1 cup icing sugar"));

        List<Ingredient> result = CardSectionAssigner.assign(List.of(
                ingredient("flour", null, "main"), ingredient("water", null, "Dough")), rows);

        assertThat(result).extracting(Ingredient::component).containsExactly("main", "Dough");
    }

    @Test
    void tiesGoToTheNextLineInOrder() {
        // "sugar" is in both lines: a tie takes the first line at or after the previous match
        List<Row> rows = List.of(new Row("Cake", "100 g sugar"), new Row("Glaze", "50 g sugar"));

        List<Ingredient> result = CardSectionAssigner.assign(List.of(
                ingredient("sugar", null, "main"), ingredient("sugar", null, "main")), rows);

        assertThat(result).extracting(Ingredient::component).containsExactly("Cake", "Cake");

        // after a match on the second line, a tie no longer goes back to the first
        List<Ingredient> reordered = CardSectionAssigner.assign(List.of(
                ingredient("icing sugar", null, "main"), ingredient("sugar", null, "main")),
                List.of(new Row("Cake", "100 g sugar"), new Row("Glaze", "50 g icing sugar")));

        assertThat(reordered).extracting(Ingredient::component).containsExactly("Glaze", "Glaze");
    }

    @Test
    void itemAndAmountOutweighWordsInTheNotes() {
        // the first sugar's notes repeat the second line's unit word for word; the amount and order still win
        List<Row> rows = List.of(new Row("Massa", "4 ovos"), new Row("Massa", "2 xícaras (chá) de açúcar"),
                new Row("Cobertura", "1 colher de manteiga"), new Row("Cobertura", "1 xícara (chá) de açúcar"));

        List<Ingredient> result = CardSectionAssigner.assign(List.of(
                new Ingredient("ovos", "4", null, null, false, null, "main"),
                new Ingredient("açúcar", "2", "cup", "xícara (chá)", false, null, "main"),
                new Ingredient("manteiga", "1", "tablespoon", null, false, null, "main"),
                new Ingredient("açúcar", "1", "cup", "xícara (chá)", false, null, "main")), rows);

        assertThat(result).extracting(Ingredient::component).containsExactly("Massa", "Massa", "Cobertura", "Cobertura");
    }

    @Test
    void givesSectionsOnlyWhenTheCardLinesMatchTheJsonLdLinesInOrder() {
        List<String> lines = List.of("2 cups flour", "3 apples", "1 cup sugar", "2 개");
        List<Row> inOrder = List.of(new Row("Dough", "2 cups flour"), new Row("Filling", "3 apples"),
                new Row("Dough", "1 cup sugar"), new Row("Filling", "2 개"));
        List<Row> reordered = List.of(new Row("Dough", "2 cups flour"), new Row("Dough", "1 cup sugar"),
                new Row("Filling", "3 apples"), new Row("Filling", "2 개"));

        assertThat(CardSectionAssigner.givesSections(inOrder, lines)).isTrue();
        assertThat(CardSectionAssigner.givesSections(reordered, lines)).isFalse();
        assertThat(CardSectionAssigner.givesSections(inOrder.subList(0, 3), lines)).isFalse();
        assertThat(CardSectionAssigner.givesSections(
                inOrder.stream().map(r -> new Row(null, r.text())).toList(), lines)).isFalse();
    }

    private static HtmlCleaner.Results cleaned(String jsonLd, String card) {
        return results(jsonLd + "\n\n<RECIPE_CARD_INGREDIENTS>\n" + card + "\n</RECIPE_CARD_INGREDIENTS>",
                Strategy.STRUCTURED_DATA);
    }

    private static HtmlCleaner.Results results(String content, Strategy strategy) {
        return new HtmlCleaner.Results(content, content.length(), content.length(), 0.0, strategy, "");
    }

    private static Ingredient ingredient(String item, String notes, String component) {
        return new Ingredient(item, null, null, notes, false, null, component);
    }

    private static Transformer.Response response(Ingredient... ingredients) {
        return Transformer.Response.recipe(recipe(Arrays.asList(ingredients)));
    }

    private static Recipe recipe(List<Ingredient> ingredients) {
        return new Recipe(true, "1.0.0", "1.0.0",
                new RecipeMetadata("Casserole", URL, null, null, LocalDate.now(), null, null, 4, null, null, null,
                        null, null),
                null, ingredients, null, List.of(new Instruction(1, "Bake", null, null, null)), null, null, null);
    }
}
