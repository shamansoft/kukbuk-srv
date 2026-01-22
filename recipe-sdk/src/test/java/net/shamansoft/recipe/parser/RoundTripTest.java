package net.shamansoft.recipe.parser;

import net.shamansoft.recipe.model.ImageMedia;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import net.shamansoft.recipe.model.Storage;
import net.shamansoft.recipe.model.Substitution;
import net.shamansoft.recipe.model.VideoMedia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests round-trip conversion: Model -> YAML -> Model
 */
class RoundTripTest {

    @Test
    void shouldRoundTripSimpleRecipe() throws Exception {
        YamlRecipeParser parser = new YamlRecipeParser();
        RecipeSerializer serializer = new RecipeSerializer();

        Recipe original = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Round Trip Recipe",
                        "https://example.com/recipe",
                        "Test Author",
                        "en",
                        LocalDate.of(2025, 1, 15),
                        List.of("breakfast"),
                        List.of("quick", "easy"),
                        2,
                        "5m",
                        "10m",
                        "15m",
                        "easy",
                        null
                ),
                "A simple test recipe",
                List.of(
                        new Ingredient("flour", "2", "cups", "all-purpose", false, null, "main"),
                        new Ingredient("sugar", "1", "cup", null, false, null, "main")
                ),
                List.of("bowl", "spoon"),
                List.of(
                        new Instruction(1, "Mix ingredients", "2m", null, null),
                        new Instruction(2, "Bake", "10m", "180°C", null)
                ),
                new Nutrition("1 serving", 300, 8.0, 45.0, 10.0, 3.0, 20.0, 150.0, null),
                "Store in cool place",
                new Storage("2 days", "1 week", "overnight")
        );

        // Serialize to YAML
        String yaml = serializer.serialize(original);

        // Parse back to Recipe
        Recipe parsed = parser.parse(yaml);

        // Verify all fields match
        assertThat(parsed.schemaVersion()).isEqualTo(original.schemaVersion());
        assertThat(parsed.recipeVersion()).isEqualTo(original.recipeVersion());
        assertThat(parsed.metadata().title()).isEqualTo(original.metadata().title());
        assertThat(parsed.metadata().author()).isEqualTo(original.metadata().author());
        assertThat(parsed.metadata().dateCreated()).isEqualTo(original.metadata().dateCreated());
        assertThat(parsed.metadata().servings()).isEqualTo(original.metadata().servings());
        assertThat(parsed.metadata().category()).isEqualTo(original.metadata().category());
        assertThat(parsed.metadata().tags()).isEqualTo(original.metadata().tags());
        assertThat(parsed.description()).isEqualTo(original.description());
        assertThat(parsed.ingredients()).hasSize(original.ingredients().size());
        assertThat(parsed.equipment()).isEqualTo(original.equipment());
        assertThat(parsed.instructions()).hasSize(original.instructions().size());
        assertThat(parsed.nutrition()).isNotNull();
        assertThat(parsed.nutrition().calories()).isEqualTo(original.nutrition().calories());
        assertThat(parsed.notes()).isEqualTo(original.notes());
        assertThat(parsed.storage()).isNotNull();
        assertThat(parsed.storage().refrigerator()).isEqualTo(original.storage().refrigerator());
    }

    @Test
    void shouldRoundTripRecipeWithSubstitutions() throws Exception {
        YamlRecipeParser parser = new YamlRecipeParser();
        RecipeSerializer serializer = new RecipeSerializer();

        Recipe original = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Recipe with Subs",
                        "https://example.com/recipe",
                        null,
                        "en",
                        LocalDate.of(2025, 1, 15),
                        List.of(),
                        List.of(),
                        4,
                        null,
                        null,
                        null,
                        "medium",
                        null
                ),
                "",
                List.of(
                        new Ingredient("butter", "100", "g", null, false,
                                List.of(
                                        new Substitution("margarine", null, null, "works well", "1:1"),
                                        new Substitution("oil", "80", "ml", "reduce slightly", "0.8:1")
                                ),
                                "main")
                ),
                List.of(),
                List.of(new Instruction(1, "Use butter", null, null, null)),
                null,
                "",
                null
        );

        String yaml = serializer.serialize(original);
        Recipe parsed = parser.parse(yaml);

        assertThat(parsed.ingredients().get(0).substitutions()).hasSize(2);
        assertThat(parsed.ingredients().get(0).substitutions().get(0).item()).isEqualTo("margarine");
        assertThat(parsed.ingredients().get(0).substitutions().get(0).ratio()).isEqualTo("1:1");
        assertThat(parsed.ingredients().get(0).substitutions().get(1).amount()).isEqualTo("80");
    }

    @Test
    void shouldRoundTripRecipeWithMedia() throws Exception {
        YamlRecipeParser parser = new YamlRecipeParser();
        RecipeSerializer serializer = new RecipeSerializer();

        Recipe original = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Recipe with Media",
                        "https://example.com/recipe",
                        null,
                        "en",
                        LocalDate.of(2025, 1, 15),
                        List.of(),
                        List.of(),
                        2,
                        null,
                        null,
                        null,
                        "medium",
                        null
                ),
                "",
                List.of(new Ingredient("test", "1", "unit", null, false, null, "main")),
                List.of(),
                List.of(
                        new Instruction(1, "Follow video", null, null,
                                List.of(
                                        new ImageMedia("/img/step.jpg", "Step image"),
                                        new VideoMedia("/vid/step.mp4", "/vid/thumb.jpg", "1:45")
                                ))
                ),
                null,
                "",
                null
        );

        String yaml = serializer.serialize(original);
        Recipe parsed = parser.parse(yaml);

        assertThat(parsed.instructions().get(0).media()).hasSize(2);
        assertThat(parsed.instructions().get(0).media().get(0)).isInstanceOf(ImageMedia.class);
        assertThat(parsed.instructions().get(0).media().get(1)).isInstanceOf(VideoMedia.class);

        ImageMedia img = (ImageMedia) parsed.instructions().get(0).media().get(0);
        assertThat(img.path()).isEqualTo("/img/step.jpg");

        VideoMedia vid = (VideoMedia) parsed.instructions().get(0).media().get(1);
        assertThat(vid.duration()).isEqualTo("1:45");
    }
}
