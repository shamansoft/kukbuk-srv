package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.IngredientDto;
import net.shamansoft.cookbook.dto.InstructionDto;
import net.shamansoft.cookbook.dto.NutritionDto;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.recipe.model.ImageMedia;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Media;
import net.shamansoft.recipe.model.Nutrition;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps Recipe SDK objects to mobile-friendly DTOs.
 * Handles extraction of image URLs and field transformations.
 */
@Component
@Slf4j
public class RecipeMapper {

    /**
     * Map Recipe to DTO using Drive file metadata.
     *
     * @param recipe   Parsed recipe from YAML
     * @param metadata Google Drive file metadata
     * @return Recipe DTO for mobile API
     */
    public RecipeDto toDto(Recipe recipe, GoogleDrive.DriveFileMetadata metadata) {
        return RecipeDto.builder()
                .id(metadata.id())
                .lastModified(metadata.modifiedTime())
                .title(recipe.metadata().title())
                .description(recipe.description())
                .author(recipe.metadata().author())
                .source(recipe.metadata().source())
                .servings(recipe.metadata().servings())
                .prepTime(recipe.metadata().prepTime())
                .cookTime(recipe.metadata().cookTime())
                .totalTime(recipe.metadata().totalTime())
                .difficulty(recipe.metadata().difficulty())
                .ingredients(mapIngredients(recipe.ingredients()))
                .equipment(recipe.equipment())
                .instructions(mapInstructions(recipe.instructions()))
                .nutrition(mapNutrition(recipe.nutrition()))
                .notes(recipe.notes())
                .coverImageUrl(extractCoverImageUrl(recipe))
                .allImageUrls(extractAllImageUrls(recipe))
                .build();
    }

    /**
     * Map Recipe to DTO using Drive file info (from list endpoint).
     *
     * @param recipe   Parsed recipe from YAML
     * @param fileInfo Google Drive file info
     * @return Recipe DTO for mobile API
     */
    public RecipeDto toDto(Recipe recipe, GoogleDrive.DriveFileInfo fileInfo) {
        return RecipeDto.builder()
                .id(fileInfo.id())
                .lastModified(fileInfo.modifiedTime())
                .title(recipe.metadata().title())
                .description(recipe.description())
                .author(recipe.metadata().author())
                .source(recipe.metadata().source())
                .servings(recipe.metadata().servings())
                .prepTime(recipe.metadata().prepTime())
                .cookTime(recipe.metadata().cookTime())
                .totalTime(recipe.metadata().totalTime())
                .difficulty(recipe.metadata().difficulty())
                .ingredients(mapIngredients(recipe.ingredients()))
                .equipment(recipe.equipment())
                .instructions(mapInstructions(recipe.instructions()))
                .nutrition(mapNutrition(recipe.nutrition()))
                .notes(recipe.notes())
                .coverImageUrl(extractCoverImageUrl(recipe))
                .allImageUrls(extractAllImageUrls(recipe))
                .build();
    }

    /**
     * Extract cover image URL from recipe metadata.
     * Returns null if no cover image is set.
     */
    private String extractCoverImageUrl(Recipe recipe) {
        if (recipe.metadata().coverImage() != null) {
            return recipe.metadata().coverImage().path();
        }
        return null;
    }

    /**
     * Extract all image URLs from recipe (cover image + instruction media).
     * Returns external URLs that mobile app should fetch directly.
     * Drive-hosted images should be proxied through /v1/media/{id}.
     */
    private List<String> extractAllImageUrls(Recipe recipe) {
        List<String> urls = new ArrayList<>();

        // Add cover image
        if (recipe.metadata().coverImage() != null) {
            urls.add(recipe.metadata().coverImage().path());
        }

        // Add images from instruction media
        for (Instruction instruction : recipe.instructions()) {
            if (instruction.media() != null) {
                instruction.media().stream()
                        .filter(m -> m instanceof ImageMedia)
                        .map(Media::path)
                        .forEach(urls::add);
            }
        }

        return urls;
    }

    /**
     * Map list of Ingredient to IngredientDto.
     */
    private List<IngredientDto> mapIngredients(List<Ingredient> ingredients) {
        if (ingredients == null) {
            return Collections.emptyList();
        }

        return ingredients.stream()
                .map(this::mapIngredient)
                .collect(Collectors.toList());
    }

    /**
     * Map single Ingredient to IngredientDto.
     */
    private IngredientDto mapIngredient(Ingredient ingredient) {
        return IngredientDto.builder()
                .item(ingredient.item())
                .amount(ingredient.amount())
                .unit(ingredient.unit())
                .notes(ingredient.notes())
                .optional(ingredient.optional())
                .component(ingredient.component())
                .build();
    }

    /**
     * Map list of Instruction to InstructionDto.
     */
    private List<InstructionDto> mapInstructions(List<Instruction> instructions) {
        if (instructions == null) {
            return Collections.emptyList();
        }

        return instructions.stream()
                .map(this::mapInstruction)
                .collect(Collectors.toList());
    }

    /**
     * Map single Instruction to InstructionDto.
     * Note: Media URLs are not included in InstructionDto - they're in allImageUrls.
     */
    private InstructionDto mapInstruction(Instruction instruction) {
        return InstructionDto.builder()
                .step(instruction.step())
                .description(instruction.description())
                .time(instruction.time())
                .temperature(instruction.temperature())
                .build();
    }

    /**
     * Map Nutrition to NutritionDto.
     * Returns null if nutrition is not provided.
     */
    private NutritionDto mapNutrition(Nutrition nutrition) {
        if (nutrition == null) {
            return null;
        }

        return NutritionDto.builder()
                .servingSize(nutrition.servingSize())
                .calories(nutrition.calories())
                .protein(nutrition.protein())
                .carbohydrates(nutrition.carbohydrates())
                .fat(nutrition.fat())
                .fiber(nutrition.fiber())
                .sugar(nutrition.sugar())
                .sodium(nutrition.sodium())
                .notes(nutrition.notes())
                .build();
    }
}
