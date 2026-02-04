package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.IngredientDto;
import net.shamansoft.cookbook.dto.InstructionDto;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.ImageMedia;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Media;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecipeMapper Tests")
class RecipeMapperTest {

    private static final String RECIPE_ID = "drive-file-123";
    private static final String LAST_MODIFIED = "2024-01-15T10:30:00Z";
    private static final String RECIPE_TITLE = "Chocolate Chip Cookies";
    private static final String RECIPE_AUTHOR = "Gordon Ramsay";
    private static final String RECIPE_SOURCE = "https://example.com/recipe";
    private static final String RECIPE_DESCRIPTION = "Classic chocolate chip cookies";
    private static final Integer SERVINGS = 24;
    private static final String PREP_TIME = "15m";
    private static final String COOK_TIME = "12m";
    private static final String TOTAL_TIME = "27m";
    private static final String DIFFICULTY = "easy";
    private static final String NOTES = "Store in airtight container";
    private static final String COVER_IMAGE_URL = "https://example.com/cover.jpg";
    private static final String COVER_IMAGE_ALT = "Freshly baked cookies";
    private RecipeMapper recipeMapper;

    @BeforeEach
    void setUp() {
        recipeMapper = new RecipeMapper();
    }

    // ===== Test: toDto with DriveFileMetadata =====

