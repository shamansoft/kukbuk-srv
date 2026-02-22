package net.shamansoft.cookbook.service.gemini;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiRestTransformerTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private RequestBuilder requestBuilder;

    private GeminiRestTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new GeminiRestTransformer(geminiClient, requestBuilder);
    }

    private Recipe createTestRecipe(String title) {
        RecipeMetadata metadata = new RecipeMetadata(
                title,
                "https://example.com",
                "Test Author",
                "en",
                null,
                null,
                null,
                4,
                null,
                null,
                null,
                null,
                null
        );

        return new Recipe(
                null,  // is_recipe not set in recipes[] items (comes from wrapper)
                "1.0.0",
                "1.0.0",
                metadata,
                null,
                List.of(new Ingredient("Sugar", "1", "cup", null, null, null, null)),
                null,
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                null,
                null
        );
    }

    private GeminiExtractionResult recipeResult(String title) {
        return new GeminiExtractionResult(true, 0.95, null, List.of(createTestRecipe(title)));
    }

    private GeminiExtractionResult nonRecipeResult(double confidence) {
        return new GeminiExtractionResult(false, confidence, "Not a recipe page.", List.of());
    }

    @Test
    void transformSuccessfullyReturnsRecipe() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);
        GeminiExtractionResult extraction = recipeResult("Test Recipe");

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(extraction, "{\"is_recipe\": true}"));

        // When
        Transformer.Response response = transformer.transform(htmlContent, "https://example.com");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.confidence()).isEqualTo(0.95);
        assertThat(response.recipes()).hasSize(1);
        assertThat(response.recipe().metadata().title()).isEqualTo("Test Recipe");
        assertThat(response.recipe().isRecipe()).isTrue();  // injected by transformer

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient).request(eq(mockRequest), eq(GeminiExtractionResult.class));
    }

    @Test
    void transformReturnsNonRecipeWhenIsRecipeIsFalse() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Not a recipe</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(nonRecipeResult(0.1), "{\"is_recipe\": false}"));

        // When
        Transformer.Response response = transformer.transform(htmlContent, "https://example.com");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isFalse();
        assertThat(response.confidence()).isEqualTo(0.1);
        assertThat(response.recipes()).isEmpty();
        assertThat(response.recipe()).isNull();
    }

    @Test
    void transformReturnsLowConfidenceWhenHTMLMightBeOverCleaned() throws JacksonException {
        // Given: LLM thinks it's not a recipe but with mid confidence (over-cleaned HTML)
        String htmlContent = "<html><body>Some stripped content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(nonRecipeResult(0.65), "{\"is_recipe\": false, \"recipe_confidence\": 0.65}"));

        // When
        Transformer.Response response = transformer.transform(htmlContent, "https://example.com");

        // Then
        assertThat(response.isRecipe()).isFalse();
        assertThat(response.confidence()).isEqualTo(0.65);  // adaptive loop can retry above 0.5
    }

    @Test
    void transformReturnsMultipleRecipes() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Multi-recipe page</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);
        GeminiExtractionResult extraction = new GeminiExtractionResult(
                true, 0.99, null,
                List.of(createTestRecipe("Pasta Sauce"), createTestRecipe("Pizza Dough"))
        );

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(extraction, "{\"is_recipe\": true}"));

        // When
        Transformer.Response response = transformer.transform(htmlContent, "https://example.com");

        // Then
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipes()).hasSize(2);
        assertThat(response.recipes().get(0).metadata().title()).isEqualTo("Pasta Sauce");
        assertThat(response.recipes().get(1).metadata().title()).isEqualTo("Pizza Dough");
        // Both should have isRecipe=true injected
        assertThat(response.recipes()).allMatch(Recipe::isRecipe);
    }

    @Test
    void transformWithFeedbackUsesCorrectRequestBuilder() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe previousRecipe = createTestRecipe("Previous Recipe");
        String validationError = "Missing required field: instructions";

        GeminiRequest mockRequest = mock(GeminiRequest.class);
        GeminiExtractionResult correctedResult = recipeResult("Corrected Recipe");

        when(requestBuilder.buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError)))
                .thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(correctedResult, "{\"is_recipe\": true}"));

        // When
        Transformer.Response response = transformer.transformWithFeedback(htmlContent, previousRecipe, validationError);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipe().metadata().title()).isEqualTo("Corrected Recipe");

        verify(requestBuilder).buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError));
        verify(requestBuilder, never()).buildRequest(anyString());
        verify(geminiClient).request(eq(mockRequest), eq(GeminiExtractionResult.class));
    }

    @Test
    void transformThrowsExceptionWhenGeminiReturnsBlockedCode() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.failure(GeminiResponse.Code.BLOCKED, "Content blocked by safety filter"));

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent, "https://example.com"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Gemini Client returned error code: BLOCKED");

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient).request(eq(mockRequest), eq(GeminiExtractionResult.class));
    }

    @Test
    void transformThrowsExceptionWhenGeminiReturnsOtherErrorCode() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.failure(GeminiResponse.Code.OTHER, "Network error"));

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent, "https://example.com"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Gemini Client returned error code: OTHER");
    }

    @Test
    void transformThrowsExceptionWhenRequestBuilderFailsWithJacksonException() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";

        when(requestBuilder.buildRequest(eq(htmlContent)))
                .thenThrow(new JacksonException("Failed to serialize request") {
                });

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent, "https://example.com"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API")
                .hasCauseInstanceOf(JacksonException.class);

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient, never()).request(any(), any());
    }

    @Test
    void transformWithFeedbackThrowsExceptionWhenRequestBuilderFails() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe previousRecipe = createTestRecipe("Test");
        String validationError = "Error";

        when(requestBuilder.buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError)))
                .thenThrow(new JacksonException("Failed to serialize") {
                });

        // When/Then
        assertThatThrownBy(() -> transformer.transformWithFeedback(htmlContent, previousRecipe, validationError))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API");

        verify(geminiClient, never()).request(any(), any());
    }

    @Test
    void transformHandlesComplexRecipe() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Complex recipe</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        RecipeMetadata metadata = new RecipeMetadata(
                "Complex Recipe",
                "https://example.com",
                null,
                null,
                null,
                null,
                null,
                4,
                null,
                null,
                null,
                null,
                null
        );

        List<Ingredient> ingredients = List.of(
                new Ingredient("Flour", "2", "cups", null, null, null, null),
                new Ingredient("Sugar", "1", "cup", null, null, null, null),
                new Ingredient("Eggs", "3", null, null, null, null, null)
        );

        Recipe complexRecipe = new Recipe(null, "1.0.0", "1.0.0", metadata, null, ingredients, null,
                List.of(new Instruction(1, "Mix ingredients", null, null, null)), null, null, null);

        GeminiExtractionResult extraction = new GeminiExtractionResult(true, 0.98, null, List.of(complexRecipe));

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(GeminiExtractionResult.class)))
                .thenReturn(GeminiResponse.success(extraction, "{\"is_recipe\": true}"));

        // When
        Transformer.Response response = transformer.transform(htmlContent, "https://example.com");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipe().ingredients()).hasSize(3);
        assertThat(response.recipe().ingredients().get(0).item()).isEqualTo("Flour");
    }
}
