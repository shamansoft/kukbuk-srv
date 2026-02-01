package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Recipe;

public interface Transformer {

    /**
     * Transforms HTML content to a Recipe object.
     *
     * @param htmlContent the HTML string to transform
     * @return the transformed result containing a Recipe object or non-recipe indicator
     */
    Response transform(String htmlContent);

    /**
     * Represents the response of a transformation.
     * Contains information on whether the content is a recipe and the Recipe object.
     *
     * @param isRecipe whether the content is a cooking recipe
     * @param recipe the parsed Recipe object (null if isRecipe is false)
     * @param rawLlmResponse raw response text from the LLM (optional, for debugging)
     */
    record Response(boolean isRecipe, Recipe recipe, String rawLlmResponse) {

        /**
         * Creates a Response indicating the content is not a recipe.
         */
        public static Response notRecipe() {
            return new Response(false, null, null);
        }

        /**
         * Creates a Response with a valid recipe.
         *
         * @param recipe the Recipe object
         */
        public static Response recipe(Recipe recipe) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe cannot be null when isRecipe is true");
            }
            return new Response(true, recipe, null);
        }

        /**
         * Creates a Response with raw LLM response included (for debugging).
         *
         * @param isRecipe       whether the content is a cooking recipe
         * @param recipe         the Recipe object
         * @param rawLlmResponse raw response text from the LLM
         */
        public static Response withRawResponse(boolean isRecipe, Recipe recipe, String rawLlmResponse) {
            return new Response(isRecipe, recipe, rawLlmResponse);
        }
    }
}