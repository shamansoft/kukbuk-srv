package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents metadata for a recipe.
 *
 * @param title Recipe name (required)
 * @param source Original recipe URL (required)
 * @param author Recipe author
 * @param language Language code (e.g., "en", "en/us")
 * @param dateCreated Date the recipe was created
 * @param category Recipe categories (e.g., "breakfast", "dessert")
 * @param tags Recipe tags (e.g., "chocolate", "vegan")
 * @param servings Number of servings (required)
 * @param prepTime Preparation time (e.g., "15m", "1h 30m")
 * @param cookTime Cooking time
 * @param totalTime Total time
 * @param difficulty Difficulty level ("easy", "medium", "hard")
 * @param coverImage Cover image for the recipe
 */
public record RecipeMetadata(
        @NotBlank
        @JsonProperty("title")
        String title,

        @JsonProperty("source")
        String source,

        @JsonProperty("author")
        String author,

        @JsonProperty("language")
        String language,

        @JsonProperty("date_created")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dateCreated,

        @JsonProperty("category")
        List<String> category,

        @JsonProperty("tags")
        List<String> tags,

        @JsonProperty("servings")
        Integer servings,

        @JsonProperty("prep_time")
        String prepTime,

        @JsonProperty("cook_time")
        String cookTime,

        @JsonProperty("total_time")
        String totalTime,

        @JsonProperty("difficulty")
        String difficulty,

        @Valid
        @JsonProperty("cover_image")
        CoverImage coverImage
) {
    public RecipeMetadata {
        if (language == null) {
            language = "en";
        }
        if (category == null) {
            category = List.of();
        }
        if (tags == null) {
            tags = List.of();
        }
        if (difficulty == null) {
            difficulty = "medium";
        }
    }
}
