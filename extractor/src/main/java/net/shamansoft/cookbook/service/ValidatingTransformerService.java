package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that combines recipe transformation with validation and retry logic.
 * When Gemini produces invalid Recipe objects, it retries with validation feedback.
 * Handles multiple recipes per page — validates each one independently.
 * Invalid recipes are either retried (if all fail) or dropped from the result (partial success).
 */
@Service
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
     * Transforms HTML content to validated Recipe objects.
     * If validation fails, retries with feedback up to max-retries times.
     *
     * <p>When recipe.llm.retry = 0, validation is skipped and raw Recipes are returned.
     * When recipe.llm.retry > 0, each recipe is validated; invalid ones trigger a retry.
     *
     * @param htmlContent the HTML string to transform
     * @param sourceUrl   the source URL of the recipe
     * @return the transformed and validated result (may contain multiple recipes)
     */
    @Override
    public Response transform(String htmlContent, String sourceUrl) {
        Response initialResponse = geminiTransformer.transform(htmlContent, sourceUrl);

        if (!initialResponse.isRecipe()) {
            log.warn("Gemini initial response: Content identified as NOT a recipe (confidence={}) - skipping validation",
                    initialResponse.confidence());
            return initialResponse;
        }

        if (maxRetries == 0) {
            log.info("Validation disabled (recipe.llm.retry=0), returning raw Recipes without validation");
            List<Recipe> processed = initialResponse.recipes().stream()
                    .map(r -> postProcessor.process(r, sourceUrl))
                    .toList();
            return Response.withRawResponse(true, initialResponse.confidence(), processed, initialResponse.rawLlmResponse());
        }

        return validateWithRetry(htmlContent, initialResponse, sourceUrl);
    }

    private Response validateWithRetry(String htmlContent, Response initialResponse, String sourceUrl) {
        List<Recipe> currentRecipes = initialResponse.recipes();
        double confidence = initialResponse.confidence();
        String rawLlmResponse = initialResponse.rawLlmResponse();

        ValidationBatch batch = validateAll(currentRecipes);

        if (batch.allValid()) {
            List<Recipe> processed = batch.validRecipes().stream()
                    .map(r -> postProcessor.process(r, sourceUrl))
                    .toList();
            logSuccess(processed, 0);
            return Response.withRawResponse(true, confidence, processed, rawLlmResponse);
        }

        log.warn("Initial Recipe(s) failed validation - Will retry up to {} times. Errors:\n{}",
                maxRetries, batch.allErrors());
        logRecipeDetails(currentRecipes.get(0), "initial validation failure");

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String errorPreview = batch.allErrors().substring(0, Math.min(200, batch.allErrors().length()));
            log.info("Retry attempt {}/{} - Sending validation feedback: {}{}",
                    attempt, maxRetries, errorPreview,
                    batch.allErrors().length() > 200 ? "..." : "");

            // Use first failing recipe for feedback
            Recipe firstFailing = batch.firstInvalidRecipe(currentRecipes);
            Response retryResponse = geminiTransformer.transformWithFeedback(
                    htmlContent, firstFailing, batch.allErrors());

            if (!retryResponse.isRecipe()) {
                log.warn("Gemini changed decision after validation feedback (attempt {}/{}): Content is NOT a recipe",
                        attempt, maxRetries);
                return retryResponse;
            }

            currentRecipes = retryResponse.recipes();
            confidence = retryResponse.confidence();
            rawLlmResponse = retryResponse.rawLlmResponse();
            batch = validateAll(currentRecipes);

            if (batch.allValid()) {
                List<Recipe> processed = batch.validRecipes().stream()
                        .map(r -> postProcessor.process(r, sourceUrl))
                        .toList();
                logSuccess(processed, attempt);
                return Response.withRawResponse(true, confidence, processed, rawLlmResponse);
            }

            log.warn("Retry attempt {}/{} failed validation:\n{}", attempt, maxRetries, batch.allErrors());
            logRecipeDetails(currentRecipes.get(0), "retry attempt " + attempt);
        }

        // Partial success: if some recipes are valid after all retries, return them
        if (!batch.validRecipes().isEmpty()) {
            log.warn("PARTIAL SUCCESS after {} retries: {}/{} recipes valid. Returning valid subset.",
                    maxRetries, batch.validRecipes().size(), currentRecipes.size());
            List<Recipe> processed = batch.validRecipes().stream()
                    .map(r -> postProcessor.process(r, sourceUrl))
                    .toList();
            return Response.withRawResponse(true, confidence, processed, rawLlmResponse);
        }

        log.error("VALIDATION FAILED after {} retry attempt(s). Final error:\n{}", maxRetries, batch.allErrors());
        return Response.notRecipe(confidence);
    }

    private ValidationBatch validateAll(List<Recipe> recipes) {
        List<RecipeValidationService.ValidationResult> results = recipes.stream()
                .map(validationService::validate)
                .toList();

        List<Recipe> valid = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        boolean multipleRecipes = recipes.size() > 1;
        for (int i = 0; i < recipes.size(); i++) {
            RecipeValidationService.ValidationResult vr = results.get(i);
            if (vr.isValid()) {
                valid.add(vr.getRecipe());
            } else if (multipleRecipes) {
                String recipeTitle = recipes.get(i).metadata() != null
                        ? recipes.get(i).metadata().title() : "Recipe #" + (i + 1);
                errors.add("[" + recipeTitle + "] " + vr.getErrorMessage());
            } else {
                errors.add(vr.getErrorMessage());
            }
        }

        return new ValidationBatch(valid, errors, recipes);
    }

    private void logSuccess(List<Recipe> recipes, int attempt) {
        String titles = recipes.stream()
                .map(r -> r.metadata() != null ? r.metadata().title() : "N/A")
                .collect(Collectors.joining(", "));
        if (attempt == 0) {
            log.info("Recipe(s) validated successfully on first attempt - Title(s): '{}'", titles);
        } else {
            log.info("Recipe(s) validated successfully after {} retry attempt(s) - Title(s): '{}'", attempt, titles);
        }
    }

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

    /**
     * Groups validation results for a batch of recipes.
     */
    private record ValidationBatch(
            List<Recipe> validRecipes,
            List<String> errorMessages,
            List<Recipe> allRecipes
    ) {
        boolean allValid() {
            return errorMessages.isEmpty();
        }

        String allErrors() {
            return String.join("\n", errorMessages);
        }

        Recipe firstInvalidRecipe(List<Recipe> recipes) {
            for (int i = 0; i < recipes.size(); i++) {
                if (i >= validRecipes.size() || !validRecipes.contains(recipes.get(i))) {
                    return recipes.get(i);
                }
            }
            return recipes.get(0);
        }
    }
}
