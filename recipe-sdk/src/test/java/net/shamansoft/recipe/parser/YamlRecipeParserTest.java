package net.shamansoft.recipe.parser;

import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlRecipeParserTest {

    private YamlRecipeParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlRecipeParser();
    }

    @Test
    void shouldParseSimpleYamlString() throws RecipeParseException {
        String yaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                  source: "https://example.com/recipe"
                  date_created: "2025-01-15"
                  servings: 4
                ingredients:
                  - item: "flour"
                    amount: 2
                    unit: "cups"
                instructions:
                  - step: 1
                    description: "Mix ingredients"
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.schemaVersion()).isEqualTo("1.0.0");
        assertThat(recipe.metadata().title()).isEqualTo("Test Recipe");
        assertThat(recipe.metadata().servings()).isEqualTo(4);
        assertThat(recipe.ingredients()).hasSize(1);
        assertThat(recipe.ingredients().get(0).item()).isEqualTo("flour");
        assertThat(recipe.ingredients().get(0).amount()).isEqualTo("2");
        assertThat(recipe.instructions()).hasSize(1);
    }

    @Test
    void shouldParseYamlWithOptionalFields() throws RecipeParseException {
        String yaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Complex Recipe"
                  source: "https://example.com/recipe"
                  author: "Chef John"
                  language: "en"
                  date_created: "2025-01-15"
                  category: ["dessert", "chocolate"]
                  tags: ["sweet", "easy"]
                  servings: 8
                  prep_time: "15m"
                  cook_time: "30m"
                  total_time: "45m"
                  difficulty: "easy"
                  cover_image:
                    path: "/images/recipe.jpg"
                    alt: "Delicious recipe"
                description: "A wonderful recipe"
                ingredients:
                  - item: "sugar"
                    amount: 1
                    unit: "cup"
                    notes: "white sugar"
                    optional: false
                    component: "main"
                equipment:
                  - "mixing bowl"
                  - "oven"
                instructions:
                  - step: 1
                    description: "Preheat oven"
                    time: "5m"
                    temperature: "180°C"
                nutrition:
                  serving_size: "1 piece"
                  calories: 250
                  protein: 5.5
                  carbohydrates: 30.0
                  fat: 12.0
                notes: "Best served warm"
                storage:
                  refrigerator: "3 days"
                  freezer: "1 month"
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().author()).isEqualTo("Chef John");
        assertThat(recipe.metadata().category()).containsExactly("dessert", "chocolate");
        assertThat(recipe.metadata().tags()).containsExactly("sweet", "easy");
        assertThat(recipe.metadata().difficulty()).isEqualTo("easy");
        assertThat(recipe.metadata().coverImage()).isNotNull();
        assertThat(recipe.metadata().coverImage().path()).isEqualTo("/images/recipe.jpg");
        assertThat(recipe.description()).isEqualTo("A wonderful recipe");
        assertThat(recipe.equipment()).containsExactly("mixing bowl", "oven");
        assertThat(recipe.nutrition()).isNotNull();
        assertThat(recipe.nutrition().calories()).isEqualTo(250);
        assertThat(recipe.storage()).isNotNull();
        assertThat(recipe.storage().refrigerator()).isEqualTo("3 days");
    }

    @Test
    void shouldParseYamlWithSubstitutions() throws RecipeParseException {
        String yaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Recipe with Substitutions"
                  source: "https://example.com/recipe"
                  date_created: "2025-01-15"
                  servings: 4
                ingredients:
                  - item: "butter"
                    amount: 100
                    unit: "g"
                    substitutions:
                      - item: "margarine"
                        ratio: "1:1"
                      - item: "oil"
                        amount: 80
                        unit: "ml"
                        ratio: "0.8:1"
                instructions:
                  - step: 1
                    description: "Use butter or substitute"
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe.ingredients()).hasSize(1);
        assertThat(recipe.ingredients().get(0).substitutions()).hasSize(2);
        assertThat(recipe.ingredients().get(0).substitutions().get(0).item()).isEqualTo("margarine");
        assertThat(recipe.ingredients().get(0).substitutions().get(0).ratio()).isEqualTo("1:1");
        assertThat(recipe.ingredients().get(0).substitutions().get(1).amount()).isEqualTo("80");
    }

    @Test
    void shouldParseYamlWithMedia() throws RecipeParseException {
        String yaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Recipe with Media"
                  source: "https://example.com/recipe"
                  date_created: "2025-01-15"
                  servings: 2
                ingredients:
                  - item: "ingredient"
                instructions:
                  - step: 1
                    description: "Follow along"
                    media:
                      - type: "image"
                        path: "/images/step1.jpg"
                        alt: "Step 1"
                      - type: "video"
                        path: "/videos/step1.mp4"
                        thumbnail: "/videos/thumb.jpg"
                        duration: "2:30"
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe.instructions()).hasSize(1);
        assertThat(recipe.instructions().get(0).media()).hasSize(2);
        assertThat(recipe.instructions().get(0).media().get(0).type()).isEqualTo("image");
        assertThat(recipe.instructions().get(0).media().get(1).type()).isEqualTo("video");
    }

    @Test
    void shouldThrowExceptionForInvalidYaml() {
        String invalidYaml = "this is not valid yaml: [unclosed";

        assertThatThrownBy(() -> parser.parse(invalidYaml))
                .isInstanceOf(RecipeParseException.class)
                .hasMessageContaining("Failed to parse YAML content");
    }

    @Test
    void shouldParseYamlEvenWithMissingOptionalMetadata() throws RecipeParseException {
        // Jackson allows missing optional fields, but requires title, source, date_created, servings
        String yaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Simple Recipe"
                  source: "https://example.com/recipe"
                  date_created: "2025-01-15"
                  servings: 4
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        Recipe recipe = parser.parse(yaml);

        // Verify it parses successfully with defaults
        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().title()).isEqualTo("Simple Recipe");
        assertThat(recipe.metadata().language()).isEqualTo("en"); // default value
        assertThat(recipe.metadata().difficulty()).isEqualTo("medium"); // default value
    }

    @Test
    void shouldParseFromFile() throws Exception {
        // Create a temporary file
        File tempFile = File.createTempFile("recipe", ".yaml");
        tempFile.deleteOnExit();

        java.nio.file.Files.writeString(tempFile.toPath(), """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "File Recipe"
                  source: "https://example.com/recipe"
                  date_created: "2025-01-15"
                  servings: 2
                ingredients:
                  - item: "test"
                instructions:
                  - step: 1
                    description: "Test"
                """);

        Recipe recipe = parser.parse(tempFile);
        assertThat(recipe.metadata().title()).isEqualTo("File Recipe");

        Recipe recipeFromPath = parser.parse(tempFile.toPath());
        assertThat(recipeFromPath.metadata().title()).isEqualTo("File Recipe");
    }
}
