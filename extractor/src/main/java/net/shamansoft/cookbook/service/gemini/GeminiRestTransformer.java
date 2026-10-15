package net.shamansoft.cookbook.service.gemini;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.exception.ExtractionFailedException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.List;

@Service("geminiTransformer")
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final GeminiClient geminiClient;
    private final RequestBuilder requestBuilder;

    // One retry when Gemini returns no content or cut-off output. At the configured temperature (0) the
    // same request gives the same answer, so the retry samples a little and allows more output.
    @Value("${cookbook.gemini.retry.temperature:0.3}")
    private float retryTemperature = 0.3f;
    @Value("${cookbook.gemini.retry.max-output-tokens:16384}")
    private int retryMaxOutputTokens = 16384;

    @Override
    public Response transform(String htmlContent, String sourceUrl) {
        return transformInternal(htmlContent, null, null);
    }

    /**
     * Transforms HTML content to Recipes with validation feedback from a previous attempt.
     *
     * @param htmlContent     the HTML string to transform
     * @param previousRecipe  the first failing Recipe from the previous attempt (used for feedback)
     * @param validationError the validation error message to help correct the issue
     * @return the transformed result
     */
    public Response transformWithFeedback(String htmlContent, Recipe previousRecipe, String validationError) {
        return transformInternal(htmlContent, previousRecipe, validationError);
    }

    /**
     * Structures a user-provided recipe description into a Recipe object.
     *
     * @param description free-form text describing the recipe
     * @return the transformed result (always isRecipe=true for user-authored content)
     */
    public Response transformDescription(String description) {
        try {
            GeminiRequest request = requestBuilder.buildRequestFromDescription(description);
            log.debug("Transforming recipe description, length: {}", description.length());

            GeminiResponse<GeminiExtractionResult> geminiResponse = requestWithRetry(request);

            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                GeminiExtractionResult result = geminiResponse.data();
                List<Recipe> recipes = toRecipes(result.recipes());
                log.debug("Description transformation returned {} recipe(s)", recipes.size());
                return Transformer.Response.withRawResponse(true, 1.0, recipes, geminiResponse.rawResponse());
            } else {
                log.error("Gemini Client returned error code: {} for description input", geminiResponse.code());
                throw failure(geminiResponse);
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare request for Gemini API. Description length: {}, Error: {}",
                    description.length(), e.getMessage(), e);
            throw new ClientException("Failed to transform description via Gemini API", e);
        }
    }

    /**
     * Debug-only entry point for parameter tuning: transforms HTML directly via Gemini with
     * overridden generation parameters, bypassing the adaptive-cleaning/validation retry chain
     * so a single call reflects exactly the given parameters.
     */
    public Response transformWithOverrides(String htmlContent, GenerationOverrides overrides) {
        try {
            GeminiRequest request = requestBuilder.buildRequest(htmlContent, overrides);
            GeminiResponse<GeminiExtractionResult> geminiResponse =
                    geminiClient.request(request, GeminiExtractionResult.class,
                            overrides != null ? overrides.model() : null);

            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                GeminiExtractionResult result = geminiResponse.data();
                double confidence = result.recipeConfidence();

                if (!result.isRecipe()) {
                    return Transformer.Response.withRawResponse(false, confidence, List.of(), geminiResponse.rawResponse());
                }

                List<Recipe> recipes = toRecipes(result.recipes());
                return Transformer.Response.withRawResponse(true, confidence, recipes, geminiResponse.rawResponse());
            } else {
                log.error("Gemini Client returned error code: {} for tuning request, input html length: {}",
                        geminiResponse.code(), htmlContent.length());
                throw new ClientException("Gemini Client returned error code: " + geminiResponse.code());
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare tuning request for Gemini API. HTML length: {}, Error: {}",
                    htmlContent.length(), e.getMessage(), e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
    }

    /**
     * Debug-only entry point for parameter tuning of the free-text description flow.
     */
    public Response transformDescriptionWithOverrides(String description, GenerationOverrides overrides) {
        try {
            GeminiRequest request = requestBuilder.buildRequestFromDescription(description, overrides);
            GeminiResponse<GeminiExtractionResult> geminiResponse =
                    geminiClient.request(request, GeminiExtractionResult.class,
                            overrides != null ? overrides.model() : null);

            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                GeminiExtractionResult result = geminiResponse.data();
                List<Recipe> recipes = toRecipes(result.recipes());
                return Transformer.Response.withRawResponse(true, 1.0, recipes, geminiResponse.rawResponse());
            } else {
                log.error("Gemini Client returned error code: {} for tuning description input", geminiResponse.code());
                throw new ClientException("Gemini Client returned error code: " + geminiResponse.code());
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare tuning request for Gemini API. Description length: {}, Error: {}",
                    description.length(), e.getMessage(), e);
            throw new ClientException("Failed to transform description via Gemini API", e);
        }
    }

    private Response transformInternal(String htmlContent, Recipe previousRecipe, String validationError) {
        try {
            GeminiRequest request;
            if (previousRecipe != null && validationError != null) {
                request = requestBuilder.buildRequest(htmlContent, previousRecipe, validationError);
                log.debug("Retrying transformation with validation feedback");
            } else {
                request = requestBuilder.buildRequest(htmlContent);
                log.debug("Initial transformation request.");
            }

            GeminiResponse<GeminiExtractionResult> geminiResponse = requestWithRetry(request);

            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                GeminiExtractionResult result = geminiResponse.data();
                double confidence = result.recipeConfidence();

                if (!result.isRecipe()) {
                    log.debug("Gemini returned is_recipe=false with confidence={}", confidence);
                    return Transformer.Response.withRawResponse(false, confidence, List.of(), geminiResponse.rawResponse());
                }

                List<Recipe> recipes = toRecipes(result.recipes());
                log.debug("Gemini returned {} recipe(s) with confidence={}", recipes.size(), confidence);
                return Transformer.Response.withRawResponse(true, confidence, recipes, geminiResponse.rawResponse());

            } else {
                log.error("Gemini Client returned error code: {}, input html length: {}",
                        geminiResponse.code(), htmlContent.length());
                throw failure(geminiResponse);
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare request for Gemini API. HTML length: {}, Error: {}",
                    htmlContent.length(), e.getMessage(), e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
    }

    /**
     * Sends the request; on an empty or cut-off answer sends it once more with a little sampling
     * temperature and a larger output budget (a deterministic repetition loop or recitation stop
     * would otherwise repeat exactly).
     */
    private GeminiResponse<GeminiExtractionResult> requestWithRetry(GeminiRequest request) {
        GeminiResponse<GeminiExtractionResult> response = geminiClient.request(request, GeminiExtractionResult.class);
        if (!response.code().isRetryable()) {
            return response;
        }
        GeminiRequest.GenerationConfig config = request.getGenerationConfig();
        if (config != null) {
            config.setTemperature(Math.max(config.getTemperature(), retryTemperature));
            config.setMaxOutputTokens(Math.max(config.getMaxOutputTokens(), retryMaxOutputTokens));
        }
        log.warn("Gemini returned {} ({}), retrying once with temperature={} maxOutputTokens={}",
                response.code(), response.errorMessage(),
                config != null ? config.getTemperature() : null, config != null ? config.getMaxOutputTokens() : null);
        return geminiClient.request(request, GeminiExtractionResult.class);
    }

    private static ClientException failure(GeminiResponse<?> response) {
        if (response.code().isRetryable()) {
            return new ExtractionFailedException(
                    "The AI model could not extract a recipe from this page: " + response.errorMessage());
        }
        return new ClientException("Gemini Client returned error code: " + response.code());
    }

    /**
     * Converts deserialized Recipe objects from the GeminiExtractionResult to proper Recipe records.
     * The items in the recipes[] array don't carry is_recipe (it's on the wrapper), so we inject true.
     */
    private List<Recipe> toRecipes(List<Recipe> rawRecipes) {
        if (rawRecipes == null || rawRecipes.isEmpty()) {
            return List.of();
        }
        return rawRecipes.stream()
                .filter(r -> r != null)
                .map(r -> new Recipe(
                        true,
                        r.schemaVersion(),
                        r.recipeVersion(),
                        r.metadata(),
                        r.description(),
                        r.ingredients(),
                        r.equipment(),
                        r.instructions(),
                        r.nutrition(),
                        r.notes(),
                        r.storage()
                ))
                .toList();
    }
}
