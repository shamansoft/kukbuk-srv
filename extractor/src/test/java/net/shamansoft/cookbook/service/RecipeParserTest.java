package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.exception.InvalidRecipeFormatException;
import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecipeParser Tests")
class RecipeParserTest {

    private static final String VALID_RECIPE_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Chocolate Chip Cookies"
              source: "https://example.com/cookies"
              author: "Jane Doe"
              language: "en"
              servings: 24
              prep_time: "15m"
              cook_time: "12m"
              total_time: "27m"
              difficulty: "easy"
            description: "Classic homemade chocolate chip cookies"
            ingredients:
              - item: "flour"
                amount: "2.25"
                unit: "cups"
              - item: "chocolate chips"
                amount: "2"
                unit: "cups"
            equipment:
              - "mixing bowl"
              - "oven"
            instructions:
              - step: 1
                description: "Preheat oven"
                time: "10m"
                temperature: "375°F"
              - step: 2
                description: "Mix ingredients"
            nutrition:
              serving_size: "1 cookie"
              calories: 200
              protein: 2.5
              carbohydrates: 25.0
              fat: 10.5
            notes: "Store in airtight container"
            """;
    private static final String MINIMAL_RECIPE_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Simple Recipe"
              source: "https://example.com"
            ingredients:
              - item: "item1"
            instructions:
              - step: 1
                description: "Do something"
            """;
    private static final String INVALID_YAML = """
            schema_version: "invalid
            this is not proper YAML: [
            """;
    private static final String MISSING_REQUIRED_FIELD_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              source: "https://example.com"
            ingredients:
              - item: "flour"
            instructions:
              - step: 1
                description: "Mix"
            """;
    private static final String INVALID_SCHEMA_VERSION_YAML = """
            schema_version: "not-a-semver"
            recipe_version: "1.0.0"
            metadata:
              title: "Test Recipe"
              source: "https://example.com"
            ingredients:
              - item: "flour"
            instructions:
              - step: 1
                description: "Mix"
            """;
    private static final String EMPTY_INGREDIENTS_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Empty Ingredients"
              source: "https://example.com"
            ingredients: []
            instructions:
              - step: 1
                description: "Do something"
            """;
    private static final String EMPTY_INSTRUCTIONS_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Empty Instructions"
              source: "https://example.com"
            ingredients:
              - item: "flour"
            instructions: []
            """;
    private RecipeParser recipeParser;

    @BeforeEach
    void setUp() {
        recipeParser = new RecipeParser();
    }

    @Test
    @DisplayName("Should parse valid recipe YAML with all fields")
    void shouldParseValidRecipeYamlWithAllFields() {
        // When
        Recipe recipe = recipeParser.parse(VALID_RECIPE_YAML);

        // Then
        assertThat(recipe).isNotNull();
        assertThat(recipe.schemaVersion()).isEqualTo("1.0.0");
        assertThat(recipe.recipeVersion()).isEqualTo("1.0.0");
        assertThat(recipe.metadata().title()).isEqualTo("Chocolate Chip Cookies");
        assertThat(recipe.metadata().source()).isEqualTo("https://example.com/cookies");
        assertThat(recipe.metadata().author()).isEqualTo("Jane Doe");
        assertThat(recipe.metadata().servings()).isEqualTo(24);
        assertThat(recipe.metadata().prepTime()).isEqualTo("15m");
        assertThat(recipe.metadata().cookTime()).isEqualTo("12m");
        assertThat(recipe.metadata().totalTime()).isEqualTo("27m");
        assertThat(recipe.metadata().difficulty()).isEqualTo("easy");
        assertThat(recipe.description()).isEqualTo("Classic homemade chocolate chip cookies");
        assertThat(recipe.ingredients()).hasSize(2);
        assertThat(recipe.equipment()).hasSize(2);
        assertThat(recipe.instructions()).hasSize(2);
        assertThat(recipe.nutrition()).isNotNull();
        assertThat(recipe.notes()).isEqualTo("Store in airtight container");
    }

    @Test
    @DisplayName("Should parse minimal valid recipe YAML")
    void shouldParseMinimalValidRecipeYaml() {
        // When
        Recipe recipe = recipeParser.parse(MINIMAL_RECIPE_YAML);

        // Then
        assertThat(recipe).isNotNull();
        assertThat(recipe.schemaVersion()).isEqualTo("1.0.0");
        assertThat(recipe.metadata().title()).isEqualTo("Simple Recipe");
        assertThat(recipe.ingredients()).hasSize(1);
        assertThat(recipe.instructions()).hasSize(1);
    }

    @Test
    @DisplayName("Should parse recipe with all ingredient fields")
    void shouldParseRecipeWithAllIngredientFields() {
        String yamlWithCompleteIngredient = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                    amount: "2"
                    unit: "cups"
                    notes: "all-purpose"
                    optional: false
                    component: "dry"
                    substitutions:
                      - item: "cornstarch"
                        amount: "1.5"
                        unit: "cups"
                        ratio: "1:1.33"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithCompleteIngredient);

        // Then
        assertThat(recipe.ingredients()).hasSize(1);
        var ingredient = recipe.ingredients().get(0);
        assertThat(ingredient.item()).isEqualTo("flour");
        assertThat(ingredient.amount()).isEqualTo("2");
        assertThat(ingredient.unit()).isEqualTo("cups");
        assertThat(ingredient.notes()).isEqualTo("all-purpose");
        assertThat(ingredient.optional()).isFalse();
        assertThat(ingredient.component()).isEqualTo("dry");
        assertThat(ingredient.substitutions()).hasSize(1);
    }

    @Test
    @DisplayName("Should parse recipe with instruction media")
    void shouldParseRecipeWithInstructionMedia() {
        String yamlWithMedia = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix ingredients"
                    media:
                      - type: "image"
                        path: "https://example.com/step1.jpg"
                        alt: "Mixed dough"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithMedia);

        // Then
        assertThat(recipe.instructions()).hasSize(1);
        var instruction = recipe.instructions().get(0);
        assertThat(instruction.media()).isNotNull();
        assertThat(instruction.media()).hasSize(1);
    }

    @Test
    @DisplayName("Should parse recipe with cover image")
    void shouldParseRecipeWithCoverImage() {
        String yamlWithCoverImage = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                  cover_image:
                    path: "https://example.com/cover.jpg"
                    alt: "Finished dish"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Cook"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithCoverImage);

        // Then
        assertThat(recipe.metadata().coverImage()).isNotNull();
        assertThat(recipe.metadata().coverImage().path()).isEqualTo("https://example.com/cover.jpg");
        assertThat(recipe.metadata().coverImage().alt()).isEqualTo("Finished dish");
    }

    @Test
    @DisplayName("Should parse recipe with nutrition details")
    void shouldParseRecipeWithNutritionDetails() {
        String yamlWithNutrition = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Cook"
                nutrition:
                  serving_size: "1 slice"
                  calories: 350
                  protein: 8.5
                  carbohydrates: 45.0
                  fat: 15.0
                  fiber: 2.0
                  sugar: 10.0
                  sodium: 250.0
                  notes: "Per serving"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithNutrition);

        // Then
        assertThat(recipe.nutrition()).isNotNull();
        assertThat(recipe.nutrition().servingSize()).isEqualTo("1 slice");
        assertThat(recipe.nutrition().calories()).isEqualTo(350);
        assertThat(recipe.nutrition().protein()).isEqualTo(8.5);
        assertThat(recipe.nutrition().carbohydrates()).isEqualTo(45.0);
        assertThat(recipe.nutrition().fat()).isEqualTo(15.0);
        assertThat(recipe.nutrition().fiber()).isEqualTo(2.0);
        assertThat(recipe.nutrition().sugar()).isEqualTo(10.0);
        assertThat(recipe.nutrition().sodium()).isEqualTo(250.0);
        assertThat(recipe.nutrition().notes()).isEqualTo("Per serving");
    }

    @Test
    @DisplayName("Should throw InvalidRecipeFormatException for malformed YAML")
    void shouldThrowInvalidRecipeFormatExceptionForMalformedYaml() {
        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(INVALID_YAML))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("Failed to parse recipe YAML");
    }

    @Test
    @DisplayName("Should throw InvalidRecipeFormatException for invalid YAML structure")
    void shouldThrowInvalidRecipeFormatExceptionForInvalidYamlStructure() {
        // Given - invalid YAML that cannot be parsed
        String invalidYaml = "{ invalid yaml: }";

        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(invalidYaml))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("Failed to parse recipe YAML");
    }

    @Test
    @DisplayName("Should throw InvalidRecipeFormatException for completely malformed input")
    void shouldThrowInvalidRecipeFormatExceptionForCompletelyMalformedInput() {
        // Given - completely invalid format
        String malformedInput = "not yaml at all !@#$%";

        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(malformedInput))
                .isInstanceOf(InvalidRecipeFormatException.class);
    }

    @Test
    @DisplayName("Should parse recipe with valid semantic version formats")
    void shouldParseRecipeWithValidSemanticVersionFormats() {
        // Given - valid semantic versions
        String validVersionYaml = """
                schema_version: "1.2.3"
                recipe_version: "2.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        Recipe recipe = recipeParser.parse(validVersionYaml);

