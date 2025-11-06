package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidatingTransformerServiceTest {

    @Mock
    private GeminiRestTransformer geminiTransformer;

    @Mock
    private RecipeValidationService validationService;

    private ValidatingTransformerService validatingTransformer;

    private static final String VALID_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Test Recipe"
              source: "https://example.com"
              author: "Test"
              language: "en"
              date_created: "2024-01-15"
            description: "Test"
            ingredients:
              - item: "test"
                amount: "1"
                unit: "cup"
                optional: false
                component: "main"
            equipment: []
            instructions:
              - step: 1
                description: "Test"
            """;

    private static final String NORMALIZED_YAML = """
            schema_version: "1.0.0"
            recipe_version: "1.0.0"
            metadata:
              title: "Test Recipe"
            """;

    @BeforeEach
    void setUp() {
        validatingTransformer = new ValidatingTransformerService(geminiTransformer, validationService);
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 3);
    }

    @Test
    void transform_whenNotRecipe_shouldReturnImmediatelyWithoutValidation() {
        // Given
        String html = "<html>Not a recipe</html>";
        Transformer.Response notRecipeResponse = new Transformer.Response(false, "is_recipe: false");
        when(geminiTransformer.transform(html)).thenReturn(notRecipeResponse);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertFalse(result.isRecipe());
        assertEquals("is_recipe: false", result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verifyNoInteractions(validationService);
    }

    @Test
    void transform_whenValidRecipeOnFirstAttempt_shouldReturnNormalizedYaml() {
        // Given
        String html = "<html>Recipe content</html>";
        Transformer.Response initialResponse = new Transformer.Response(true, VALID_YAML);
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(NORMALIZED_YAML);

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);
        when(validationService.validate(VALID_YAML)).thenReturn(validResult);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertTrue(result.isRecipe());
        assertEquals(NORMALIZED_YAML, result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verify(validationService, times(1)).validate(VALID_YAML);
        verify(geminiTransformer, never()).transformWithFeedback(anyString(), anyString(), anyString());
    }

    @Test
    void transform_whenInvalidThenValidOnRetry_shouldReturnNormalizedYaml() {
        // Given
        String html = "<html>Recipe content</html>";
        String invalidYaml = "invalid: yaml";
        String validYamlAfterFeedback = VALID_YAML;

        Transformer.Response initialResponse = new Transformer.Response(true, invalidYaml);
        Transformer.Response retryResponse = new Transformer.Response(true, validYamlAfterFeedback);

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Missing required fields");
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(NORMALIZED_YAML);

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);
        when(validationService.validate(invalidYaml)).thenReturn(invalidResult);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml, "Missing required fields"))
                .thenReturn(retryResponse);
        when(validationService.validate(validYamlAfterFeedback)).thenReturn(validResult);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertTrue(result.isRecipe());
        assertEquals(NORMALIZED_YAML, result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verify(geminiTransformer, times(1)).transformWithFeedback(html, invalidYaml, "Missing required fields");
        verify(validationService, times(2)).validate(anyString());
    }

    @Test
    void transform_whenAllRetriesFail_shouldReturnAsNonRecipe() {
        // Given
        String html = "<html>Recipe content</html>";
        String invalidYaml1 = "invalid: yaml1";
        String invalidYaml2 = "invalid: yaml2";
        String invalidYaml3 = "invalid: yaml3";
        String invalidYaml4 = "invalid: yaml4";

        Transformer.Response initialResponse = new Transformer.Response(true, invalidYaml1);
        Transformer.Response retry1Response = new Transformer.Response(true, invalidYaml2);
        Transformer.Response retry2Response = new Transformer.Response(true, invalidYaml3);
        Transformer.Response retry3Response = new Transformer.Response(true, invalidYaml4);

        RecipeValidationService.ValidationResult invalidResult1 =
                RecipeValidationService.ValidationResult.failure("Error 1");
        RecipeValidationService.ValidationResult invalidResult2 =
                RecipeValidationService.ValidationResult.failure("Error 2");
        RecipeValidationService.ValidationResult invalidResult3 =
                RecipeValidationService.ValidationResult.failure("Error 3");
        RecipeValidationService.ValidationResult invalidResult4 =
                RecipeValidationService.ValidationResult.failure("Error 4");

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);
        when(validationService.validate(invalidYaml1)).thenReturn(invalidResult1);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml1, "Error 1")).thenReturn(retry1Response);
        when(validationService.validate(invalidYaml2)).thenReturn(invalidResult2);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml2, "Error 2")).thenReturn(retry2Response);
        when(validationService.validate(invalidYaml3)).thenReturn(invalidResult3);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml3, "Error 3")).thenReturn(retry3Response);
        when(validationService.validate(invalidYaml4)).thenReturn(invalidResult4);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertFalse(result.isRecipe(), "Should return as non-recipe after exhausting retries");
        assertEquals(invalidYaml4, result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verify(geminiTransformer, times(3)).transformWithFeedback(eq(html), anyString(), anyString());
        verify(validationService, times(4)).validate(anyString());
    }

    @Test
    void transform_whenModelChangesToNonRecipeAfterFeedback_shouldRespectThat() {
        // Given
        String html = "<html>Recipe content</html>";
        String invalidYaml = "invalid: yaml";

        Transformer.Response initialResponse = new Transformer.Response(true, invalidYaml);
        Transformer.Response retryResponse = new Transformer.Response(false, "is_recipe: false");

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Error");

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);
        when(validationService.validate(invalidYaml)).thenReturn(invalidResult);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml, "Error"))
                .thenReturn(retryResponse);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertFalse(result.isRecipe());
        assertEquals("is_recipe: false", result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verify(geminiTransformer, times(1)).transformWithFeedback(html, invalidYaml, "Error");
        verify(validationService, times(1)).validate(invalidYaml);
        // Should not validate the "is_recipe: false" result
        verify(validationService, never()).validate("is_recipe: false");
    }

    @Test
    void transform_whenRetriesSetToZero_shouldSkipValidationAndReturnRawYaml() {
        // Given
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 0);
        String html = "<html>Recipe content</html>";
        String rawYaml = "raw: yaml\nno: validation";
        Transformer.Response initialResponse = new Transformer.Response(true, rawYaml);

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertTrue(result.isRecipe(), "Should still be marked as recipe");
        assertEquals(rawYaml, result.value(), "Should return raw YAML without normalization");
        verify(geminiTransformer, times(1)).transform(html);
        verifyNoInteractions(validationService); // Validation should be completely skipped
        verify(geminiTransformer, never()).transformWithFeedback(anyString(), anyString(), anyString());
    }

    @Test
    void transform_whenRetriesSetToOne_shouldValidateAndRetryOnce() {
        // Given
        ReflectionTestUtils.setField(validatingTransformer, "maxRetries", 1);
        String html = "<html>Recipe content</html>";
        String invalidYaml = "invalid: yaml";
        String validYamlAfterFeedback = VALID_YAML;

        Transformer.Response initialResponse = new Transformer.Response(true, invalidYaml);
        Transformer.Response retryResponse = new Transformer.Response(true, validYamlAfterFeedback);

        RecipeValidationService.ValidationResult invalidResult =
                RecipeValidationService.ValidationResult.failure("Missing required fields");
        RecipeValidationService.ValidationResult validResult =
                RecipeValidationService.ValidationResult.success(NORMALIZED_YAML);

        when(geminiTransformer.transform(html)).thenReturn(initialResponse);
        when(validationService.validate(invalidYaml)).thenReturn(invalidResult);
        when(geminiTransformer.transformWithFeedback(html, invalidYaml, "Missing required fields"))
                .thenReturn(retryResponse);
        when(validationService.validate(validYamlAfterFeedback)).thenReturn(validResult);

        // When
        Transformer.Response result = validatingTransformer.transform(html);

        // Then
        assertTrue(result.isRecipe());
        assertEquals(NORMALIZED_YAML, result.value());
        verify(geminiTransformer, times(1)).transform(html);
        verify(geminiTransformer, times(1)).transformWithFeedback(html, invalidYaml, "Missing required fields");
        verify(validationService, times(2)).validate(anyString());
    }
}
