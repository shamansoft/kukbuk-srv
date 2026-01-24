package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    private Recipe createTestRecipe(String title, boolean isRecipe) {
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

        List<Ingredient> ingredients = List.of(
                new Ingredient("Sugar", "1", "cup", null, null, null, null)
        );

        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null)
        );

        return new Recipe(
                isRecipe,
                "1.0.0",
                "1.0.0",
                metadata,
                null,
                ingredients,
                null,
                instructions,
                null,
                null,
                null
        );
    }

    @Test
    void transformSuccessfullyReturnsRecipe() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);
        Recipe expectedRecipe = createTestRecipe("Test Recipe", true);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.success(expectedRecipe));

        // When
        Transformer.Response response = transformer.transform(htmlContent);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipe()).isEqualTo(expectedRecipe);
        assertThat(response.recipe().metadata().title()).isEqualTo("Test Recipe");

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient).request(eq(mockRequest), eq(Recipe.class));
    }

    @Test
    void transformReturnsNonRecipeWhenIsRecipeIsFalse() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Not a recipe</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);
        Recipe nonRecipe = createTestRecipe("Not a recipe", false);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.success(nonRecipe));

        // When
        Transformer.Response response = transformer.transform(htmlContent);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isFalse();
        assertThat(response.recipe()).isEqualTo(nonRecipe);
    }

    @Test
    void transformWithFeedbackUsesCorrectRequestBuilder() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe previousRecipe = createTestRecipe("Previous Recipe", true);
        String validationError = "Missing required field: instructions";

        GeminiRequest mockRequest = mock(GeminiRequest.class);
        Recipe correctedRecipe = createTestRecipe("Corrected Recipe", true);

        when(requestBuilder.buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError)))
                .thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.success(correctedRecipe));

        // When
        Transformer.Response response = transformer.transformWithFeedback(htmlContent, previousRecipe, validationError);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipe()).isEqualTo(correctedRecipe);

        verify(requestBuilder).buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError));
        verify(requestBuilder, never()).buildRequest(anyString());
        verify(geminiClient).request(eq(mockRequest), eq(Recipe.class));
    }

    @Test
    void transformThrowsExceptionWhenGeminiReturnsBlockedCode() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.failure(GeminiResponse.Code.BLOCKED, "Content blocked by safety filter"));

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Gemini Client returned error code: BLOCKED");

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient).request(eq(mockRequest), eq(Recipe.class));
    }

    @Test
    void transformThrowsExceptionWhenGeminiReturnsOtherErrorCode() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        GeminiRequest mockRequest = mock(GeminiRequest.class);

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.failure(GeminiResponse.Code.OTHER, "Network error"));

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Gemini Client returned error code: OTHER");
    }

    @Test
    void transformThrowsExceptionWhenRequestBuilderFailsWithJsonProcessingException() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";

        when(requestBuilder.buildRequest(eq(htmlContent)))
                .thenThrow(new JsonProcessingException("Failed to serialize request") {});

        // When/Then
        assertThatThrownBy(() -> transformer.transform(htmlContent))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(requestBuilder).buildRequest(eq(htmlContent));
        verify(geminiClient, never()).request(any(), any());
    }

    @Test
    void transformWithFeedbackThrowsExceptionWhenRequestBuilderFails() throws JsonProcessingException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe previousRecipe = createTestRecipe("Test", true);
        String validationError = "Error";

        when(requestBuilder.buildRequest(eq(htmlContent), eq(previousRecipe), eq(validationError)))
                .thenThrow(new JsonProcessingException("Failed to serialize") {});

        // When/Then
        assertThatThrownBy(() -> transformer.transformWithFeedback(htmlContent, previousRecipe, validationError))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to transform content via Gemini API");

        verify(geminiClient, never()).request(any(), any());
    }

    @Test
    void transformHandlesComplexRecipe() throws JsonProcessingException {
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

        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null)
        );

        Recipe complexRecipe = new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                metadata,
                null,
                ingredients,
                null,
                instructions,
                null,
                null,
                null
        );

        when(requestBuilder.buildRequest(eq(htmlContent))).thenReturn(mockRequest);
        when(geminiClient.request(eq(mockRequest), eq(Recipe.class)))
                .thenReturn(GeminiResponse.success(complexRecipe));

        // When
        Transformer.Response response = transformer.transform(htmlContent);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.recipe().ingredients()).hasSize(3);
        assertThat(response.recipe().ingredients().get(0).item()).isEqualTo("Flour");
    }
}
