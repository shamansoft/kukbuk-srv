package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeAdjustmentServiceTest {

    private RecipeAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new RecipeAdjustmentService();
    }

    // =========================================================================
    // Step renumbering
    // =========================================================================

    @Nested
    class StepRenumbering {

        @Test
        void allNullSteps_renumberedFromOne() {
            Recipe recipe = recipeWithInstructions(
                    instruction(null, "Step A"),
                    instruction(null, "Step B"),
                    instruction(null, "Step C")
            );
            Recipe adjusted = service.adjust(recipe);
            assertSteps(adjusted, 1, 2, 3);
        }

        @Test
        void nullBetweenNumberedSteps_renumberedSequentially() {
            Recipe recipe = recipeWithInstructions(
                    instruction(1, "Step 1"),
                    instruction(2, "Step 2"),
                    instruction(7, "Step 7"),
                    instruction(null, "Step null"),
                    instruction(8, "Step 8")
            );
            Recipe adjusted = service.adjust(recipe);
            assertSteps(adjusted, 1, 2, 3, 4, 5);
        }

        @Test
        void gapsInSteps_renumbered() {
            Recipe recipe = recipeWithInstructions(
                    instruction(1, "Step 1"),
                    instruction(2, "Step 2"),
                    instruction(5, "Step 5"),
                    instruction(8, "Step 8")
            );
            Recipe adjusted = service.adjust(recipe);
            assertSteps(adjusted, 1, 2, 3, 4);
        }

        @Test
        void alreadyCorrect_noChange() {
            Recipe recipe = recipeWithInstructions(
                    instruction(1, "Step 1"),
                    instruction(2, "Step 2"),
                    instruction(3, "Step 3")
            );
            Recipe adjusted = service.adjust(recipe);
            assertSteps(adjusted, 1, 2, 3);
        }

        @Test
        void singleInstruction_stepIsOne() {
            Recipe recipe = recipeWithInstructions(instruction(null, "Mix everything"));
            Recipe adjusted = service.adjust(recipe);
            assertSteps(adjusted, 1);
        }

        @Test
        void outOfOrderSteps_renumberedInListOrder() {
            Recipe recipe = recipeWithInstructions(
                    instruction(3, "Step written third"),
                    instruction(1, "Step written first"),
                    instruction(2, "Step written second")
            );
            Recipe adjusted = service.adjust(recipe);
            // List order preserved; steps assigned 1,2,3 by position
            assertSteps(adjusted, 1, 2, 3);
            assertEquals("Step written third", adjusted.instructions().get(0).description());
            assertEquals("Step written first", adjusted.instructions().get(1).description());
        }

        @Test
        void emptyInstructions_returnedAsIs() {
            Recipe recipe = recipeWith(List.of(), List.of(ingredient("flour")));
            Recipe adjusted = service.adjust(recipe);
            assertTrue(adjusted.instructions().isEmpty());
        }
    }

    // =========================================================================
    // Time normalization
    // =========================================================================

    @Nested
    class TimeNormalization {

        @ParameterizedTest
        @CsvSource({
                "15 minutes,   15m",
                "1 hour,       1h",
                "1 hour 30 minutes, 1h 30m",
                "2 hrs,        2h",
                "30 mins,      30m",
                "2 days,       2d",
                "1 day 3 hours, 1d 3h"
        })
        void naturalLanguageTime_normalized(String input, String expected) {
            assertEquals(expected.trim(), service.normalizeTime(input));
        }

        @Test
        void alreadyValidTime_unchanged() {
            assertEquals("2h 30m", service.normalizeTime("2h 30m"));
            assertEquals("15m", service.normalizeTime("15m"));
            assertEquals("1h", service.normalizeTime("1h"));
        }

        @Test
        void nullTime_returnsNull() {
            assertNull(service.normalizeTime(null));
        }

        @Test
        void blankTime_returnsSameBlank() {
            assertEquals("  ", service.normalizeTime("  "));
        }

        @Test
        void unparseable_returnsOriginalTrimmed() {
            assertEquals("some random text", service.normalizeTime("some random text"));
        }

        @Test
        void instructionTimesAreNormalized() {
            Recipe recipe = recipeWithInstructions(
                    new Instruction(1, "Boil water", "15 minutes", null, null),
                    new Instruction(2, "Simmer", "1 hour", null, null)
            );
            Recipe adjusted = service.adjust(recipe);
            assertEquals("15m", adjusted.instructions().get(0).time());
            assertEquals("1h", adjusted.instructions().get(1).time());
        }

        @Test
        void metadataTimesAreNormalized() {
            Recipe recipe = recipeWithMetadataTimes("15 minutes", "1 hour", "1 hour 15 minutes");
            Recipe adjusted = service.adjust(recipe);
            assertEquals("15m", adjusted.metadata().prepTime());
            assertEquals("1h", adjusted.metadata().cookTime());
            assertEquals("1h 15m", adjusted.metadata().totalTime());
        }
    }

    // =========================================================================
    // Amount normalization
    // =========================================================================

    @Nested
    class AmountNormalization {

        @ParameterizedTest
        @CsvSource({
                "½,     0.5",
                "¼,     0.25",
                "¾,     0.75",
                "⅓,     0.333",
                "⅔,     0.667",
                "⅛,     0.125",
                "⅜,     0.375",
                "⅝,     0.625",
                "⅞,     0.875"
        })
        void unicodeFractions_converted(String input, String expected) {
            assertEquals(expected.trim(), service.normalizeAmount(input));
        }

        @Test
        void mixedUnicode_onePlusHalf() {
            assertEquals("1.5", service.normalizeAmount("1 ½"));
        }

        @Test
        void mixedUnicode_twoAndThreeQuarters() {
            assertEquals("2.75", service.normalizeAmount("2 ¾"));
        }

        @Test
        void asciiFraction_half() {
            assertEquals("0.5", service.normalizeAmount("1/2"));
        }

        @Test
        void asciiFraction_mixedNumber() {
            assertEquals("1.5", service.normalizeAmount("1 1/2"));
        }

        @Test
        void asciiFraction_twoAndThreeQuarters() {
            assertEquals("2.75", service.normalizeAmount("2 3/4"));
        }

        @Test
        void plainNumber_unchanged() {
            assertEquals("2", service.normalizeAmount("2"));
        }

        @Test
        void nullAmount_returnsNull() {
            assertNull(service.normalizeAmount(null));
        }

        @Test
        void wordAmount_unchanged() {
            assertEquals("handful", service.normalizeAmount("handful"));
        }
    }

    // =========================================================================
    // Unit normalization
    // =========================================================================

    @Nested
    class UnitNormalization {

        @Test
        void paddedUnit_trimmedAndLowercased() {
            assertEquals("tablespoon", service.normalizeUnit(" Tablespoon "));
        }

        @Test
        void uppercaseUnit_lowercased() {
            assertEquals("g", service.normalizeUnit("G"));
        }

        @Test
        void nullUnit_returnsNull() {
            assertNull(service.normalizeUnit(null));
        }

        @Test
        void alreadyLowercase_unchanged() {
            assertEquals("cup", service.normalizeUnit("cup"));
        }
    }

    // =========================================================================
    // Component normalization
    // =========================================================================

    @Nested
    class ComponentNormalization {

        @Test
        void emptyComponent_becomesMain() {
            assertEquals("main", service.normalizeComponent(""));
        }

        @Test
        void blankComponent_becomesMain() {
            assertEquals("main", service.normalizeComponent("   "));
        }

        @Test
        void nullComponent_becomesMain() {
            assertEquals("main", service.normalizeComponent(null));
        }

        @Test
        void namedComponent_unchanged() {
            assertEquals("sauce", service.normalizeComponent("sauce"));
        }

        @Test
        void ingredientWithBlankComponent_adjustedToMain() {
            // Ingredient constructor handles null→"main" but NOT blank→"main"
            Ingredient ingredient = new Ingredient("sugar", "1", "cup", null, null, null, "");
            Recipe recipe = recipeWith(
                    List.of(new Instruction(1, "Mix", null, null, null)),
                    List.of(ingredient)
            );
            Recipe adjusted = service.adjust(recipe);
            assertEquals("main", adjusted.ingredients().get(0).component());
        }
    }

    // =========================================================================
    // Difficulty normalization
    // =========================================================================

    @Nested
    class DifficultyNormalization {

        @ParameterizedTest
        @CsvSource({
                "Easy,  easy",
                "MEDIUM, medium",
                "Hard,  hard",
                "HARD,  hard",
                "easy,  easy"
        })
        void knownDifficulty_normalized(String input, String expected) {
            assertEquals(expected.trim(), service.normalizeDifficulty(input));
        }

        @Test
        void unknownDifficulty_returnsNull() {
            assertNull(service.normalizeDifficulty("beginner"));
            assertNull(service.normalizeDifficulty("expert"));
        }

        @Test
        void nullDifficulty_returnsNull() {
            assertNull(service.normalizeDifficulty(null));
        }

        @Test
        void unknownDifficultyInRecipe_becomesNullThenDefaultsToMedium() {
            // When difficulty is unknown → service returns null → RecipeMetadata constructor defaults to "medium"
            Recipe recipe = recipeWithDifficulty("beginner");
            Recipe adjusted = service.adjust(recipe);
            assertEquals("medium", adjusted.metadata().difficulty());
        }
    }

    // =========================================================================
    // Tags / category normalization
    // =========================================================================

    @Nested
    class TagsNormalization {

        @Test
        void tags_trimmedAndLowercased() {
            List<String> result = service.normalizeTags(List.of("  Dessert ", "VEGAN", "chocolate"));
            assertEquals(List.of("dessert", "vegan", "chocolate"), result);
        }

        @Test
        void blankTagsFiltered() {
            List<String> result = service.normalizeTags(List.of("  Dessert ", "", "  "));
            assertEquals(List.of("dessert"), result);
        }

        @Test
        void nullList_returnsNull() {
            assertNull(service.normalizeTags(null));
        }

        @Test
        void recipeTagsAdjusted() {
            Recipe recipe = recipeWithTags(List.of("  Dessert ", "VEGAN", ""), List.of("  Italian "));
            Recipe adjusted = service.adjust(recipe);
            assertEquals(List.of("dessert", "vegan"), adjusted.metadata().tags());
            assertEquals(List.of("italian"), adjusted.metadata().category());
        }
    }

    // =========================================================================
    // Null handling
    // =========================================================================

    @Test
    void adjustNullRecipe_returnsNull() {
        assertNull(service.adjust(null));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Instruction instruction(Integer step, String description) {
        return new Instruction(step, description, null, null, null);
    }

    private static Ingredient ingredient(String item) {
        return new Ingredient(item, null, null, null, null, null, null);
    }

    private static Recipe recipeWithInstructions(Instruction... instructions) {
        return recipeWith(List.of(instructions), List.of(ingredient("flour")));
    }

    private static Recipe recipeWith(List<Instruction> instructions, List<Ingredient> ingredients) {
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe", "https://example.com", null, null, null,
                null, null, null, null, null, null, null, null);
        return new Recipe(true, "1.0.0", "1.0.0", metadata,
                null, ingredients, null, instructions, null, null, null);
    }

    private static Recipe recipeWithMetadataTimes(String prepTime, String cookTime, String totalTime) {
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe", "https://example.com", null, null, null,
                null, null, null, prepTime, cookTime, totalTime, null, null);
        return new Recipe(true, "1.0.0", "1.0.0", metadata,
                null, List.of(ingredient("flour")), null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }

    private static Recipe recipeWithDifficulty(String difficulty) {
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe", "https://example.com", null, null, null,
                null, null, null, null, null, null, difficulty, null);
        return new Recipe(true, "1.0.0", "1.0.0", metadata,
                null, List.of(ingredient("flour")), null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }

    private static Recipe recipeWithTags(List<String> tags, List<String> category) {
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe", "https://example.com", null, null, null,
                category, tags, null, null, null, null, null, null);
        return new Recipe(true, "1.0.0", "1.0.0", metadata,
                null, List.of(ingredient("flour")), null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }

    private static void assertSteps(Recipe recipe, int... expectedSteps) {
        List<Instruction> instructions = recipe.instructions();
        assertEquals(expectedSteps.length, instructions.size());
        for (int i = 0; i < expectedSteps.length; i++) {
            assertEquals(expectedSteps[i], instructions.get(i).step(),
                    "Step at position " + i + " mismatch");
        }
    }
}
