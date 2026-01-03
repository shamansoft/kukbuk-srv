package net.shamansoft.cookbook.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Fully parsed recipe DTO for mobile API.
 * Contains all recipe data including ingredients, instructions, and metadata.
 * Used by both list and detail endpoints.
 */
@Data
@Builder
public class RecipeDto {
    // Identity
    private String id;  // Google Drive file ID
    private String lastModified;  // ISO-8601 timestamp

    // Metadata
    private String title;
    private String description;
    private String author;
    private String source;
    private Integer servings;
    private String prepTime;
    private String cookTime;
    private String totalTime;
    private String difficulty;

    // Content
    private List<IngredientDto> ingredients;
    private List<String> equipment;
    private List<InstructionDto> instructions;
    private NutritionDto nutrition;
    private String notes;

    // Media
    private String coverImageUrl;  // External URL or null
    private List<String> allImageUrls;  // All images in recipe
}
