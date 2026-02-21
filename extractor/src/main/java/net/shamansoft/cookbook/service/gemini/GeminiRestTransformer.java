package net.shamansoft.cookbook.service.gemini;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.List;

@Service("geminiTransformer")
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final GeminiClient geminiClient;
    private final RequestBuilder requestBuilder;

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

            GeminiResponse<GeminiExtractionResult> geminiResponse =
                    geminiClient.request(request, GeminiExtractionResult.class);

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
                throw new ClientException("Gemini Client returned error code: " + geminiResponse.code());
            }
        } catch (JacksonException e) {
            log.error("Failed to prepare request for Gemini API. HTML length: {}, Error: {}",
                    htmlContent.length(), e.getMessage(), e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
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
