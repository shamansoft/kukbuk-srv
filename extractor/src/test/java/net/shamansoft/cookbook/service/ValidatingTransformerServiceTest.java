package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidatingTransformerServiceTest {

    @Mock
    private GeminiRestTransformer geminiTransformer;

    @Mock
    private RecipeValidationService validationService;

    @Mock
    private RecipePostProcessor postProcessor;

    private ValidatingTransformerService validatingTransformer;

    @BeforeEach
    void setUp() {
        validatingTransformer = new ValidatingTransformerService(geminiTransformer, validationService, postProcessor);
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 3);
    }

    @Test
    void transform_whenNotRecipe_shouldReturnImmediatelyWithoutValidation() {
        // Given
        String html = "<html>Not a recipe</html>";
        String sourceUrl = "https://example.com/not-recipe";
        Transformer.Response notRecipeResponse = Transformer.Response.notRecipe();
        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(notRecipeResponse);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertFalse(result.isRecipe());
        assertNull(result.recipe());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verifyNoInteractions(validationService);
        verifyNoInteractions(postProcessor);
    }

    @Test
    void transform_whenValidRecipeOnFirstAttempt_shouldReturnValidatedRecipe() {
        // Given
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe initialRecipe = createValidRecipe("Test Recipe");
        Recipe postProcessedRecipe = createValidRecipe("Test Recipe"); // Simulating post-processed recipe
        Transformer.Response initialResponse = Transformer.Response.recipe(initialRecipe);
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(initialRecipe);

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(validationService.validate(initialRecipe)).thenReturn(validResult);
        when(postProcessor.process(initialRecipe, sourceUrl)).thenReturn(postProcessedRecipe);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertTrue(result.isRecipe());
        assertNotNull(result.recipe());
        assertEquals("Test Recipe", result.recipe().metadata().title());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verify(validationService, times(1)).validate(initialRecipe);
        verify(postProcessor, times(1)).process(initialRecipe, sourceUrl);
        verify(geminiTransformer, never()).transformWithFeedback(anyString(), any(Recipe.class), anyString());
    }

    @Test
    void transform_whenInvalidThenValidOnRetry_shouldReturnValidatedRecipe() throws Exception {
        // Given
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe invalidRecipe = createRecipeWithMissingFields();
        Recipe validRecipe = createValidRecipe("Fixed Recipe");
        Recipe postProcessedRecipe = createValidRecipe("Fixed Recipe"); // Simulating post-processed recipe

        Transformer.Response initialResponse = Transformer.Response.recipe(invalidRecipe);
        Transformer.Response retryResponse = Transformer.Response.recipe(validRecipe);

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Field 'metadata.title': must not be null");
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(validRecipe);

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(validationService.validate(invalidRecipe)).thenReturn(invalidResult);
        lenient().when(validationService.toYaml(invalidRecipe)).thenReturn("invalid yaml");
        when(geminiTransformer.transformWithFeedback(eq(html), eq(invalidRecipe), anyString()))
                .thenReturn(retryResponse);
        when(validationService.validate(validRecipe)).thenReturn(validResult);
        when(postProcessor.process(validRecipe, sourceUrl)).thenReturn(postProcessedRecipe);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertTrue(result.isRecipe());
        assertNotNull(result.recipe());
        assertEquals("Fixed Recipe", result.recipe().metadata().title());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verify(geminiTransformer, times(1)).transformWithFeedback(eq(html), eq(invalidRecipe), anyString());
        verify(validationService, times(2)).validate(any(Recipe.class));
        verify(postProcessor, times(1)).process(validRecipe, sourceUrl);
    }

    @Test
    void transform_whenAllRetriesFail_shouldReturnAsNonRecipe() throws Exception {
        // Given
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe invalidRecipe1 = createRecipeWithMissingFields();
        Recipe invalidRecipe2 = createRecipeWithMissingFields();
        Recipe invalidRecipe3 = createRecipeWithMissingFields();
        Recipe invalidRecipe4 = createRecipeWithMissingFields();

        Transformer.Response initialResponse = Transformer.Response.recipe(invalidRecipe1);
        Transformer.Response retry1Response = Transformer.Response.recipe(invalidRecipe2);
        Transformer.Response retry2Response = Transformer.Response.recipe(invalidRecipe3);
        Transformer.Response retry3Response = Transformer.Response.recipe(invalidRecipe4);

        RecipeValidationService.ValidationResult invalidResult1 =
                RecipeValidationService.ValidationResult.failure("Error 1");
        RecipeValidationService.ValidationResult invalidResult2 =
                RecipeValidationService.ValidationResult.failure("Error 2");
        RecipeValidationService.ValidationResult invalidResult3 =
                RecipeValidationService.ValidationResult.failure("Error 3");
        RecipeValidationService.ValidationResult invalidResult4 =
                RecipeValidationService.ValidationResult.failure("Error 4");

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(validationService.validate(any(Recipe.class))).thenReturn(invalidResult1, invalidResult2, invalidResult3, invalidResult4);
        lenient().when(validationService.toYaml(any(Recipe.class))).thenReturn("yaml1", "yaml2", "yaml3", "yaml4");
        when(geminiTransformer.transformWithFeedback(eq(html), any(Recipe.class), anyString()))
                .thenReturn(retry1Response, retry2Response, retry3Response);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertFalse(result.isRecipe(), "Should return as non-recipe after exhausting retries");
        assertNull(result.recipe());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verify(geminiTransformer, times(3)).transformWithFeedback(eq(html), any(Recipe.class), anyString());
        verify(validationService, times(4)).validate(any(Recipe.class));
        verifyNoInteractions(postProcessor); // Should not post-process when validation fails
    }

    @Test
    void transform_whenModelChangesToNonRecipeAfterFeedback_shouldRespectThat() throws Exception {
        // Given
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe invalidRecipe = createRecipeWithMissingFields();

        Transformer.Response initialResponse = Transformer.Response.recipe(invalidRecipe);
        Transformer.Response retryResponse = Transformer.Response.notRecipe();

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Error");

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(validationService.validate(invalidRecipe)).thenReturn(invalidResult);
        lenient().when(validationService.toYaml(invalidRecipe)).thenReturn("invalid yaml");
        when(geminiTransformer.transformWithFeedback(eq(html), eq(invalidRecipe), eq("Error")))
                .thenReturn(retryResponse);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertFalse(result.isRecipe());
        assertNull(result.recipe());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verify(geminiTransformer, times(1)).transformWithFeedback(eq(html), eq(invalidRecipe), eq("Error"));
        verify(validationService, times(1)).validate(invalidRecipe);
        verifyNoInteractions(postProcessor); // Should not post-process when model returns non-recipe
        // Should not validate the notRecipe result (only 1 validation call)
    }

    @Test
    void transform_whenRetriesSetToZero_shouldSkipValidationAndReturnRawRecipe() {
        // Given
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 0);
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe rawRecipe = createValidRecipe("Raw Recipe");
        Recipe postProcessedRecipe = createValidRecipe("Raw Recipe"); // Simulating post-processed recipe
        Transformer.Response initialResponse = Transformer.Response.recipe(rawRecipe);

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(postProcessor.process(rawRecipe, sourceUrl)).thenReturn(postProcessedRecipe);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertTrue(result.isRecipe(), "Should still be marked as recipe");
        assertNotNull(result.recipe());
        assertEquals("Raw Recipe", result.recipe().metadata().title());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verifyNoInteractions(validationService); // Validation should be completely skipped
        verify(postProcessor, times(1)).process(rawRecipe, sourceUrl); // But should still post-process
        verify(geminiTransformer, never()).transformWithFeedback(anyString(), any(Recipe.class), anyString());
    }

    @Test
    void transform_whenRetriesSetToOne_shouldValidateAndRetryOnce() throws Exception {
        // Given
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 1);
        String html = "<html>Recipe content</html>";
        String sourceUrl = "https://example.com/recipe";
        Recipe invalidRecipe = createRecipeWithMissingFields();
        Recipe validRecipe = createValidRecipe("Valid After Retry");
        Recipe postProcessedRecipe = createValidRecipe("Valid After Retry"); // Simulating post-processed recipe

        Transformer.Response initialResponse = Transformer.Response.recipe(invalidRecipe);
        Transformer.Response retryResponse = Transformer.Response.recipe(validRecipe);

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Missing required fields");
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(validRecipe);

        when(geminiTransformer.transform(html, sourceUrl)).thenReturn(initialResponse);
        when(validationService.validate(invalidRecipe)).thenReturn(invalidResult);
        lenient().when(validationService.toYaml(invalidRecipe)).thenReturn("invalid yaml");
        when(geminiTransformer.transformWithFeedback(eq(html), eq(invalidRecipe), eq("Missing required fields")))
                .thenReturn(retryResponse);
        when(validationService.validate(validRecipe)).thenReturn(validResult);
        when(postProcessor.process(validRecipe, sourceUrl)).thenReturn(postProcessedRecipe);

        // When
        Transformer.Response result = validatingTransformer.transform(html, sourceUrl);

        // Then
        assertTrue(result.isRecipe());
        assertNotNull(result.recipe());
        assertEquals("Valid After Retry", result.recipe().metadata().title());
        verify(geminiTransformer, times(1)).transform(html, sourceUrl);
        verify(geminiTransformer, times(1)).transformWithFeedback(eq(html), eq(invalidRecipe), eq("Missing required fields"));
        verify(validationService, times(2)).validate(any(Recipe.class));
        verify(postProcessor, times(1)).process(validRecipe, sourceUrl);
    }

    // Helper methods to create test Recipe objects

    private Recipe createValidRecipe(String title) {
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        title,
                        "https://example.com",  // source
                        "Test Author",
                        "en",
                        LocalDate.parse("2024-01-15"),
                        List.of("dessert"),
                        List.of("test"),
                        4,
                        "15m",
                        "12m",
                        "27m",
                        "easy",
                        null
                ),
                "Test description",
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main")),
                List.of("mixing bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Store in container",  // notes
                null  // storage
        );
    }

    private Recipe createRecipeWithMissingFields() {
        // Recipe with null title (validation constraint violation)
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        null,  // Missing title
                        null,  // source
                        null,  // author
                        "en",  // language
                        LocalDate.now(),  // dateCreated
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                List.of(),  // Empty ingredients
                null,
                List.of(),  // Empty instructions
                null,
                null,
                null
        );
    }
}