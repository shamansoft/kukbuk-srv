package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter.Row;
import net.shamansoft.cookbook.service.JsonLdRecipeAssembler.Assembly;
import net.shamansoft.cookbook.service.gemini.JsonLdEnrichment;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLdRecipeAssemblerTest {

    private static final String URL = "https://example.com/recipes/pie";
    private static final List<String> LINES = List.of("500 g flour", "salt and pepper", "200 g onions");

    @Test
    void buildsTheRecipeWhenEveryLineIsUsed() {
        Assembly assembly = assemble(fields(LINES), null, null,
                row(0, "flour", "500", "g"), row(1, "salt", null, null), row(1, "pepper", null, null),
                row(2, "onions", "200", "g"));

        assertThat(assembly.ok()).isTrue();
        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::item)
                .containsExactly("flour", "salt", "pepper", "onions");
        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::component).containsOnly("main");
    }

    @Test
    void reportsEveryLineWithoutARow() {
        Assembly assembly = assemble(fields(LINES), null, null, row(0, "flour", "500", "g"));

        assertThat(assembly.violations()).containsExactly(
                "Line 1 (\"salt and pepper\") has no row. Every line from 0 to 2 needs at least one row with its "
                        + "source_index.",
                "Line 2 (\"200 g onions\") has no row. Every line from 0 to 2 needs at least one row with its "
                        + "source_index.");
    }

    @Test
    void reportsRowsOutsideTheLines() {
        Assembly assembly = assemble(fields(List.of("500 g flour")), null, null,
                row(0, "flour", "500", "g"), row(3, "sugar", "1", "cup"));

        assertThat(assembly.violations()).containsExactly(
                "Row \"sugar\" has source_index 3, but the lines are numbered 0 to 0.");
        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::item).containsExactly("flour");
    }

    @Test
    void reportsARepeatedRowButAllowsTwoQuantitiesOfOneLine() {
        List<String> lines = List.of("100 g butter, plus extra for the tin");

        assertThat(assemble(fields(lines), null, null,
                row(0, "butter", "100", "g"), row(0, "butter", null, null)).ok()).isTrue();
        assertThat(assemble(fields(lines), null, null,
                row(0, "butter", "100", "g"), row(0, "Butter ", "100", "g")).violations())
                .containsExactly("Line 0 (\"100 g butter, plus extra for the tin\") has the row \"Butter\" twice. "
                        + "Return each ingredient of a line once.");
    }

    @Test
    void acceptsCardOnlyIngredientsAndRejectsInventedOnes() {
        String card = "<ul><li>500 g flour</li><li>salt and pepper</li><li>200 g onions</li>"
                + "<li>fresh parsley</li></ul>";
        JsonLdEnrichment.Row[] rows = {row(0, "flour", "500", "g"), row(1, "salt", null, null),
                row(2, "onions", "200", "g"), row(null, "parsley", null, null)};

        Assembly withCard = assemble(fields(LINES), null, card, rows);
        Assembly withoutCard = assemble(fields(LINES), null, null, rows);

        assertThat(withCard.ok()).isTrue();
        assertThat(withCard.cardOnlyRows()).isEqualTo(1);
        assertThat(withoutCard.violations()).singleElement().asString()
                .startsWith("Row \"parsley\" has no source_index and is not in <RECIPE_CARD_INGREDIENTS>.");
    }

    @Test
    void headerLinesNameTheSectionsAfterThem() {
        List<String> lines = List.of("200 g flour", "Glaze:", "50 g icing sugar");
        Assembly assembly = assemble(fields(lines), null, null,
                row(0, "flour", "200", "g"), header(1, "Glaze:"), row(2, "icing sugar", "50", "g"));

        assertThat(assembly.ok()).isTrue();
        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::component)
                .containsExactly("main", "Glaze");
    }

    @Test
    void cardGroupsGiveTheSectionsWhenTheyLineUp() {
        List<Row> card = List.of(new Row(null, "500 g flour"), new Row("Filling", "salt and pepper"),
                new Row("Filling", "200 g onions"));
        Assembly assembly = assemble(fields(LINES), card, "<ul></ul>",
                row(0, "flour", "500", "g", "Dough"), row(1, "salt", null, null, "main"),
                row(1, "pepper", null, null, "main"), row(2, "onions", "200", "g", "main"));

        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::component)
                .containsExactly("main", "Filling", "Filling", "Filling");
    }

    @Test
    void jsonLdFieldsWinAndTheModelFillsTheRest() {
        JsonLdRecipe fields = new JsonLdRecipe("Onion Pie Recipe by Site", 4, null, "45m", null, "/img/pie.jpg",
                new Nutrition(null, 300, null, null, null, null, null, null, null), LINES, "Ann", "de",
                List.of("Dinner"), List.of("German", "pie"), "A pie.", List.of("Bake."));
        JsonLdEnrichment answer = new JsonLdEnrichment("Onion Pie", "Savoury onion pie.", "en", "easy", 6, "20m",
                "1h", "2h", List.of(row(0, "flour", "500", "g"), row(1, "salt", null, null), row(2, "onions", "200", "g")),
                List.of(new Instruction(null, "Bake 45 minutes.", "45m", "180°C", null),
                        new Instruction(null, " ", null, null, null)),
                List.of("pie dish"), "Best warm.", null);

        Recipe recipe = JsonLdRecipeAssembler.assemble(fields, null, null, answer, URL).recipe();

        assertThat(recipe.metadata().title()).isEqualTo("Onion Pie");
        assertThat(recipe.metadata().author()).isEqualTo("Ann");
        assertThat(recipe.metadata().language()).isEqualTo("de");
        assertThat(recipe.metadata().servings()).isEqualTo(4);
        assertThat(recipe.metadata().prepTime()).isEqualTo("20m");
        assertThat(recipe.metadata().cookTime()).isEqualTo("45m");
        assertThat(recipe.metadata().totalTime()).isEqualTo("2h");
        assertThat(recipe.metadata().category()).containsExactly("Dinner");
        assertThat(recipe.metadata().tags()).containsExactly("German", "pie");
        assertThat(recipe.metadata().difficulty()).isEqualTo("easy");
        assertThat(recipe.metadata().coverImage().path()).isEqualTo("https://example.com/img/pie.jpg");
        assertThat(recipe.nutrition().calories()).isEqualTo(300);
        assertThat(recipe.description()).isEqualTo("Savoury onion pie.");
        assertThat(recipe.instructions()).containsExactly(new Instruction(1, "Bake 45 minutes.", "45m", "180°C", null));
        assertThat(recipe.equipment()).containsExactly("pie dish");
        assertThat(recipe.notes()).isEqualTo("Best warm.");
    }

    @Test
    void aLineWithAQuantityIsNotAHeader() {
        List<String> lines = List.of("2 eggs", "1 cup flour");
        Assembly assembly = assemble(fields(lines), null, null,
                header(0, "eggs"), row(1, "flour", "1", "cup"));

        assertThat(assembly.violations()).containsExactly("Line 0 (\"2 eggs\") has a quantity, so it is an "
                + "ingredient, not a section header. Give it an ingredient row.");
    }

    @Test
    void aCardOnlyRowNeedsAllItsWordsInTheCard() {
        String card = "<ul><li>500 g flour</li><li>salt and pepper</li><li>200 g onions</li>"
                + "<li>fresh lemon</li></ul>";
        Assembly assembly = assemble(fields(LINES), null, card, row(0, "flour", "500", "g"),
                row(1, "salt", null, null), row(2, "onions", "200", "g"), row(null, "fresh parsley", null, null));

        assertThat(assembly.violations()).singleElement().asString()
                .startsWith("Row \"fresh parsley\" has no source_index");
    }

    @Test
    void reportsARepeatedCardOnlyRow() {
        String card = "<ul><li>500 g flour</li><li>salt and pepper</li><li>200 g onions</li>"
                + "<li>fresh parsley</li></ul>";
        Assembly assembly = assemble(fields(LINES), null, card, row(0, "flour", "500", "g"),
                row(1, "salt", null, null), row(2, "onions", "200", "g"),
                row(null, "parsley", null, null), row(null, "Parsley", null, null));

        assertThat(assembly.violations()).containsExactly(
                "The row \"Parsley\" without source_index is there twice. Return each ingredient once.");
        assertThat(assembly.cardOnlyRows()).isEqualTo(1);
    }

    @Test
    void aCardOnlyRowTakesTheGroupOfTheCardLineHoldingIt() {
        List<Row> card = List.of(new Row(null, "500 g flour"), new Row("Filling", "salt and pepper"),
                new Row("Filling", "200 g onions"));
        Assembly assembly = assemble(fields(LINES), card, "<ul><li>salt and pepper</li></ul>",
                row(null, "pepper", null, null), row(0, "flour", "500", "g"), row(1, "salt", null, null),
                row(2, "onions", "200", "g"));

        assertThat(assembly.recipe().ingredients()).extracting(Ingredient::component)
                .containsExactly("Filling", "main", "Filling", "Filling");
    }

    @Test
    void anAnswerWithoutIngredientsIsAViolation() {
        Assembly assembly = assemble(fields(List.of("Dressing:")), null, null, header(0, "Dressing:"));

        assertThat(assembly.violations()).containsExactly("The answer has no ingredient rows.");
    }

    private static Assembly assemble(JsonLdRecipe fields, List<Row> cardRows, String card,
                                     JsonLdEnrichment.Row... rows) {
        JsonLdEnrichment answer = new JsonLdEnrichment("Pie", "A pie.", "en", null, null, null, null, null,
                Arrays.asList(rows), List.of(new Instruction(null, "Bake.", null, null, null)), null, null, null);
        return JsonLdRecipeAssembler.assemble(fields, cardRows, card, answer, URL);
    }

    private static JsonLdRecipe fields(List<String> lines) {
        return new JsonLdRecipe("Pie", null, null, null, null, null, null, lines, null, null, List.of(), List.of(),
                null, List.of("Bake."));
    }

    private static JsonLdEnrichment.Row row(Integer index, String item, String amount, String unit) {
        return row(index, item, amount, unit, null);
    }

    private static JsonLdEnrichment.Row row(Integer index, String item, String amount, String unit, String component) {
        return new JsonLdEnrichment.Row(index, null, item, amount, unit, null, null, null, component);
    }

    private static JsonLdEnrichment.Row header(int index, String text) {
        return new JsonLdEnrichment.Row(index, true, text, null, null, null, null, null, null);
    }
}
