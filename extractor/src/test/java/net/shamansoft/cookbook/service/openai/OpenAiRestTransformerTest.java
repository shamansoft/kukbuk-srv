package net.shamansoft.cookbook.service.openai;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.GenerationOverrides;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.gemini.RequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiRestTransformerTest {

    private static final String HTML = "<html><body>Recipe content</body></html>";

    // What strict mode makes the model produce: every optional field present, most of them null
    private static final String RECIPE_JSON = """
            {"is_recipe": true, "recipe_confidence": 0.9, "internal_reasoning": null,
             "recipes": [{
               "schema_version": "1.0.0", "recipe_version": "1.0.0",
               "metadata": {"title": "Pancakes", "source": "https://example.com", "author": null, "language": "en",
                            "date_created": "2026-10-06", "category": null, "tags": null, "servings": 2,
                            "prep_time": null, "cook_time": null, "total_time": null, "difficulty": null,
                            "cover_image": null},
               "description": null,
               "ingredients": [
                 {"item": "flour", "amount": 1, "unit": "cup", "notes": null, "optional": null,
                  "substitutions": null, "component": null},
                 {"item": "milk", "amount": null, "unit": null, "notes": "to taste", "optional": null,
                  "substitutions": null, "component": null}],
               "equipment": null,
               "instructions": [{"step": 1, "description": "Mix and fry", "time": null, "temperature": null, "media": null}],
               "nutrition": null, "notes": null, "storage": null}]}
            """;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private RequestBuilder requestBuilder;

    private OpenAiRestTransformer transformer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        transformer = new OpenAiRestTransformer(openAiClient, requestBuilder, objectMapper);
        ReflectionTestUtils.setField(transformer, "defaultModel", "gpt-6-luna");
        ReflectionTestUtils.setField(transformer, "reasoningEffort", "none");
        ReflectionTestUtils.setField(transformer, "maxOutputTokens", 8192);
        ReflectionTestUtils.setField(transformer, "temperature", 0.0f);

        lenient().when(requestBuilder.responseSchema()).thenReturn(objectMapper.readValue("""
                {"type": "object", "required": ["is_recipe"],
                 "properties": {"is_recipe": {"type": "boolean"}, "internal_reasoning": {"type": "string"}}}
                """, Object.class));
        lenient().when(requestBuilder.htmlSystemPrompt()).thenReturn("System instructions");
        lenient().when(requestBuilder.htmlUserContent(HTML)).thenReturn("<HTML_CONTENT>" + HTML + "</HTML_CONTENT>");
        lenient().when(openAiClient.isConfigured()).thenReturn(true);
        transformer.init();
    }

    private static GenerationOverrides overrides(Float temperature, Double topP, Integer maxOutputTokens,
                                                 String model, String reasoningEffort) {
        return new GenerationOverrides(temperature, topP, maxOutputTokens, null, model,
                null, null, null, null, null, reasoningEffort);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody() {
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(openAiClient).request(body.capture());
        return body.getValue();
    }

    // --- identity & validation ---------------------------------------------------------------

    @Test
    void providerIsOpenAiAndDefaultModelComesFromConfig() {
        assertThat(transformer.provider()).isEqualTo("openai");
        assertThat(transformer.defaultModel()).isEqualTo("gpt-6-luna");
    }

    @Test
    void validateOverridesAcceptsNullEmptyAndSupportedValues() {
        assertThat(transformer.validateOverrides(null)).isNull();
        assertThat(transformer.validateOverrides(overrides(null, null, null, null, null))).isNull();
        assertThat(transformer.validateOverrides(overrides(0.2f, 0.9, 4096, "gpt-6-luna", "low"))).isNull();
    }

    @Test
    void validateOverridesFailsWhenApiKeyIsMissing() {
        when(openAiClient.isConfigured()).thenReturn(false);

        assertThat(transformer.validateOverrides(null)).contains("API key").contains("OPENAI_API_KEY");
    }

    @Test
    void validateOverridesRejectsUnknownModelAndEffort() {
        assertThat(transformer.validateOverrides(overrides(null, null, null, "gemini-2.5-flash-lite", null)))
                .contains("model").contains("gpt-6-luna");
        assertThat(transformer.validateOverrides(overrides(null, null, null, null, "extreme")))
                .contains("reasoningEffort");
    }

    @Test
    void validateOverridesRejectsParametersTheResponsesApiDoesNotHave() {
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, 1024, null, null, null, null, null, null, null)))
                .contains("thinkingBudget").contains("reasoningEffort");
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, null, null, 40, null, null, null, null, null)))
                .contains("topK");
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, null, null, null, 42, null, null, null, null)))
                .contains("seed");
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, null, null, null, null, 0.5f, null, null, null)))
                .contains("presencePenalty");
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, null, null, null, null, null, 0.5f, null, null)))
                .contains("frequencyPenalty");
        assertThat(transformer.validateOverrides(
                new GenerationOverrides(null, null, null, null, null, null, null, null, null, List.of("END"), null)))
                .contains("stopSequences");
    }

    // --- request body ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void buildsResponsesApiBodyFromConfiguredDefaults() {
        Map<String, Object> body = transformer.buildRequestBody(HTML, null);

        assertThat(body.get("model")).isEqualTo("gpt-6-luna");
        assertThat(body.get("instructions")).isEqualTo("System instructions");
        assertThat(body.get("input")).isEqualTo("<HTML_CONTENT>" + HTML + "</HTML_CONTENT>");
        assertThat(body.get("max_output_tokens")).isEqualTo(8192);
        assertThat(body.get("reasoning")).isEqualTo(Map.of("effort", "none"));
        assertThat(body.get("temperature")).isEqualTo(0.0f);
        assertThat(body).doesNotContainKey("top_p");
        assertThat(body.get("store")).isEqualTo(false);

        Map<String, Object> format = (Map<String, Object>) ((Map<String, Object>) body.get("text")).get("format");
        assertThat(format.get("type")).isEqualTo("json_schema");
        assertThat(format.get("strict")).isEqualTo(true);
        assertThat(format.get("name")).isEqualTo("recipe_extraction");
        Map<String, Object> schema = (Map<String, Object>) format.get("schema");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(schema.get("required")).isEqualTo(List.of("is_recipe", "internal_reasoning"));
    }

    @Test
    void overridesReplaceConfiguredValues() {
        Map<String, Object> body = transformer.buildRequestBody(HTML, overrides(0.3f, 0.9, 2048, "gpt-6-astra", null));

        assertThat(body.get("model")).isEqualTo("gpt-6-astra");
        assertThat(body.get("max_output_tokens")).isEqualTo(2048);
        assertThat(body.get("temperature")).isEqualTo(0.3f);
        assertThat(body.get("top_p")).isEqualTo(0.9);
    }

    @Test
    void configuredSamplingDefaultsAreNotSentWhenReasoningIsOn() {
        // The API rejects temperature/top_p unless reasoning effort is "none"
        Map<String, Object> body = transformer.buildRequestBody(HTML, overrides(null, null, null, null, "low"));

        assertThat(body.get("reasoning")).isEqualTo(Map.of("effort", "low"));
        assertThat(body).doesNotContainKey("temperature");
        assertThat(body).doesNotContainKey("top_p");
    }

    @Test
    void explicitSamplingOverrideIsSentEvenWhenReasoningIsOn() {
        // Not silently dropped: the caller asked for it, so the API's own error should come back
        Map<String, Object> body = transformer.buildRequestBody(HTML, overrides(0.3f, null, null, null, "high"));

        assertThat(body.get("temperature")).isEqualTo(0.3f);
    }

    // --- transform ---------------------------------------------------------------------------

    @Test
    void transformParsesRecipeFromStrictModeOutput() {
        when(openAiClient.request(anyMap())).thenReturn(OpenAiClient.Result.success(RECIPE_JSON));

        Transformer.Response response = transformer.transform(HTML, "https://example.com");

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.confidence()).isEqualTo(0.9);
        assertThat(response.rawLlmResponse()).isEqualTo(RECIPE_JSON);
        assertThat(response.recipes()).hasSize(1);
        assertThat(response.recipe().isRecipe()).isTrue();
        assertThat(response.recipe().metadata().title()).isEqualTo("Pancakes");
        assertThat(response.recipe().ingredients()).hasSize(2);
        assertThat(response.recipe().ingredients().get(0).amount()).isEqualTo("1");
        assertThat(response.recipe().ingredients().get(1).amount()).isNull();
        assertThat(response.recipe().ingredients().get(1).notes()).isEqualTo("to taste");
        assertThat(capturedBody().get("model")).isEqualTo("gpt-6-luna");
    }

    @Test
    void transformWithOverridesSendsTheOverriddenRequest() {
        when(openAiClient.request(anyMap())).thenReturn(OpenAiClient.Result.success(RECIPE_JSON));

        transformer.transformWithOverrides(HTML, overrides(null, null, 4096, null, "medium"));

        Map<String, Object> body = capturedBody();
        assertThat(body.get("max_output_tokens")).isEqualTo(4096);
        assertThat(body.get("reasoning")).isEqualTo(Map.of("effort", "medium"));
    }

    @Test
    void transformReturnsNotRecipeWithConfidence() {
        when(openAiClient.request(anyMap())).thenReturn(OpenAiClient.Result.success("""
                {"is_recipe": false, "recipe_confidence": 0.2, "internal_reasoning": null, "recipes": null}
                """));

        Transformer.Response response = transformer.transform(HTML, "https://example.com");

        assertThat(response.isRecipe()).isFalse();
        assertThat(response.confidence()).isEqualTo(0.2);
        assertThat(response.recipes()).isEmpty();
    }

    @Test
    void transformThrowsWithTheApiErrorMessageWhenRequestFails() {
        when(openAiClient.request(anyMap())).thenReturn(
                OpenAiClient.Result.failure("OpenAI API error 429: You have no credits remaining."));

        assertThatThrownBy(() -> transformer.transform(HTML, "https://example.com"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("OpenAI request failed")
                .hasMessageContaining("You have no credits remaining.");
    }

    @Test
    void transformThrowsWhenOutputIsNotValidJson() {
        when(openAiClient.request(anyMap())).thenReturn(OpenAiClient.Result.success("{\"is_recipe\": tru"));

        assertThatThrownBy(() -> transformer.transform(HTML, "https://example.com"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Invalid JSON structure received from OpenAI API");
    }

    @Test
    void transformRejectsNullContentBeforeCallingTheApi() {
        assertThatThrownBy(() -> transformer.transformWithOverrides(null, null))
                .isInstanceOf(NullPointerException.class);
        verify(openAiClient, never()).request(any());
    }
}
