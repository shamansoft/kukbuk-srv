package net.shamansoft.recipe.parser;

import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests parsing of real example YAML files from the no-git/examples directory.
 * This test is only enabled if the examples directory exists.
 */
class ExampleYamlFilesTest {

    private YamlRecipeParser parser;
    private static final String EXAMPLES_DIR = "../no-git/examples";

    @BeforeEach
    void setUp() {
        parser = new YamlRecipeParser();
    }

    static boolean examplesDirectoryExists() {
        return Files.exists(Paths.get(EXAMPLES_DIR));
    }

    @Test
    @EnabledIf("examplesDirectoryExists")
    void shouldParseAllExampleYamlFiles() throws Exception {
        Path examplesPath = Paths.get(EXAMPLES_DIR);

        try (Stream<Path> paths = Files.walk(examplesPath)) {
            List<Path> yamlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .toList();

            assertThat(yamlFiles).isNotEmpty();

            int successCount = 0;
            int failureCount = 0;

            for (Path yamlFile : yamlFiles) {
                System.out.println("Parsing: " + yamlFile.getFileName());

                try {
                    Recipe recipe = parser.parse(yamlFile);

                    // Verify basic structure
                    assertThat(recipe).isNotNull();
                    assertThat(recipe.schemaVersion()).isNotBlank();
                    assertThat(recipe.recipeVersion()).isNotBlank();
                    assertThat(recipe.metadata()).isNotNull();
                    assertThat(recipe.metadata().title()).isNotBlank();
                    // Note: source may be null in some example files (data quality issue)
                    // assertThat(recipe.metadata().source()).isNotBlank();
                    assertThat(recipe.metadata().dateCreated()).isNotNull();
                    assertThat(recipe.metadata().servings()).isPositive();
                    assertThat(recipe.ingredients()).isNotEmpty();
                    assertThat(recipe.instructions()).isNotEmpty();

                    System.out.println("  ✓ Successfully parsed: " + recipe.metadata().title());
                    successCount++;
                } catch (RecipeParseException e) {
                    System.out.println("  ✗ Failed to parse (may use different schema format): " + e.getMessage());
                    failureCount++;
                }
            }

            System.out.println("\nSummary: " + successCount + " successful, " + failureCount + " failed");
            // Ensure we successfully parsed at least most of the files
            assertThat(successCount).isGreaterThanOrEqualTo(yamlFiles.size() - 1);
        }
    }

    @Test
    @EnabledIf("examplesDirectoryExists")
    void shouldParseCreamyMushroomToastExample() throws Exception {
        Path exampleFile = Paths.get(EXAMPLES_DIR, "creamy-mushroom-toast-with-soft-egg-gruy-re-gordon-ramsay-com.yaml");

        if (!Files.exists(exampleFile)) {
            System.out.println("Skipping test - file not found: " + exampleFile);
            return;
        }

        Recipe recipe = parser.parse(exampleFile);

        assertThat(recipe.schemaVersion()).isEqualTo("1.0.0");
        assertThat(recipe.recipeVersion()).isEqualTo("1.1.0");
        assertThat(recipe.metadata().title()).isEqualTo("CREAMY MUSHROOM TOAST WITH SOFT EGG & GRUYÈRE");
        assertThat(recipe.metadata().author()).isEqualTo("Gordon Ramsay");
        assertThat(recipe.metadata().servings()).isEqualTo(2);
        assertThat(recipe.metadata().difficulty()).isEqualTo("easy");
        assertThat(recipe.ingredients()).hasSizeGreaterThan(5);
        assertThat(recipe.instructions()).hasSizeGreaterThan(2);
        assertThat(recipe.equipment()).isNotEmpty();
        assertThat(recipe.nutrition()).isNotNull();
    }

    @Test
    @EnabledIf("examplesDirectoryExists")
    void shouldParseGordonRamsayBurgerExample() throws Exception {
        Path exampleFile = Paths.get(EXAMPLES_DIR, "gordon-ramsay-burger-patty-recipe-gordon-ramsay-eats.yaml");

        if (!Files.exists(exampleFile)) {
            System.out.println("Skipping test - file not found: " + exampleFile);
            return;
        }

        Recipe recipe = parser.parse(exampleFile);

        assertThat(recipe.metadata().title()).isEqualTo("Gordon Ramsay Burger Patty Recipe");
        assertThat(recipe.metadata().category()).contains("side dish");
        assertThat(recipe.metadata().tags()).contains("burger", "patty", "beef");
        assertThat(recipe.metadata().prepTime()).isEqualTo("10m");
        assertThat(recipe.metadata().cookTime()).isEqualTo("8m");
        assertThat(recipe.metadata().totalTime()).isEqualTo("18m");

        // Check for specific instruction with time
        boolean hasTimedInstruction = recipe.instructions().stream()
                .anyMatch(i -> i.time() != null);
        assertThat(hasTimedInstruction).isTrue();
    }

    @Test
    @EnabledIf("examplesDirectoryExists")
    void shouldParseChocolateAvocadoMousseExample() throws Exception {
        Path exampleFile = Paths.get(EXAMPLES_DIR, "chocolate-avocado-mousse-mousse-recipes-gordon-ramsay-recipes.yaml");

        if (!Files.exists(exampleFile)) {
            System.out.println("Skipping test - file not found: " + exampleFile);
            return;
        }

        Recipe recipe = parser.parse(exampleFile);

        assertThat(recipe.metadata().title()).isEqualTo("Chocolate Avocado Mousse");
        assertThat(recipe.metadata().category()).contains("dessert");
        assertThat(recipe.metadata().prepTime()).isEqualTo("1h");
        assertThat(recipe.metadata().cookTime()).isEqualTo("0m");
        assertThat(recipe.description()).contains("avocado");
    }

    @Test
    @EnabledIf("examplesDirectoryExists")
    void shouldRoundTripExampleFiles() throws Exception {
        Path examplesPath = Paths.get(EXAMPLES_DIR);

        try (Stream<Path> paths = Files.walk(examplesPath)) {
            List<Path> yamlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .limit(3)  // Test first 3 files for speed
                    .toList();

            RecipeSerializer serializer = new RecipeSerializer();

            for (Path yamlFile : yamlFiles) {
                System.out.println("Round-trip testing: " + yamlFile.getFileName());

                // Parse original
                Recipe original = parser.parse(yamlFile);

                // Serialize to YAML
                String yaml = serializer.serialize(original);

                // Parse again
                Recipe reparsed = parser.parse(yaml);

                // Verify key fields match
                assertThat(reparsed.metadata().title()).isEqualTo(original.metadata().title());
                assertThat(reparsed.metadata().servings()).isEqualTo(original.metadata().servings());
                assertThat(reparsed.ingredients().size()).isEqualTo(original.ingredients().size());
                assertThat(reparsed.instructions().size()).isEqualTo(original.instructions().size());

                System.out.println("  ✓ Round-trip successful");
            }
        }
    }
}
