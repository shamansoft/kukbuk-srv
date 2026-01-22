package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeValidationServiceTest {

    private RecipeValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new RecipeValidationService();
    }

    @Test
    void validate_withValidRecipe_shouldReturnSuccess() {
        // Given
        Recipe validRecipe = createValidRecipe();

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(validRecipe);

        // Then
        assertTrue(result.isValid(), "Valid recipe should pass validation");
        assertNotNull(result.getRecipe(), "Recipe should not be null");
        assertNull(result.getErrorMessage(), "Error message should be null for valid recipe");
        assertEquals(validRecipe, result.getRecipe(), "Recipe should be returned unchanged");
    }

    @Test
    void validate_withNullRecipe_shouldReturnFailure() {
        // When
        RecipeValidationService.ValidationResult result = validationService.validate(null);

        // Then
        assertFalse(result.isValid(), "Null recipe should fail validation");
        assertNull(result.getRecipe(), "Recipe should be null");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
        assertTrue(result.getErrorMessage().contains("null"), "Error message should mention null");
    }

    @Test
    void validate_withMissingTitle_shouldReturnFailure() {
        // Given - Recipe with null title
        RecipeMetadata metadata = new RecipeMetadata(
                null,  // title is null
                "https://example.com",  // source
                "Test Author",
                "en",
                LocalDate.parse("2024-01-15"),
                List.of("dessert"),
                List.of("test"),
                4,
                "15m",
                "12m",
                "27m",
                "easy",
                null
        );

        Recipe recipe = new Recipe(
                true,  // isRecipe
                "1.0.0",  // schemaVersion
                "1.0.0",  // recipeVersion
                metadata,
                "Test description",
                List.of(createValidIngredient()),
                List.of(),
                List.of(createValidInstruction()),
                null,
                null,
                null  // storage
        );

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(recipe);

        // Then
        assertFalse(result.isValid(), "Recipe with null title should fail validation");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
        assertTrue(result.getErrorMessage().contains("title") || result.getErrorMessage().contains("metadata"),
                "Error message should mention the missing field");
    }

    @Test
    void validate_withEmptyIngredients_shouldReturnFailure() {
        // Given - Recipe with empty ingredients list
        Recipe recipe = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                createValidMetadata(),
                "Test description",
                List.of(),  // empty ingredients
                List.of(),
                List.of(createValidInstruction()),
                null,
                null,
                null
        );

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(recipe);

        // Then
        assertFalse(result.isValid(), "Recipe with empty ingredients should fail validation");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
    }

    @Test
    void validate_withEmptyInstructions_shouldReturnFailure() {
        // Given - Recipe with empty instructions list
        Recipe recipe = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                createValidMetadata(),
                "Test description",
                List.of(createValidIngredient()),
                List.of(),
                List.of(),  // empty instructions
                null,
                null,
                null
        );

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(recipe);

        // Then
        assertFalse(result.isValid(), "Recipe with empty instructions should fail validation");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
    }

    @Test
    void toYaml_withValidRecipe_shouldReturnYamlString() throws RecipeSerializeException {
        // Given
        Recipe recipe = createValidRecipe();

        // When
        String yaml = validationService.toYaml(recipe);

        // Then
        assertNotNull(yaml, "YAML should not be null");
        assertFalse(yaml.isEmpty(), "YAML should not be empty");
        assertTrue(yaml.contains("schema_version"), "YAML should contain schema_version");
        assertTrue(yaml.contains("Chocolate Chip Cookies"), "YAML should contain recipe title");
    }

    @Test
    void toYaml_withNullRecipe_shouldThrowException() {
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> {
            validationService.toYaml(null);
        }, "Should throw IllegalArgumentException for null recipe");
    }

    @Test
    void validate_withValidRecipe_thenConvertToYaml_shouldBeReusable() throws RecipeSerializeException {
        // Given
        Recipe recipe = createValidRecipe();

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(recipe);
        String yaml = validationService.toYaml(result.getRecipe());

        // Then
        assertTrue(result.isValid(), "Recipe should be valid");
        assertNotNull(yaml, "YAML should not be null");
        assertTrue(yaml.contains("Chocolate Chip Cookies"), "YAML should contain recipe data");
    }

    @Test
    void validate_withNonRecipe_shouldStillValidate() {
        // Given - Recipe marked as not a recipe
        Recipe nonRecipe = new Recipe(
                false,  // is_recipe = false
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        "Not a Recipe",
                        null,  // source
                        null,  // author
                        "en",  // language
                        LocalDate.now(),  // dateCreated
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(nonRecipe);

        // Then
        // Note: Validation only checks constraints, not is_recipe flag
        // This will likely fail due to missing required fields
        // The is_recipe flag is checked at the transformer level
        assertFalse(result.isValid(), "Non-recipe with missing fields should fail validation");
    }

    // Helper methods to create test data

    private Recipe createValidRecipe() {
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                createValidMetadata(),
                "Classic chocolate chip cookies",
                List.of(createValidIngredient()),
                List.of(),  // equipment
                List.of(createValidInstruction()),
                null,  // nutrition
                "Store in airtight container",  // notes
                null  // storage
        );
    }

    private RecipeMetadata createValidMetadata() {
        return new RecipeMetadata(
                "Chocolate Chip Cookies",
                "https://example.com/recipe",  // source
                "John Doe",  // author
                "en",
                LocalDate.parse("2024-01-15"),
                List.of("dessert"),
                List.of("cookies", "chocolate"),
                24,
                "15m",
                "12m",
                "27m",
                "easy",
                null
        );
    }

    private Ingredient createValidIngredient() {
        return new Ingredient(
                "flour",
                "2",
                "cups",
                null,
                false,
                null,
                "main"
        );
    }

    private Instruction createValidInstruction() {
        return new Instruction(
                1,
                "Mix dry ingredients and bake at 350°F",
                "12m",
                "350°F",
                null
        );
    }
}