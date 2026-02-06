package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Service that combines recipe transformation with validation and retry logic.
 * When Gemini produces invalid Recipe objects, it retries with validation feedback.
 * This is the primary Transformer implementation that should be used by controllers.
 */
@Service
@Primary
@Slf4j
public class ValidatingTransformerService implements Transformer {

    private final GeminiRestTransformer geminiTransformer;
    private final RecipeValidationService validationService;
    private final RecipePostProcessor postProcessor;
    @Value("${recipe.llm.retry:1}")
    private int maxRetries;

    public ValidatingTransformerService(
            @Qualifier("geminiTransformer") GeminiRestTransformer geminiTransformer,
            RecipeValidationService validationService,
            RecipePostProcessor postProcessor) {
        this.geminiTransformer = geminiTransformer;
        this.validationService = validationService;
        this.postProcessor = postProcessor;
    }

    /**
     * Transforms HTML content to validated Recipe object.
     * If validation fails, retries with feedback up to max-retries times.
     * <p>
     * When recipe.llm.retry = 0, validation is skipped entirely and the raw Recipe is returned.
     * When recipe.llm.retry > 0, validation is performed with the specified number of retry attempts.
     *
     * @param htmlContent the HTML string to transform
     * @param sourceUrl   the source URL of the recipe
     * @return the transformed and validated result
     */
    @Override
    public Response transform(String htmlContent, String sourceUrl) {
        Response initialResponse = geminiTransformer.transform(htmlContent, sourceUrl);

        // If it's not a recipe, return immediately without validation
        if (!initialResponse.isRecipe()) {
            log.warn("Gemini initial response: Content identified as NOT a recipe - skipping validation");
            return initialResponse;
        }

        // If maxRetries is 0, skip validation entirely and return raw Recipe
        if (maxRetries == 0) {
            log.info("Validation disabled (recipe.llm.retry=0), returning raw Recipe without validation");
            Recipe postProcessedRecipe = postProcessor.process(initialResponse.recipe(), sourceUrl);
            return Response.withRawResponse(true, postProcessedRecipe, initialResponse.rawLlmResponse());
        }

        // Validate and potentially retry
        return validateWithRetry(htmlContent, initialResponse, sourceUrl);
    }

    private Response validateWithRetry(String htmlContent, Response initialResponse, String sourceUrl) {
        Recipe currentRecipe = initialResponse.recipe();
        String rawLlmResponse = initialResponse.rawLlmResponse(); // Preserve raw response
        RecipeValidationService.ValidationResult validationResult = validationService.validate(currentRecipe);

        if (validationResult.isValid()) {
            log.info("Recipe validated successfully on first attempt - Title: '{}'",
                    currentRecipe.metadata() != null ? currentRecipe.metadata().title() : "N/A");
            Recipe postProcessedRecipe = postProcessor.process(validationResult.getRecipe(), sourceUrl);
            return Response.withRawResponse(true, postProcessedRecipe, rawLlmResponse);
        }

        log.warn("Initial Recipe failed validation - Will retry up to {} times. Error:\n{}",
                maxRetries,
                validationResult.getErrorMessage());
        logRecipeDetails(currentRecipe, "initial validation failure");

        // Retry with feedback
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String errorPreview = validationResult.getErrorMessage().substring(
                    0, Math.min(200, validationResult.getErrorMessage().length())
            );
            log.info("Retry attempt {}/{} - Sending validation feedback to Gemini: {}{}",
                    attempt,
                    maxRetries,
                    errorPreview,
                    validationResult.getErrorMessage().length() > 200 ? "..." : "");

            Response retryResponse = geminiTransformer.transformWithFeedback(
                    htmlContent,
                    currentRecipe,
                    validationResult.getErrorMessage()
            );

            // If the model decides it's not a recipe after feedback, respect that
            if (!retryResponse.isRecipe()) {
                log.warn("Gemini changed decision after validation feedback (attempt {}/{}): Content is NOT a recipe",
                        attempt,
                        maxRetries);
                return retryResponse;
            }

            currentRecipe = retryResponse.recipe();
            validationResult = validationService.validate(currentRecipe);

            if (validationResult.isValid()) {
                log.info("Recipe validated successfully after {} retry attempt(s) - Title: '{}'",
                        attempt,
                        currentRecipe.metadata() != null ? currentRecipe.metadata().title() : "N/A");
                // Preserve raw response from the retry attempt
                Recipe postProcessedRecipe = postProcessor.process(validationResult.getRecipe(), sourceUrl);
                return Response.withRawResponse(true, postProcessedRecipe, retryResponse.rawLlmResponse());
            }

            log.warn("Retry attempt {}/{} failed validation:\n{}", attempt, maxRetries, validationResult.getErrorMessage());
            logRecipeDetails(currentRecipe, "retry attempt " + attempt);
        }

        // All retries exhausted, return as non-recipe since validation failed
        log.error("VALIDATION FAILED after {} retry attempt(s). Final error:\n{}",
                maxRetries,
                validationResult.getErrorMessage());
        log.error("Recipe that failed all validation attempts - Title: '{}'",
                currentRecipe.metadata() != null ? currentRecipe.metadata().title() : "N/A");
        logRecipeDetails(currentRecipe, "all validation attempts");

        // Return as non-recipe since validation failed
        return Response.notRecipe();
    }

    /**
     * Logs detailed information about a Recipe for debugging purposes.
     * Includes conversion to YAML for inspection.
     */
    private void logRecipeDetails(Recipe recipe, String context) {
        try {
            String yamlContent = validationService.toYaml(recipe);
            log.error("Recipe YAML that failed {} (first 500 chars):\n{}{}",
                    context,
                    yamlContent.substring(0, Math.min(500, yamlContent.length())),
                    yamlContent.length() > 500 ? "\n... (truncated)" : "");
        } catch (RecipeSerializeException e) {
            log.error("Failed to serialize Recipe to YAML for logging ({}): {}", context, e.getMessage());
            log.error("Recipe object toString: {}", recipe);
        }
    }
}