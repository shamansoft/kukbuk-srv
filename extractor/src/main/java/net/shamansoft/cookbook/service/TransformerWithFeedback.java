package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Recipe;

/**
 * Extended transformer interface that supports retry with validation feedback.
 * Implementations can use validation errors from previous attempts to improve results.
 */
public interface TransformerWithFeedback extends Transformer {

    /**
     * Transforms content with validation feedback from a previous attempt.
     *
     * @param htmlContent     the content to transform (may be null for non-HTML transformers)
     * @param sourceUrl       the source URL
     * @param previousRecipe  the Recipe from the previous attempt that failed validation
     * @param validationError the validation error message to help correct the issue
     * @return the transformed result
     */
    Response transformWithFeedback(String htmlContent, String sourceUrl,
                                   Recipe previousRecipe, String validationError);
}
