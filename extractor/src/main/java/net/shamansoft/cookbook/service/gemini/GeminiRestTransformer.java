package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.stereotype.Service;

@Service("geminiTransformer")
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final GeminiClient geminiClient;
    private final RequestBuilder requestBuilder;

    @Override
    public Response transform(String htmlContent) {
        return transformInternal(htmlContent, null, null);
    }

    /**
     * Transforms HTML content to Recipe with validation feedback from a previous attempt.
     *
     * @param htmlContent the HTML string to transform
     * @param previousRecipe the Recipe from the previous attempt that failed validation
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
            GeminiResponse<Recipe> geminiResponse = geminiClient.request(request, Recipe.class);
            if (geminiResponse.code() == GeminiResponse.Code.SUCCESS) {
                return new Transformer.Response(geminiResponse.data().isRecipe(), geminiResponse.data());
            } else {
                log.error("Gemini Client returned error code: {}, input html length: {}",
                        geminiResponse.code(),
                        htmlContent.length());
                throw new ClientException("Gemini Client returned error code: " + geminiResponse.code());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to prepare request for Gemini API. HTML length: {}, Error: {}",
                htmlContent.length(),
                e.getMessage(),
                e);
            throw new ClientException("Failed to transform content via Gemini API", e);
        }
    }
}