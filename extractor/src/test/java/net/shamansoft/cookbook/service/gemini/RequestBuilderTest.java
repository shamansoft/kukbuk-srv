package net.shamansoft.cookbook.service.gemini;

import net.shamansoft.cookbook.service.ResourcesLoader;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestBuilderTest {

    @Mock
    private ResourcesLoader resourcesLoader;

    private ObjectMapper objectMapper;
    private RequestBuilder requestBuilder;

    private Clock clock;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-02-03T10:00:00Z"), ZoneId.of("UTC"));
        requestBuilder = new RequestBuilder(resourcesLoader, objectMapper, clock);

        // Set configuration values
        ReflectionTestUtils.setField(requestBuilder, "temperature", 0.7f);
        ReflectionTestUtils.setField(requestBuilder, "topP", 0.9f);
        ReflectionTestUtils.setField(requestBuilder, "maxOutputTokens", 4096);
        ReflectionTestUtils.setField(requestBuilder, "safetyThreshold", "BLOCK_NONE");

        // Mock resource loading
        when(resourcesLoader.loadTextFile(eq("classpath:prompt.md")))
                .thenReturn("Date: %s\nHTML: %s");
        when(resourcesLoader.loadTextFile(eq("classpath:prompt_with_validation.md")))
                .thenReturn("\nValidation Error: %s\nPrevious Recipe: %s");
        when(resourcesLoader.loadTextFile(eq("classpath:recipe-schema-1.0.0.json")))
                .thenReturn("""
                        {
                          "$id": "https://example.com/recipe.schema.json",
                          "$schema": "http://json-schema.org/draft-07/schema#",
                          "type": "object",
                          "properties": {
                            "title": {"type": "string"}
                          }
                        }
                        """);

        // Initialize
        requestBuilder.init();
    }

    private Recipe createTestRecipe(String title) {
        RecipeMetadata metadata = new RecipeMetadata(
                title,
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
                null);

        List<Ingredient> ingredients = List.of(
                new Ingredient("Sugar", "1", "cup", null, null, null, null));

        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null));

        return new Recipe(
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
                null);
    }

    @Test
    void buildRequestWithHtmlContentCreatesValidRequest() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";

        // When
        GeminiRequest request = requestBuilder.buildRequest(htmlContent);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getContents()).hasSize(1);
        assertThat(request.getContents().get(0).getParts()).hasSize(1);

        String promptText = request.getContents().get(0).getParts().get(0).getText();
        assertThat(promptText).contains("HTML: " + htmlContent);
        assertThat(promptText).contains("Date:");

        // Verify generation config
        assertThat(request.getGenerationConfig()).isNotNull();
        assertThat(request.getGenerationConfig().getTemperature()).isEqualTo(0.7f);
        assertThat(request.getGenerationConfig().getTopP()).isEqualTo(0.9f);
        assertThat(request.getGenerationConfig().getMaxOutputTokens()).isEqualTo(4096);
        assertThat(request.getGenerationConfig().getResponseMimeType()).isEqualTo("application/json");
        assertThat(request.getGenerationConfig().getResponseSchema()).isNotNull();

        // Verify safety settings
        assertThat(request.getSafetySettings()).hasSize(4);
        assertThat(request.getSafetySettings().get(0).getThreshold()).isEqualTo("BLOCK_NONE");
    }

    @Test
    void buildRequestWithFeedbackIncludesValidationInfo() throws JacksonException {
        // Given
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe previousRecipe = createTestRecipe("Test Recipe");
        String validationError = "Missing required field: instructions";

        // When
        GeminiRequest request = requestBuilder.buildRequest(htmlContent, previousRecipe, validationError);

        // Then
        assertThat(request).isNotNull();
        String promptText = request.getContents().get(0).getParts().get(0).getText();
        assertThat(promptText).contains("HTML: " + htmlContent);
        assertThat(promptText).contains("Validation Error: " + validationError);
        assertThat(promptText).contains("Test Recipe");
    }

    @Test
    void buildRequestThrowsExceptionWhenHtmlContentIsNull() {
        // When/Then
        assertThatThrownBy(() -> requestBuilder.buildRequest(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("htmlContent cannot be null");
    }

    @Test
    void buildRequestWithFeedbackThrowsExceptionWhenHtmlContentIsNull() {
        // Given
        Recipe recipe = createTestRecipe("Test");
        String error = "Error";

        // When/Then
        assertThatThrownBy(() -> requestBuilder.buildRequest(null, recipe, error))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("htmlContent cannot be null");
    }

    @Test
    void buildRequestWithFeedbackThrowsExceptionWhenFeedbackIsNull() {
        // Given
        String htmlContent = "<html></html>";
        String error = "Error";

        // When/Then
        assertThatThrownBy(() -> requestBuilder.buildRequest(htmlContent, null, error))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("feedback cannot be null");
    }

    @Test
    void buildRequestWithFeedbackThrowsExceptionWhenValidationErrorIsNull() {
        // Given
        String htmlContent = "<html></html>";
        Recipe recipe = createTestRecipe("Test");

        // When/Then
        assertThatThrownBy(() -> requestBuilder.buildRequest(htmlContent, recipe, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("validationError cannot be null");
    }

    @Test
    void initRemovesIdAndSchemaFromJsonSchema() throws IOException {
        // When
        requestBuilder.init();

        // Then
        GeminiRequest request = requestBuilder.buildRequest("<html></html>");
        Object schema = request.getGenerationConfig().getResponseSchema();
        String schemaJson = objectMapper.writeValueAsString(schema);

        // Verify $id and $schema are removed
        assertThat(schemaJson).doesNotContain("$id");
        assertThat(schemaJson).doesNotContain("$schema");
        // But the actual schema content should still be there
        assertThat(schemaJson).contains("\"type\"");
        assertThat(schemaJson).contains("\"properties\"");
    }

    @Test
    void buildRequestIncludesAllSafetyCategories() throws JacksonException {
        // Given
        String htmlContent = "<html></html>";

        // When
        GeminiRequest request = requestBuilder.buildRequest(htmlContent);

        // Then
        List<GeminiRequest.SafetySetting> safetySettings = request.getSafetySettings();
        assertThat(safetySettings).hasSize(4);

        List<String> categories = safetySettings.stream()
                .map(GeminiRequest.SafetySetting::getCategory)
                .toList();

        assertThat(categories).containsExactlyInAnyOrder(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT");

        safetySettings.forEach(setting -> assertThat(setting.getThreshold()).isEqualTo("BLOCK_NONE"));
    }

    @Test
    void buildRequestIncludesCurrentDate() throws JacksonException {
        // Given
        String htmlContent = "<html></html>";

        // When
        GeminiRequest request = requestBuilder.buildRequest(htmlContent);

        // Then
        String promptText = request.getContents().get(0).getParts().get(0).getText();
        // Should contain a date in YYYY-MM-DD format
        assertThat(promptText).containsPattern("Date: \\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void postConstructLoadsPromptAndSchemaResources() throws IOException {
        // Given
        RequestBuilder builder = new RequestBuilder(resourcesLoader, objectMapper, clock);
        ReflectionTestUtils.setField(builder, "temperature", 0.7f);
        ReflectionTestUtils.setField(builder, "topP", 0.9f);
        ReflectionTestUtils.setField(builder, "maxOutputTokens", 4096);
        ReflectionTestUtils.setField(builder, "safetyThreshold", "BLOCK_NONE");

        // When
        builder.init();

        // Then
        String prompt = (String) ReflectionTestUtils.getField(builder, "prompt");
        String validationPrompt = (String) ReflectionTestUtils.getField(builder, "validationPrompt");
        Object parsedJsonSchema = ReflectionTestUtils.getField(builder, "parsedJsonSchema");

        assertThat(prompt).isNotNull();
        assertThat(validationPrompt).isNotNull();
        assertThat(parsedJsonSchema).isNotNull();
    }

    @Test
    void postConstructRemovesIdAndSchemaFromJsonSchema() throws IOException {
        // Given
        RequestBuilder builder = new RequestBuilder(resourcesLoader, objectMapper, clock);
        ReflectionTestUtils.setField(builder, "temperature", 0.7f);
        ReflectionTestUtils.setField(builder, "topP", 0.9f);
        ReflectionTestUtils.setField(builder, "maxOutputTokens", 4096);
        ReflectionTestUtils.setField(builder, "safetyThreshold", "BLOCK_NONE");

        // When
        builder.init();

        // Then
        Object parsedJsonSchema = ReflectionTestUtils.getField(builder, "parsedJsonSchema");
        String schemaJson = objectMapper.writeValueAsString(parsedJsonSchema);

        // Verify $id and $schema are removed during initialization
        assertThat(schemaJson).doesNotContain("$id");
        assertThat(schemaJson).doesNotContain("$schema");
        assertThat(schemaJson).contains("\"type\"");
    }
}
