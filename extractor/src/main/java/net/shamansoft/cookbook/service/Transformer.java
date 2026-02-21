package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Recipe;

import java.util.List;

public interface Transformer {

    /**
     * Transforms HTML content to Recipe objects.
     *
     * @param htmlContent the HTML string to transform
     * @param sourceUrl   the source URL of the recipe
     * @return the transformed result containing Recipe objects or a non-recipe indicator
     */
    Response transform(String htmlContent, String sourceUrl);

    /**
     * Represents the response of a transformation.
     * A page may contain zero, one, or multiple recipes.
     *
     * @param isRecipe       whether the content contains at least one cooking recipe
     * @param confidence     LLM confidence (0.0–1.0) that the page is a recipe.
     *                       High confidence + isRecipe=false signals over-cleaned HTML.
     * @param recipes        the parsed Recipe objects (empty if isRecipe is false)
     * @param rawLlmResponse raw response text from the LLM (optional, for debugging)
     */
    record Response(boolean isRecipe, double confidence, List<Recipe> recipes, String rawLlmResponse) {

        /**
         * Convenience accessor for single-recipe scenarios.
         * Returns the first recipe in the list, or null if the list is empty.
         */
        public Recipe recipe() {
            return recipes == null || recipes.isEmpty() ? null : recipes.get(0);
        }

        /**
         * Creates a Response indicating the content is not a recipe (confidence=0.0).
         */
        public static Response notRecipe() {
            return new Response(false, 0.0, List.of(), null);
        }

        /**
         * Creates a Response indicating the content is not a recipe with an explicit confidence.
         * Use when the LLM is uncertain (e.g., confidence=0.6 means "might be a recipe
         * if given more HTML context").
         *
         * @param confidence LLM's estimated probability that the page is a recipe (0.0–1.0)
         */
        public static Response notRecipe(double confidence) {
            return new Response(false, confidence, List.of(), null);
        }

        /**
         * Creates a Response with a single valid recipe (confidence=1.0).
         *
         * @param recipe the Recipe object
         */
        public static Response recipe(Recipe recipe) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe cannot be null when isRecipe is true");
            }
            return new Response(true, 1.0, List.of(recipe), null);
        }

        /**
         * Creates a Response with multiple valid recipes (confidence=1.0).
         *
         * @param recipeList the list of Recipe objects (must be non-empty)
         */
        public static Response recipes(List<Recipe> recipeList) {
            if (recipeList == null || recipeList.isEmpty()) {
                throw new IllegalArgumentException("Recipe list cannot be null or empty when isRecipe is true");
            }
            return new Response(true, 1.0, recipeList, null);
        }

        /**
         * Creates a Response with full control over all fields (for internal use).
         *
         * @param isRecipe       whether the content is a cooking recipe
         * @param confidence     LLM confidence score (0.0–1.0)
         * @param recipeList     the Recipe objects (empty list if not a recipe)
         * @param rawLlmResponse raw response text from the LLM
         */
        public static Response withRawResponse(boolean isRecipe, double confidence,
                                               List<Recipe> recipeList, String rawLlmResponse) {
            return new Response(isRecipe, confidence,
                    recipeList != null ? recipeList : List.of(),
                    rawLlmResponse);
        }
    }
}
