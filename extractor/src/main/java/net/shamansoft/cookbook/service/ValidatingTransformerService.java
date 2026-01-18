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
            log.warn("Gemini initial response: Content identified as NOT a recipe - skipping validation");
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

        log.warn("Initial recipe YAML failed validation - Will retry up to {} times. Error: {}",
            maxRetries,
            validationResult.getErrorMessage());
        log.error("Full YAML content that failed initial validation:\n{}", currentYaml);

        // Retry with feedback
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Retry attempt {}/{} - Sending validation feedback to Gemini: {}",
                attempt,
                maxRetries,
                validationResult.getErrorMessage().substring(0, Math.min(200, validationResult.getErrorMessage().length())));

            Response retryResponse = geminiTransformer.transformWithFeedback(
                    htmlContent,
                    currentYaml,
                    validationResult.getErrorMessage()
            );

            // If the model decides it's not a recipe after feedback, respect that
            if (!retryResponse.isRecipe()) {
                log.warn("Gemini changed decision after validation feedback (attempt {}/{}): Content is NOT a recipe",
                    attempt,
                    maxRetries);
                return retryResponse;
            }

            currentYaml = retryResponse.value();
            validationResult = validationService.validate(currentYaml);

            if (validationResult.isValid()) {
                log.info("Recipe YAML validated successfully after {} retry attempts", attempt);
                return new Response(true, validationResult.getNormalizedYaml());
            }

            log.warn("Retry attempt {} failed validation: {}", attempt, validationResult.getErrorMessage());
            log.error("Full YAML content that failed validation (attempt {}):\n{}", attempt, currentYaml);
        }

        // All retries exhausted, return the last attempt with warning
        log.error("VALIDATION FAILED after {} retry attempts. Final error: {}. Returning as NOT a recipe.",
            maxRetries,
            validationResult.getErrorMessage());
        log.error("Full YAML content that failed all validation attempts:\n{}", currentYaml);
        // Return as non-recipe since validation failed
        return new Response(false, currentYaml);
    }
}
