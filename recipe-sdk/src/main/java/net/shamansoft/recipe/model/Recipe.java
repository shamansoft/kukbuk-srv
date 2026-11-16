package net.shamansoft.recipe.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Objects;

/**
 * Represents a complete recipe with all its components.
 * This is the root model class for recipe data.
 *
 * @param schemaVersion Semantic version of the schema (e.g., "1.0.0")
 * @param recipeVersion Semantic version of the recipe (e.g., "1.0.0")
 * @param metadata Recipe metadata (title, author, source, etc.)
 * @param description Markdown-formatted description
 * @param ingredients List of ingredients (required, at least one)
 * @param equipment List of required equipment
 * @param instructions List of instruction steps (required, at least one)
 * @param nutrition Nutritional information
 * @param notes Markdown-formatted notes, tips and tricks
 * @param storage Storage instructions
 * @param userTags Tags the user added (stored as {@code user_tags}, omitted when empty). Kept apart from the
 *                 extractor's {@code metadata.tags}: extraction never writes them, saves carry them over.
 */
public record Recipe(
        @NotNull
        @JsonProperty("is_recipe")
        Boolean isRecipe,

        @NotNull
        @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Schema version must be semantic version like '1.0.0'")
        @JsonProperty("schema_version")
        String schemaVersion,

        @NotNull
        @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Recipe version must be semantic version like '1.0.0'")
        @JsonProperty("recipe_version")
        String recipeVersion,

        @NotNull
        @Valid
        @JsonProperty("metadata")
        RecipeMetadata metadata,

        @JsonProperty("description")
        String description,

        @NotEmpty
        @Valid
        @JsonProperty("ingredients")
        List<Ingredient> ingredients,

        @JsonProperty("equipment")
        List<String> equipment,

        @NotEmpty
        @Valid
        @JsonProperty("instructions")
        List<Instruction> instructions,

        @Valid
        @JsonProperty("nutrition")
        Nutrition nutrition,

        @JsonProperty("notes")
        String notes,

        @Valid
        @JsonProperty("storage")
        Storage storage,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("user_tags")
        List<String> userTags
) {
    /**
     * Recipe without user data, as produced by extraction.
     */
    public Recipe(Boolean isRecipe, String schemaVersion, String recipeVersion, RecipeMetadata metadata,
                  String description, List<Ingredient> ingredients, List<String> equipment,
                  List<Instruction> instructions, Nutrition nutrition, String notes, Storage storage) {
        this(isRecipe, schemaVersion, recipeVersion, metadata, description, ingredients, equipment,
                instructions, nutrition, notes, storage, List.of());
    }

    public Recipe {
        if (description == null) {
            description = "";
        }
        if (equipment == null) {
            equipment = List.of();
        }
        if (notes == null) {
            notes = "";
        }
        userTags = userTags == null ? List.of() : userTags.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Returns a copy of this recipe with the given user tags.
     */
    public Recipe withUserTags(List<String> userTags) {
        return new Recipe(isRecipe, schemaVersion, recipeVersion, metadata, description, ingredients, equipment,
                instructions, nutrition, notes, storage, userTags);
    }
}
