package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Service that combines recipe transformation with validation and retry logic.
 * When Gemini produces invalid YAML, it retries with validation feedback.
 * This is the primary Transformer implementation that should be used by controllers.
 */
@Service
@Primary
@Slf4j
public class ValidatingTransformerService implements Transformer {

    private final GeminiRestTransformer geminiTransformer;
    private final RecipeValidationService validationService;

    public ValidatingTransformerService(
            @Qualifier("geminiTransformer") GeminiRestTransformer geminiTransformer,
            RecipeValidationService validationService) {
        this.geminiTransformer = geminiTransformer;
        this.validationService = validationService;
    }

    @Value("${recipe.llm.retry:1}")
    private int maxRetries;

    /**
     * Transforms HTML content to validated and normalized recipe YAML.
     * If validation fails, retries with feedback up to max-retries times.
     *
     * When recipe.llm.retry = 0, validation is skipped entirely and raw YAML is returned.
     * When recipe.llm.retry > 0, validation is performed with the specified number of retry attempts.
     *
     * @param htmlContent the HTML string to transform
     * @return the transformed and validated result
     */
    @Override
    public Response transform(String htmlContent) {
        Response initialResponse = geminiTransformer.transform(htmlContent);

        // If it's not a recipe, return immediately without validation
        if (!initialResponse.isRecipe()) {
            log.info("Content identified as not a recipe, skipping validation");
            return initialResponse;
        }

        // If maxRetries is 0, skip validation entirely and return raw YAML
        if (maxRetries == 0) {
            log.info("Validation disabled (recipe.llm.retry=0), returning raw YAML");
            return initialResponse;
        }

        // Validate and potentially retry
        return validateWithRetry(htmlContent, initialResponse);
    }

    private Response validateWithRetry(String htmlContent, Response initialResponse) {
        String currentYaml = initialResponse.value();
        RecipeValidationService.ValidationResult validationResult = validationService.validate(currentYaml);

        if (validationResult.isValid()) {
            log.info("Recipe YAML validated successfully on first attempt");
            return new Response(true, validationResult.getNormalizedYaml());
        }

        log.warn("Initial recipe YAML failed validation: {}", validationResult.getErrorMessage());

        // Retry with feedback
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Retrying transformation with validation feedback (attempt {}/{})", attempt, maxRetries);

            Response retryResponse = geminiTransformer.transformWithFeedback(
                    htmlContent,
                    currentYaml,
                    validationResult.getErrorMessage()
            );

            // If the model decides it's not a recipe after feedback, respect that
            if (!retryResponse.isRecipe()) {
                log.info("Model determined content is not a recipe after validation feedback");
                return retryResponse;
            }

            currentYaml = retryResponse.value();
            validationResult = validationService.validate(currentYaml);

            if (validationResult.isValid()) {
                log.info("Recipe YAML validated successfully after {} retry attempts", attempt);
                return new Response(true, validationResult.getNormalizedYaml());
            }

            log.warn("Retry attempt {} failed validation: {}", attempt, validationResult.getErrorMessage());
        }

        // All retries exhausted, return the last attempt with warning
        log.error("Failed to produce valid recipe YAML after {} attempts. Returning last attempt.", maxRetries);
        // Return as non-recipe since validation failed
        return new Response(false, currentYaml);
    }
}