    @Test
    @DisplayName("Should map recipe with metadata to DTO using DriveFileMetadata")
    void shouldMapRecipeWithMetadataToDtoUsingDriveFileMetadata() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(RECIPE_ID);
        assertThat(dto.getLastModified()).isEqualTo(LAST_MODIFIED);
        assertThat(dto.getTitle()).isEqualTo(RECIPE_TITLE);
        assertThat(dto.getDescription()).isEqualTo(RECIPE_DESCRIPTION);
        assertThat(dto.getAuthor()).isEqualTo(RECIPE_AUTHOR);
        assertThat(dto.getSource()).isEqualTo(RECIPE_SOURCE);
        assertThat(dto.getServings()).isEqualTo(SERVINGS);
        assertThat(dto.getPrepTime()).isEqualTo(PREP_TIME);
        assertThat(dto.getCookTime()).isEqualTo(COOK_TIME);
        assertThat(dto.getTotalTime()).isEqualTo(TOTAL_TIME);
        assertThat(dto.getDifficulty()).isEqualTo(DIFFICULTY);
        assertThat(dto.getNotes()).isEqualTo(NOTES);
    }

    @Test
    @DisplayName("Should map recipe with no cover image to DTO")
    void shouldMapRecipeWithNoCoverImageToDtoUsingMetadata() {
        // Given
        Recipe recipe = createRecipeWithoutCoverImage();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getCoverImageUrl()).isNull();
        assertThat(dto.getAllImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("Should map ingredients correctly using DriveFileMetadata")
    void shouldMapIngredientsCorrectlyUsingMetadata() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getIngredients()).hasSize(2);

        IngredientDto flour = dto.getIngredients().get(0);
        assertThat(flour.getName()).isEqualTo("all-purpose flour");
        assertThat(flour.getAmount()).isEqualTo("2.25");
        assertThat(flour.getUnit()).isEqualTo("cups");
        assertThat(flour.getOptional()).isFalse();
        assertThat(flour.getComponent()).isEqualTo("main");

        IngredientDto chocolate = dto.getIngredients().get(1);
        assertThat(chocolate.getName()).isEqualTo("chocolate chips");
        assertThat(chocolate.getAmount()).isEqualTo("2");
        assertThat(chocolate.getUnit()).isEqualTo("cups");
        assertThat(chocolate.getOptional()).isFalse();
    }

    @Test
    @DisplayName("Should map instructions correctly using DriveFileMetadata")
    void shouldMapInstructionsCorrectlyUsingMetadata() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getInstructions()).hasSize(2);

        InstructionDto step1 = dto.getInstructions().get(0);
        assertThat(step1.getStep()).isEqualTo(1);
        assertThat(step1.getDescription()).isEqualTo("Preheat oven to 375°F");
        assertThat(step1.getTime()).isEqualTo("10m");
        assertThat(step1.getTemperature()).isEqualTo("375°F");

        InstructionDto step2 = dto.getInstructions().get(1);
        assertThat(step2.getStep()).isEqualTo(2);
        assertThat(step2.getDescription()).isEqualTo("Mix ingredients");
        assertThat(step2.getTime()).isNull();
    }

    @Test
    @DisplayName("Should map nutrition correctly using DriveFileMetadata")
    void shouldMapNutritionCorrectlyUsingMetadata() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getNutrition()).isNotNull();
        assertThat(dto.getNutrition().getServingSize()).isEqualTo("1 cookie");
        assertThat(dto.getNutrition().getCalories()).isEqualTo(200);
        assertThat(dto.getNutrition().getProtein()).isEqualTo(2.5);
        assertThat(dto.getNutrition().getCarbohydrates()).isEqualTo(25.0);
        assertThat(dto.getNutrition().getFat()).isEqualTo(10.5);
        assertThat(dto.getNutrition().getFiber()).isEqualTo(1.2);
        assertThat(dto.getNutrition().getSugar()).isEqualTo(18.5);
        assertThat(dto.getNutrition().getSodium()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Should map recipe without nutrition to DTO")
    void shouldMapRecipeWithoutNutritionUsingMetadata() {
        // Given
        Recipe recipe = createRecipeWithoutNutrition();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getNutrition()).isNull();
    }

    @Test
    @DisplayName("Should map equipment correctly using DriveFileMetadata")
    void shouldMapEquipmentCorrectlyUsingMetadata() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getEquipment()).hasSize(2);
        assertThat(dto.getEquipment()).containsExactly("mixing bowl", "oven");
    }

    // ===== Test: toDto with DriveFileInfo =====

    @Test
    @DisplayName("Should map recipe to DTO using DriveFileInfo")
    void shouldMapRecipeToDtoUsingDriveFileInfo() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileInfo fileInfo = new GoogleDrive.DriveFileInfo(
                RECIPE_ID, "recipe.yaml", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileInfo);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(RECIPE_ID);
        assertThat(dto.getLastModified()).isEqualTo(LAST_MODIFIED);
        assertThat(dto.getTitle()).isEqualTo(RECIPE_TITLE);
    }

    @Test
    @DisplayName("Should map ingredients using DriveFileInfo")
    void shouldMapIngredientsUsingDriveFileInfo() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileInfo fileInfo = new GoogleDrive.DriveFileInfo(
                RECIPE_ID, "recipe.yaml", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileInfo);

        // Then
        assertThat(dto.getIngredients()).hasSize(2);
        assertThat(dto.getIngredients().get(0).getName()).isEqualTo("all-purpose flour");
    }

    // ===== Test: Image URLs extraction =====

    @Test
    @DisplayName("Should extract cover image URL")
    void shouldExtractCoverImageUrl() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getCoverImageUrl()).isEqualTo(COVER_IMAGE_URL);
    }

    @Test
    @DisplayName("Should extract all image URLs including cover and instruction media")
    void shouldExtractAllImageUrlsIncludingCoverAndInstructionMedia() {
        // Given
        Recipe recipe = createRecipeWithMultipleImages();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getAllImageUrls()).hasSize(3);
        assertThat(dto.getAllImageUrls()).contains(
                COVER_IMAGE_URL,
                "https://example.com/step1.jpg",
                "https://example.com/step2.jpg"
        );
    }

    @Test
    @DisplayName("Should handle recipe with no images in instructions")
    void shouldHandleRecipeWithNoImagesInInstructions() {
        // Given
        Recipe recipe = createCompleteRecipe();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getAllImageUrls()).hasSize(1);
        assertThat(dto.getAllImageUrls()).contains(COVER_IMAGE_URL);
    }

    @Test
    @DisplayName("Should handle instructions with null media list")
    void shouldHandleInstructionsWithNullMediaList() {
        // Given
        Recipe recipe = createRecipeWithInstructionsNullMedia();
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, metadata);

        // Then
        assertThat(dto.getAllImageUrls()).hasSize(1);
        assertThat(dto.getAllImageUrls()).contains(COVER_IMAGE_URL);
    }

    // ===== Test: Null/Empty handling =====

    @Test
    @DisplayName("Should handle recipe with empty ingredients list")
    void shouldHandleRecipeWithEmptyIngredients() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY, null
        );
        Recipe recipe = new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                Collections.emptyList(), Collections.emptyList(),
                List.of(createInstruction(1, "Step 1")),
                null, NOTES, null
        );
        GoogleDrive.DriveFileMetadata fileMetadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileMetadata);

        // Then
        assertThat(dto.getIngredients()).isEmpty();
    }

    @Test
    @DisplayName("Should handle recipe with empty instructions list")
    void shouldHandleRecipeWithEmptyInstructions() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY, null
        );
        Recipe recipe = new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                List.of(createIngredient("flour")), Collections.emptyList(),
                Collections.emptyList(), null, NOTES, null
        );
        GoogleDrive.DriveFileMetadata fileMetadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileMetadata);

        // Then
        assertThat(dto.getInstructions()).isEmpty();
    }

    @Test
    @DisplayName("Should handle ingredient with notes and optional flag")
    void shouldHandleIngredientWithNotesAndOptionalFlag() {
        // Given
        List<Ingredient> ingredients = List.of(
                new Ingredient("vanilla extract", "1", "tsp", "or vanilla paste", true,
                        Collections.emptyList(), "main")
        );
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY, null
        );
        Recipe recipe = new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                ingredients, Collections.emptyList(),
                List.of(createInstruction(1, "Mix all")), null, NOTES, null
        );
        GoogleDrive.DriveFileMetadata fileMetadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileMetadata);

        // Then
        assertThat(dto.getIngredients()).hasSize(1);
        IngredientDto ingredientDto = dto.getIngredients().get(0);
        assertThat(ingredientDto.getOptional()).isTrue();
        assertThat(ingredientDto.getNotes()).isEqualTo("or vanilla paste");
        assertThat(ingredientDto.getComponent()).isEqualTo("main");
    }

    @Test
    @DisplayName("Should handle ingredient with component grouping")
    void shouldHandleIngredientWithComponentGrouping() {
        // Given
        List<Ingredient> ingredients = List.of(
                new Ingredient("butter", "2", "tbsp", null, false,
                        Collections.emptyList(), "filling"),
                new Ingredient("flour", "2", "cups", null, false,
                        Collections.emptyList(), "dough")
        );
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY, null
        );
        Recipe recipe = new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                ingredients, Collections.emptyList(),
                List.of(createInstruction(1, "Mix")), null, NOTES, null
        );
        GoogleDrive.DriveFileMetadata fileMetadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileMetadata);

        // Then
        assertThat(dto.getIngredients()).hasSize(2);
        assertThat(dto.getIngredients().get(0).getComponent()).isEqualTo("filling");
        assertThat(dto.getIngredients().get(1).getComponent()).isEqualTo("dough");
    }

    // ===== Test: Metadata fields =====

    @Test
    @DisplayName("Should map all metadata fields correctly")
    void shouldMapAllMetadataFieldsCorrectly() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Pasta Carbonara",
                "https://example.com/carbonara",
                "Jamie Oliver",
                "it",
                LocalDate.of(2023, 1, 15),
                List.of("pasta", "italian"),
                List.of("egg", "cheese", "bacon"),
                4,
                "5m",
                "20m",
                "25m",
                "medium",
                new CoverImage("https://example.com/carbonara.jpg", "Creamy pasta")
        );
        Recipe recipe = new Recipe(
                true, "1.0.0", "1.0.0", metadata, "A classic Italian pasta",
                List.of(createIngredient("spaghetti")), Collections.emptyList(),
                List.of(createInstruction(1, "Cook pasta")), null, "Traditional recipe", null
        );
        GoogleDrive.DriveFileMetadata fileMetadata = new GoogleDrive.DriveFileMetadata(
                RECIPE_ID, "recipe.yaml", "text/plain", LAST_MODIFIED
        );

        // When
        RecipeDto dto = recipeMapper.toDto(recipe, fileMetadata);

        // Then
        assertThat(dto.getTitle()).isEqualTo("Pasta Carbonara");
        assertThat(dto.getAuthor()).isEqualTo("Jamie Oliver");
        assertThat(dto.getSource()).isEqualTo("https://example.com/carbonara");
        assertThat(dto.getDifficulty()).isEqualTo("medium");
    }

    // ===== Helper methods =====

    private Recipe createCompleteRecipe() {
        List<Ingredient> ingredients = List.of(
                createIngredient("all-purpose flour", "2.25", "cups"),
                createIngredient("chocolate chips", "2", "cups")
        );

        List<String> equipment = List.of("mixing bowl", "oven");

        List<Instruction> instructions = List.of(
                createInstructionWithTemperature(1, "Preheat oven to 375°F", "10m", "375°F"),
                createInstruction(2, "Mix ingredients")
        );

        Nutrition nutrition = new Nutrition(
                "1 cookie", 200, 2.5, 25.0, 10.5, 1.2, 18.5, 150.0, "Per serving"
        );

        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY,
                new CoverImage(COVER_IMAGE_URL, COVER_IMAGE_ALT)
        );

        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                ingredients, equipment, instructions, nutrition, NOTES, null
        );
    }

    private Recipe createRecipeWithoutCoverImage() {
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY, null
        );

        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                List.of(createIngredient("flour")), Collections.emptyList(),
                List.of(createInstruction(1, "Step 1")), null, NOTES, null
        );
    }

    private Recipe createRecipeWithoutNutrition() {
        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY,
                new CoverImage(COVER_IMAGE_URL, COVER_IMAGE_ALT)
        );

        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                List.of(createIngredient("flour")), Collections.emptyList(),
                List.of(createInstruction(1, "Step 1")), null, NOTES, null
        );
    }

    private Recipe createRecipeWithMultipleImages() {
        List<Media> media1 = List.of(
                new ImageMedia("https://example.com/step1.jpg", "Step 1 image")
        );
        List<Media> media2 = List.of(
                new ImageMedia("https://example.com/step2.jpg", "Step 2 image")
        );

        List<Instruction> instructions = List.of(
                new Instruction(1, "Preheat", null, null, media1),
                new Instruction(2, "Mix", null, null, media2)
        );

        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY,
                new CoverImage(COVER_IMAGE_URL, COVER_IMAGE_ALT)
        );

        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                List.of(createIngredient("flour")), Collections.emptyList(),
                instructions, null, NOTES, null
        );
    }

    private Recipe createRecipeWithInstructionsNullMedia() {
        List<Instruction> instructions = List.of(
                new Instruction(1, "Preheat", null, null, null),
                new Instruction(2, "Mix", null, null, null)
        );

        RecipeMetadata metadata = new RecipeMetadata(
                RECIPE_TITLE, RECIPE_SOURCE, RECIPE_AUTHOR, "en", null,
                Collections.emptyList(), Collections.emptyList(), SERVINGS, PREP_TIME,
                COOK_TIME, TOTAL_TIME, DIFFICULTY,
                new CoverImage(COVER_IMAGE_URL, COVER_IMAGE_ALT)
        );

        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, RECIPE_DESCRIPTION,
                List.of(createIngredient("flour")), Collections.emptyList(),
                instructions, null, NOTES, null
        );
    }

    private Ingredient createIngredient(String item) {
        return createIngredient(item, "1", "unit");
    }

    private Ingredient createIngredient(String item, String amount, String unit) {
        return new Ingredient(item, amount, unit, null, false, Collections.emptyList(), "main");
    }

    private Instruction createInstruction(Integer step, String description) {
        return new Instruction(step, description, null, null, null);
    }

    private Instruction createInstructionWithTemperature(Integer step, String description,
                                                         String time, String temperature) {
        return new Instruction(step, description, time, temperature, null);
    }
}