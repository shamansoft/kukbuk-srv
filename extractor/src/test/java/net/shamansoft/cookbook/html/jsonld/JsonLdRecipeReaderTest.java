package net.shamansoft.cookbook.html.jsonld;

import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLdRecipeReaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonLdRecipe read(String json) {
        return JsonLdRecipeReader.read(mapper.readTree(json));
    }

    @Test
    void decodesEntitiesAndMarkupInTitle() {
        assertThat(read("{\"name\":\" Bolo cl&aacute;ssico <b>de</b> cenoura \"}").title())
                .isEqualTo("Bolo clássico de cenoura");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "4                          | 4",
            "'\"4 servings\"'           | 4",
            "'[\"4\",\"4 servings\"]'   | 4",
            "'[\"about\",\"6 people\"]' | 6",
            "'\"4-6\"'                  | 5",
            "'\"4 to 6 portions\"'      | 5",
            "'\"Serves 2 – 3\"'         | 3",
            "'\"8 porções\"'            | 8",
            "'\"Makes 12 cookies\"'     | 12",
            "'\"1 Roast, 6 serving(s)\"' | 6",
            "'\"1 tray, 4-6 portions\"' | 5",
    })
    void readsServingsFromYield(String yield, int servings) {
        assertThat(read("{\"recipeYield\":" + yield + "}").servings()).isEqualTo(servings);
    }

    @ParameterizedTest
    @CsvSource({"'\"a big pot\"'", "0", "'\"0\"'", "'{}'", "null"})
    void noServingsWithoutAPositiveNumber(String yield) {
        assertThat(read("{\"recipeYield\":" + yield + "}").servings()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "PT1H30M, 1h 30m",
            "PT90M, 1h 30m",
            "PT45M, 45m",
            "PT2H, 2h",
            "P1DT2H, 1d 2h",
            "P0DT0H20M, 20m",
            "pt15m, 15m",
    })
    void formatsIsoDurations(String iso, String expected) {
        JsonLdRecipe recipe = read("{\"prepTime\":\"" + iso + "\",\"cookTime\":\"" + iso + "\",\"totalTime\":\"" + iso + "\"}");

        assertThat(recipe.prepTime()).isEqualTo(expected);
        assertThat(recipe.cookTime()).isEqualTo(expected);
        assertThat(recipe.totalTime()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PT0M", "PT0S", "30 mins", "''"})
    void noTimeForZeroOrNonIsoDuration(String value) {
        assertThat(read("{\"prepTime\":\"" + value + "\"}").prepTime()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'\"https://x.test/a.jpg\"'",
            "'[\"https://x.test/a.jpg\",\"https://x.test/b.jpg\"]'",
            "'{\"@type\":\"ImageObject\",\"url\":\"https://x.test/a.jpg\",\"width\":1200}'",
            "'[{\"@type\":\"ImageObject\",\"url\":\"https://x.test/a.jpg\"},\"https://x.test/b.jpg\"]'",
            "'{\"@type\":\"ImageObject\",\"contentUrl\":\"https://x.test/a.jpg\"}'",
            "'[{\"@type\":\"ImageObject\"},\"https://x.test/a.jpg\"]'",
    })
    void readsFirstImageUrl(String image) {
        assertThat(read("{\"image\":" + image + "}").image()).isEqualTo("https://x.test/a.jpg");
    }

    @Test
    void parsesNutritionNumbersFromStrings() {
        var nutrition = read("{\"nutrition\":{\"@type\":\"NutritionInformation\",\"servingSize\":\"1 slice\","
                + "\"calories\":\"240.6 kcal\",\"proteinContent\":\"12,5 g\",\"carbohydrateContent\":30,"
                + "\"fatContent\":\"9 grams\",\"fiberContent\":\"2 g\",\"sugarContent\":\"5g\","
                + "\"sodiumContent\":\"300 mg\"}}").nutrition();

        assertThat(nutrition.servingSize()).isEqualTo("1 slice");
        assertThat(nutrition.calories()).isEqualTo(241);
        assertThat(nutrition.protein()).isEqualTo(12.5);
        assertThat(nutrition.carbohydrates()).isEqualTo(30.0);
        assertThat(nutrition.fat()).isEqualTo(9.0);
        assertThat(nutrition.fiber()).isEqualTo(2.0);
        assertThat(nutrition.sugar()).isEqualTo(5.0);
        assertThat(nutrition.sodium()).isEqualTo(300.0);
        assertThat(nutrition.notes()).isNull();
    }

    @Test
    void convertsSodiumInGramsToMilligrams() {
        assertThat(read("{\"nutrition\":{\"sodiumContent\":\"0.3 g\"}}").nutrition().sodium()).isEqualTo(300.0);
    }

    @Test
    void noNutritionWithoutNumbers() {
        assertThat(read("{\"nutrition\":{\"@type\":\"NutritionInformation\",\"servingSize\":\"1 bowl\"}}").nutrition())
                .isNull();
    }

    @Test
    void readsIngredientStringsAndPropertyValues() {
        assertThat(read("{\"recipeIngredient\":[\"2 eggs\",{\"@type\":\"PropertyValue\",\"name\":\"farina\","
                + "\"value\":\"200 g\"}]}").ingredients())
                .containsExactly("2 eggs", "farina 200 g");
    }

    @Test
    void allNullWhenNodeHasNoFields() {
        JsonLdRecipe recipe = read("{\"@type\":\"Recipe\"}");

        assertThat(recipe.title()).isNull();
        assertThat(recipe.servings()).isNull();
        assertThat(recipe.prepTime()).isNull();
        assertThat(recipe.image()).isNull();
        assertThat(recipe.nutrition()).isNull();
        assertThat(recipe.ingredients()).isEmpty();
    }
}
