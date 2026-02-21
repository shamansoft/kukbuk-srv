package net.shamansoft.cookbook.service.gemini;

import net.shamansoft.recipe.model.Recipe;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level wrapper returned by Gemini for recipe extraction.
 * Contains the overall page-level verdict plus a list of all recipes found.
 *
 * <p>The LLM returns this as the root JSON object. The {@code is_recipe} and
 * {@code recipe_confidence} fields apply to the whole page, while individual
 * recipes are in the {@code recipes} array.
 *
 * <p>When {@code is_recipe} is false, {@code recipes} will be empty.
 * When {@code recipe_confidence >= threshold} AND {@code is_recipe} is false,
 * the HTML may have been over-cleaned — the adaptive cleaning loop will retry
 * with a less restrictive strategy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExtractionResult(
        @JsonProperty("is_recipe") boolean isRecipe,
        @JsonProperty("recipe_confidence") Double recipeConfidence,
        @JsonProperty("internal_reasoning") String internalReasoning,
        @JsonProperty("recipes") List<Recipe> recipes
) {
    public GeminiExtractionResult {
        if (recipeConfidence == null) {
            recipeConfidence = 0.0;
        }
        if (recipes == null) {
            recipes = List.of();
        }
    }
}
