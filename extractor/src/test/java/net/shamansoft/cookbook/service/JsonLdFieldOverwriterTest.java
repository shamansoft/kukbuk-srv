package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLdFieldOverwriterTest {

    private static final String JSON_LD = "{\"@type\":\"Recipe\",\"name\":\"Carrot Cake\",\"recipeYield\":\"8 slices\","
            + "\"prepTime\":\"PT20M\",\"totalTime\":\"PT1H5M\",\"image\":[\"https://x.test/cake.jpg\"],"
            + "\"nutrition\":{\"calories\":\"320 kcal\",\"fatContent\":\"14 g\"}}";

    private JsonLdFieldOverwriter overwriter;

    @BeforeEach
    void setUp() {
        overwriter = new JsonLdFieldOverwriter(new ObjectMapper());
        ReflectionTestUtils.setField(overwriter, "enabled", true);
    }

    @Test
    void replacesFieldsTheJsonLdHasAndKeepsTheRest() {
        Recipe recipe = apply(modelRecipe(), JSON_LD, Strategy.STRUCTURED_DATA).recipe();
        RecipeMetadata m = recipe.metadata();

        assertThat(m.title()).isEqualTo("Carrot Cake");
        assertThat(m.servings()).isEqualTo(8);
        assertThat(m.prepTime()).isEqualTo("20m");
        assertThat(m.cookTime()).isEqualTo("40m");
        assertThat(m.totalTime()).isEqualTo("1h 5m");
        assertThat(m.coverImage()).isEqualTo(new CoverImage("https://x.test/cake.jpg", "model alt"));
        assertThat(m.author()).isEqualTo("Model Author");
        assertThat(recipe.nutrition()).isEqualTo(new Nutrition("1 slice", 320, 4.0, null, 14.0, null, null, null,
                "model note"));
        assertThat(recipe.ingredients()).isEqualTo(modelRecipe().ingredients());
        assertThat(recipe.instructions()).isEqualTo(modelRecipe().instructions());
    }

    @Test
    void readsJsonLdWhenIngredientCardFollows() {
        String cleaned = JSON_LD + "\n\n<RECIPE_CARD_INGREDIENTS>\n<ul><li>2 carrots</li></ul>\n</RECIPE_CARD_INGREDIENTS>";

        assertThat(apply(modelRecipe(), cleaned, Strategy.STRUCTURED_DATA).recipe().metadata().title())
                .isEqualTo("Carrot Cake");
    }

    @Test
    void keepsModelTitleWhenItOnlyTrimsThePageName() {
        Recipe recipe = apply(modelRecipe(), "{\"@type\":\"Recipe\",\"name\":\"Carrot Cake (Best) Recipe by Tasty\"}",
                Strategy.STRUCTURED_DATA).recipe();

        assertThat(recipe.metadata().title()).isEqualTo("carrot cake (best)");
    }

    @Test
    void takesPageNameWhenModelShortenedIt() {
        Recipe recipe = apply(modelRecipe(), "{\"@type\":\"Recipe\",\"name\":\"Carrot Cake (Best) with Walnuts\"}",
                Strategy.STRUCTURED_DATA).recipe();

        assertThat(recipe.metadata().title()).isEqualTo("Carrot Cake (Best) with Walnuts");
    }

    @Test
    void keepsModelCoverWhenJsonLdHasNoImage() {
        Recipe recipe = apply(modelRecipe(), "{\"@type\":\"Recipe\",\"name\":\"Carrot Cake\"}",
                Strategy.STRUCTURED_DATA).recipe();

        assertThat(recipe.metadata().coverImage()).isEqualTo(new CoverImage("https://x.test/model.jpg", "model alt"));
        assertThat(recipe.nutrition()).isEqualTo(modelRecipe().nutrition());
    }

    @Test
    void unchangedForOtherStrategies() {
        Transformer.Response response = Transformer.Response.recipe(modelRecipe());

        assertThat(overwriter.apply(response, cleaned(JSON_LD, Strategy.CONTENT_FILTER))).isSameAs(response);
    }

    @Test
    void unchangedForNonRecipeOrSeveralRecipes() {
        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.9);
        Transformer.Response two = Transformer.Response.recipes(List.of(modelRecipe(), modelRecipe()));

        assertThat(overwriter.apply(notRecipe, cleaned(JSON_LD, Strategy.STRUCTURED_DATA))).isSameAs(notRecipe);
        assertThat(overwriter.apply(two, cleaned(JSON_LD, Strategy.STRUCTURED_DATA))).isSameAs(two);
    }

    @Test
    void unchangedWhenDisabledOrJsonUnreadable() {
        Transformer.Response response = Transformer.Response.recipe(modelRecipe());

        assertThat(overwriter.apply(response, cleaned("not json", Strategy.STRUCTURED_DATA))).isSameAs(response);
        ReflectionTestUtils.setField(overwriter, "enabled", false);
        assertThat(overwriter.apply(response, cleaned(JSON_LD, Strategy.STRUCTURED_DATA))).isSameAs(response);
    }

    private Transformer.Response apply(Recipe recipe, String cleanedHtml, Strategy strategy) {
        return overwriter.apply(Transformer.Response.recipe(recipe), cleaned(cleanedHtml, strategy));
    }

    private static HtmlCleaner.Results cleaned(String html, Strategy strategy) {
        return new HtmlCleaner.Results(html, html.length(), html.length(), 0.0, strategy, "test");
    }

    private static Recipe modelRecipe() {
        return new Recipe(
                true, "1.0.0", "1.0.0",
                new RecipeMetadata("carrot cake (best)", "https://example.com", "Model Author", "en",
                        LocalDate.of(2026, 10, 17), null, null, 6, "15m", "40m", "55m", null,
                        new CoverImage("https://x.test/model.jpg", "model alt")),
                "Description",
                List.of(new Ingredient("carrots", "2", null, null, false, null, "main")),
                null,
                List.of(new Instruction(1, "Bake", null, null, null)),
                new Nutrition("1 slice", 300, 4.0, null, 12.0, null, null, null, "model note"),
                null, null);
    }
}
