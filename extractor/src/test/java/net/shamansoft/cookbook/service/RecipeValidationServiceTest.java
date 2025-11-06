package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeValidationServiceTest {

    private RecipeValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new RecipeValidationService();
    }

    @Test
    void validate_withValidYaml_shouldReturnSuccess() {
        // Given
        String validYaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Chocolate Chip Cookies"
                  source: "https://example.com/recipe"
                  author: "John Doe"
                  language: "en"
                  date_created: "2024-01-15"
                  category:
                    - dessert
                  tags:
                    - cookies
                    - chocolate
                  servings: 24
                  prep_time: "15m"
                  cook_time: "12m"
                  total_time: "27m"
                  difficulty: "easy"
                description: "Classic chocolate chip cookies"
                ingredients:
                  - item: "flour"
                    amount: "2"
                    unit: "cups"
                    optional: false
                    component: "main"
                  - item: "chocolate chips"
                    amount: "1"
                    unit: "cup"
                    optional: false
                    component: "main"
                equipment:
                  - "mixing bowl"
                  - "baking sheet"
                instructions:
                  - step: 1
                    description: "Mix dry ingredients"
                  - step: 2
                    description: "Bake at 350°F"
                    time: "12m"
                    temperature: "350°F"
                notes: "Store in airtight container"
                """;

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(validYaml);

        // Then
        assertTrue(result.isValid(), "Valid YAML should pass validation");
        assertNotNull(result.getNormalizedYaml(), "Normalized YAML should not be null");
        assertNull(result.getErrorMessage(), "Error message should be null for valid YAML");
    }

    @Test
    void validate_withInvalidYaml_shouldReturnFailure() {
        // Given - YAML with invalid structure
        String invalidYaml = """
                this is not valid yaml
                - it has no structure
                metadata: incomplete
                """;

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(invalidYaml);

        // Then
        assertFalse(result.isValid(), "Invalid YAML should fail validation");
        assertNull(result.getNormalizedYaml(), "Normalized YAML should be null for invalid YAML");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
        assertTrue(result.getErrorMessage().contains("YAML parsing failed"),
                "Error message should indicate parsing failure");
    }

    @Test
    void validate_withMissingRequiredFields_shouldReturnFailure() {
        // Given - YAML missing required fields
        String incompleteYaml = """
                schema_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                """;

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(incompleteYaml);

        // Then
        assertFalse(result.isValid(), "YAML with missing required fields should fail validation");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
    }

    @Test
    void validate_withExtraFields_shouldReturnFailure() {
        // Given - YAML with unrecognized field
        String yamlWithExtraFields = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                unknown_field: "this should not be here"
                metadata:
                  title: "Test Recipe"
                  source: "https://example.com"
                  author: "Test Author"
                  language: "en"
                  date_created: "2024-01-15"
                description: "Test description"
                ingredients:
                  - item: "test"
                    amount: "1"
                    unit: "cup"
                    optional: false
                    component: "main"
                equipment: []
                instructions:
                  - step: 1
                    description: "Test"
                """;

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(yamlWithExtraFields);

        // Then
        assertFalse(result.isValid(), "YAML with extra fields should fail validation");
        assertNotNull(result.getErrorMessage(), "Error message should not be null");
        assertTrue(result.getErrorMessage().contains("Unrecognized field") ||
                        result.getErrorMessage().contains("not part of the recipe schema"),
                "Error message should mention unrecognized field");
    }

    @Test
    void validate_withValidYaml_shouldNormalizeFormat() {
        // Given - Valid YAML with some formatting
        String validYaml = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Simple Recipe"
                  source: "https://example.com"
                  author: "Test"
                  language: "en"
                  date_created: "2024-01-15"
                  servings: 4
                description: "Test"
                ingredients:
                  - item: "flour"
                    amount: "1"
                    unit: "cup"
                    optional: false
                    component: "main"
                equipment: []
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        RecipeValidationService.ValidationResult result = validationService.validate(validYaml);

        // Then
        assertTrue(result.isValid(), "Valid YAML should pass validation");
        assertNotNull(result.getNormalizedYaml(), "Normalized YAML should not be null");
        // The normalized YAML should be parseable again
        RecipeValidationService.ValidationResult secondPass = validationService.validate(result.getNormalizedYaml());
        assertTrue(secondPass.isValid(), "Normalized YAML should be valid on second pass");
    }
}
