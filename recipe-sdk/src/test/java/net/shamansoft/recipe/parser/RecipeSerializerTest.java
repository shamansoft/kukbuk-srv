package net.shamansoft.recipe.parser;

import net.shamansoft.recipe.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeSerializerTest {

    private RecipeSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new RecipeSerializer();
    }

    @Test
    void shouldSerializeSimpleRecipe() throws RecipeSerializeException {
        Recipe recipe = new Recipe(
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Test Recipe",
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
                List.of(new Ingredient("flour", "2", "cups", null, false, null, "main")),
                List.of(),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "",
                null
        );

        String yaml = serializer.serialize(recipe);

        assertThat(yaml).contains("schema_version: \"1.0.0\"");
        assertThat(yaml).contains("recipe_version: \"1.0.0\"");
        assertThat(yaml).contains("title: \"Test Recipe\"");
        assertThat(yaml).contains("servings: 4");
        assertThat(yaml).contains("item: \"flour\"");
        assertThat(yaml).contains("description: \"Mix ingredients\"");
    }

    @Test
    void shouldSerializeComplexRecipe() throws RecipeSerializeException {
        Recipe recipe = new Recipe(
                "1.0.0",
                "1.1.0",
                new RecipeMetadata(
                        "Complex Recipe",
                        "https://example.com/recipe",
                        "Chef John",
                        "en",
                        LocalDate.of(2025, 1, 15),
                        List.of("dessert", "chocolate"),
                        List.of("sweet", "easy"),
                        8,
                        "15m",
                        "30m",
                        "45m",
                        "easy",
                        new CoverImage("/images/recipe.jpg", "Delicious recipe")
                ),
                "A wonderful recipe",
                List.of(
                        new Ingredient("sugar", "1", "cup", "white sugar", false, null, "main"),
                        new Ingredient("butter", "100", "g", null, false,
                                List.of(
                                        new Substitution("margarine", null, null, null, "1:1"),
                                        new Substitution("oil", "80", "ml", null, "0.8:1")
                                ),
                                "main")
                ),
                List.of("mixing bowl", "oven"),
                List.of(
                        new Instruction(1, "Preheat oven", "5m", "180°C",
                                List.of(
                                        new ImageMedia("/images/step1.jpg", "Step 1"),
                                        new VideoMedia("/videos/step1.mp4", "/videos/thumb.jpg", "2:30")
                                ))
                ),
                new Nutrition("1 piece", 250, 5.5, 30.0, 12.0, 2.0, 15.0, 100.0, null),
                "Best served warm",
                new Storage("3 days", "1 month", "overnight")
        );

        String yaml = serializer.serialize(recipe);

        assertThat(yaml).contains("schema_version: \"1.0.0\"");
        assertThat(yaml).contains("recipe_version: \"1.1.0\"");
        assertThat(yaml).contains("author: \"Chef John\"");
        assertThat(yaml).contains("category:");
        assertThat(yaml).contains("- \"dessert\"");
        assertThat(yaml).contains("substitutions:");
        assertThat(yaml).contains("equipment:");
        assertThat(yaml).contains("type: \"image\"");
        assertThat(yaml).contains("type: \"video\"");
        assertThat(yaml).contains("nutrition:");
        assertThat(yaml).contains("calories: 250");
        assertThat(yaml).contains("storage:");
    }

    @Test
    void shouldSerializeToFile() throws Exception {
        Recipe recipe = new Recipe(
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "File Recipe",
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
                List.of(new Instruction(1, "Test step", null, null, null)),
                null,
                "",
                null
        );

        File tempFile = File.createTempFile("recipe-output", ".yaml");
        tempFile.deleteOnExit();

        serializer.serialize(recipe, tempFile);

        String content = java.nio.file.Files.readString(tempFile.toPath());
        assertThat(content).contains("title: \"File Recipe\"");
    }

    @Test
    void shouldSerializeToWriter() throws Exception {
        Recipe recipe = new Recipe(
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Writer Recipe",
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
                List.of(new Instruction(1, "Test step", null, null, null)),
                null,
                "",
                null
        );

        StringWriter writer = new StringWriter();
        serializer.serialize(recipe, writer);

        String yaml = writer.toString();
        assertThat(yaml).contains("title: \"Writer Recipe\"");
    }
}
