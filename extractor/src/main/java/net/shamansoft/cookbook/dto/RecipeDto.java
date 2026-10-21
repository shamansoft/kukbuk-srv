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
    private List<String> category;  // e.g. ["Dinner", "Main Course"]; empty list if none
    private List<String> tags;  // e.g. ["vegan", "Italian"]; empty list if none
    private String language;  // ISO 639-1, e.g. "en"; null if the stored value is not a valid code

    // Content
    private List<IngredientDto> ingredients;
    private List<String> equipment;
    private List<InstructionDto> instructions;
    private NutritionDto nutrition;
    private String notes;

    // Media
    private String coverImageUrl;  // Absolute external URL, or null if absent or unresolvable
    private List<String> allImageUrls;  // All images in recipe as absolute URLs; unresolvable ones omitted
}