        // Then
        assertThat(recipe.schemaVersion()).isEqualTo("1.2.3");
        assertThat(recipe.recipeVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("Should parse recipe with multiple categories")
    void shouldParseRecipeWithMultipleCategories() {
        // Given - recipe with category list
        String yamlWithCategories = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Multi-Category Recipe"
                  source: "https://example.com"
                  category:
                    - "dessert"
                    - "vegan"
                    - "gluten-free"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithCategories);

        // Then
        assertThat(recipe.metadata().category()).hasSize(3);
        assertThat(recipe.metadata().category()).contains("dessert", "vegan", "gluten-free");
    }

    @Test
    @DisplayName("Should parse recipe with multiple tags")
    void shouldParseRecipeWithMultipleTags() {
        // Given - recipe with tags list
        String yamlWithTags = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Tagged Recipe"
                  source: "https://example.com"
                  tags:
                    - "quick"
                    - "easy"
                    - "family-friendly"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithTags);

        // Then
        assertThat(recipe.metadata().tags()).hasSize(3);
        assertThat(recipe.metadata().tags()).contains("quick", "easy", "family-friendly");
    }

    @Test
    @DisplayName("Should throw InvalidRecipeFormatException for empty YAML string")
    void shouldThrowInvalidRecipeFormatExceptionForEmptyYaml() {
        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(""))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("Failed to parse recipe YAML");
    }

    @Test
    @DisplayName("Should throw InvalidRecipeFormatException for null YAML string")
    void shouldThrowInvalidRecipeFormatExceptionForNullYaml() {
        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(null))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("Failed to parse recipe YAML");
    }

    @Test
    @DisplayName("Should parse recipe with categories and tags")
    void shouldParseRecipeWithCategoriesAndTags() {
        String yamlWithCategoriesAndTags = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Pasta Carbonara"
                  source: "https://example.com/carbonara"
                  category:
                    - "pasta"
                    - "italian"
                  tags:
                    - "egg"
                    - "cheese"
                    - "bacon"
                ingredients:
                  - item: "spaghetti"
                instructions:
                  - step: 1
                    description: "Cook pasta"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithCategoriesAndTags);

        // Then
        assertThat(recipe.metadata().category()).hasSize(2);
        assertThat(recipe.metadata().category()).contains("pasta", "italian");
        assertThat(recipe.metadata().tags()).hasSize(3);
        assertThat(recipe.metadata().tags()).contains("egg", "cheese", "bacon");
    }

    @Test
    @DisplayName("Should parse recipe with date created")
    void shouldParseRecipeWithDateCreated() {
        String yamlWithDate = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                  source: "https://example.com"
                  date_created: "2024-01-15"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithDate);

        // Then
        assertThat(recipe.metadata().dateCreated()).isNotNull();
        assertThat(recipe.metadata().dateCreated().toString()).isEqualTo("2024-01-15");
    }

    @Test
    @DisplayName("Should parse recipe with multiple instruction attributes")
    void shouldParseRecipeWithMultipleInstructionAttributes() {
        String yamlWithDetailedInstructions = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Preheat oven"
                    time: "10m"
                    temperature: "375°F"
                  - step: 2
                    description: "Bake"
                    time: "30m"
                    temperature: "375°F"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithDetailedInstructions);

        // Then
        assertThat(recipe.instructions()).hasSize(2);
        var step1 = recipe.instructions().get(0);
        assertThat(step1.step()).isEqualTo(1);
        assertThat(step1.time()).isEqualTo("10m");
        assertThat(step1.temperature()).isEqualTo("375°F");

        var step2 = recipe.instructions().get(1);
        assertThat(step2.step()).isEqualTo(2);
        assertThat(step2.time()).isEqualTo("30m");
        assertThat(step2.temperature()).isEqualTo("375°F");
    }

    @Test
    @DisplayName("Should parse recipe with default language when not specified")
    void shouldParseRecipeWithDefaultLanguageWhenNotSpecified() {
        String yamlWithoutLanguage = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test"
                  source: "https://example.com"
                ingredients:
                  - item: "flour"
                instructions:
                  - step: 1
                    description: "Cook"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithoutLanguage);

        // Then
        assertThat(recipe.metadata().language()).isEqualTo("en");
    }

    @Test
    @DisplayName("Should parse recipe with large number of ingredients")
    void shouldParseRecipeWithLargeNumberOfIngredients() {
        // Given - recipe with many ingredients
        String yamlWithManyIngredients = """
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Complex Recipe"
                  source: "https://example.com"
                ingredients:
                  - item: "ingredient1"
                  - item: "ingredient2"
                  - item: "ingredient3"
                  - item: "ingredient4"
                  - item: "ingredient5"
                instructions:
                  - step: 1
                    description: "Mix all"
                """;

        // When
        Recipe recipe = recipeParser.parse(yamlWithManyIngredients);

        // Then
        assertThat(recipe.ingredients()).hasSize(5);
    }

    @Test
    @DisplayName("Should wrap underlying parser exceptions with InvalidRecipeFormatException")
    void shouldWrapUnderlyingParserExceptionsWithInvalidRecipeFormatException() {
        // Given - malformed YAML
        String malformedYaml = "{ this is not valid yaml at all }";

        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(malformedYaml))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("Failed to parse recipe YAML")
                .hasCauseInstanceOf(Exception.class);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("Should include YAML preview in exception for long input")
    void shouldIncludeYamlPreviewInExceptionMessageForLongInput() {
        // Given - invalid YAML but long enough to trigger preview truncation
        StringBuilder sb = new StringBuilder();
        sb.append("schema_version: \"invalid\"\n");
        for (int i = 0; i < 300; i++) sb.append('x');
        String longInvalid = sb.toString();

        // When & Then
        assertThatThrownBy(() -> recipeParser.parse(longInvalid))
                .isInstanceOf(InvalidRecipeFormatException.class)
                .hasMessageContaining("YAML preview:")
                .hasMessageContaining("...");
    }
}
