package net.shamansoft.cookbook.service.openai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.service.GenerationOverrides;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.TunableTransformer;
import net.shamansoft.cookbook.service.gemini.GeminiExtractionResult;
import net.shamansoft.cookbook.service.gemini.RequestBuilder;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extracts recipes with an OpenAI model through the Responses API with Structured Outputs.
 * <p>
 * Sends the same system prompt, input framing and JSON schema as the Gemini path (taken from
 * {@link RequestBuilder}), so results from the two providers are directly comparable.
 * <p>
 * Local profile only: it exists so the debug endpoint can evaluate OpenAI models. Production
 * traffic still goes through Gemini.
 */
@Service("openAiTransformer")
@Profile("local")
@Slf4j
@RequiredArgsConstructor
public class OpenAiRestTransformer implements TunableTransformer {

    public static final String PROVIDER = "openai";

    static final String NO_REASONING = "none";
    private static final String SCHEMA_NAME = "recipe_extraction";

    // Model ids from OpenAI's model list. Allow-listed so a typo fails fast with a clear 400
    // before any API call is made.
    private static final Set<String> ALLOWED_MODELS = Set.of(
            "gpt-6-luna",
            "gpt-6-astra",
            "gpt-6.1-sol"
    );

    private static final List<String> REASONING_EFFORTS = List.of(
            NO_REASONING, "minimal", "low", "medium", "high", "xhigh", "max");

    private final OpenAiClient openAiClient;
    private final RequestBuilder requestBuilder;
    private final ObjectMapper objectMapper;

    @Value("${cookbook.openai.model}")
    private String defaultModel;
    @Value("${cookbook.openai.reasoning-effort}")
    private String reasoningEffort;
    @Value("${cookbook.openai.max-output-tokens}")
    private int maxOutputTokens;
    // Sampling parameters are optional: the API rejects them unless reasoning effort is "none".
    @Value("${cookbook.openai.temperature:#{null}}")
    private Float temperature;
    @Value("${cookbook.openai.top-p:#{null}}")
    private Double topP;

    private Map<String, Object> strictSchema;

    @PostConstruct
    public void init() {
        this.strictSchema = OpenAiSchemaAdapter.toStrictSchema(requestBuilder.responseSchema());
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    public String validateOverrides(GenerationOverrides overrides) {
        if (!openAiClient.isConfigured()) {
            return "OpenAI API key is not set: export COOKBOOK_OPENAI_API_KEY (or OPENAI_API_KEY) and restart";
        }
        if (overrides == null) {
            return null;
        }
        if (overrides.model() != null && !ALLOWED_MODELS.contains(overrides.model())) {
            return "model must be one of: " + new TreeSet<>(ALLOWED_MODELS);
        }
        if (overrides.reasoningEffort() != null && !REASONING_EFFORTS.contains(overrides.reasoningEffort())) {
            return "reasoningEffort must be one of: " + REASONING_EFFORTS;
        }
        if (overrides.thinkingBudget() != null) {
            return "thinkingBudget is not supported by provider 'openai'; use reasoningEffort";
        }
        if (overrides.topK() != null) {
            return unsupported("topK");
        }
        if (overrides.seed() != null) {
            return unsupported("seed");
        }
        if (overrides.presencePenalty() != null) {
            return unsupported("presencePenalty");
        }
        if (overrides.frequencyPenalty() != null) {
            return unsupported("frequencyPenalty");
        }
        if (overrides.stopSequences() != null) {
            return unsupported("stopSequences");
        }
        return null;
    }

    private static String unsupported(String field) {
        return field + " is not supported by provider 'openai'";
    }

    @Override
    public Response transform(String htmlContent, String sourceUrl) {
        return transformWithOverrides(htmlContent, null);
    }

    @Override
    public Response transformWithOverrides(String content, GenerationOverrides overrides) {
        Objects.requireNonNull(content, "content cannot be null");

        OpenAiClient.Result result = openAiClient.request(buildRequestBody(content, overrides));
        if (!result.success()) {
            log.error("OpenAI request failed: {}, input length: {}", result.errorMessage(), content.length());
            throw new ClientException("OpenAI request failed: " + result.errorMessage());
        }

        GeminiExtractionResult extraction;
        try {
            // Same wrapper shape as Gemini: both providers answer against llm-recipe-schema.json.
            extraction = objectMapper.readValue(result.text(), GeminiExtractionResult.class);
        } catch (JacksonException e) {
            log.error("Invalid JSON structure received from OpenAI API", e);
            throw new ClientException("Invalid JSON structure received from OpenAI API: " + e.getMessage(), e);
        }

        double confidence = extraction.recipeConfidence();
        if (!extraction.isRecipe()) {
            log.debug("OpenAI returned is_recipe=false with confidence={}", confidence);
            return Transformer.Response.withRawResponse(false, confidence, List.of(), result.text());
        }

        List<Recipe> recipes = toRecipes(extraction.recipes());
        log.debug("OpenAI returned {} recipe(s) with confidence={}", recipes.size(), confidence);
        return Transformer.Response.withRawResponse(true, confidence, recipes, result.text());
    }

    Map<String, Object> buildRequestBody(String content, GenerationOverrides overrides) {
        String effort = overrides != null && overrides.reasoningEffort() != null
                ? overrides.reasoningEffort() : reasoningEffort;
        // A configured sampling default would make the API reject any request that turns
        // reasoning on, so defaults apply only without reasoning. An explicit per-request
        // value is always sent: if the combination is invalid the API's own error is returned.
        boolean samplingDefaultsApply = NO_REASONING.equals(effort);
        Float effTemperature = overrides != null && overrides.temperature() != null
                ? overrides.temperature() : (samplingDefaultsApply ? temperature : null);
        Double effTopP = overrides != null && overrides.topP() != null
                ? overrides.topP() : (samplingDefaultsApply ? topP : null);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", SCHEMA_NAME);
        format.put("schema", strictSchema);
        format.put("strict", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", overrides != null && overrides.model() != null ? overrides.model() : defaultModel);
        body.put("instructions", requestBuilder.htmlSystemPrompt());
        body.put("input", requestBuilder.htmlUserContent(content));
        body.put("max_output_tokens", overrides != null && overrides.maxOutputTokens() != null
                ? overrides.maxOutputTokens() : maxOutputTokens);
        body.put("reasoning", Map.of("effort", effort));
        if (effTemperature != null) {
            body.put("temperature", effTemperature);
        }
        if (effTopP != null) {
            body.put("top_p", effTopP);
        }
        body.put("text", Map.of("format", format));
        // Recipe pages are user content: don't keep the response stored for later retrieval.
        body.put("store", false);
        return body;
    }

    /**
     * The items in recipes[] don't carry is_recipe (it's on the wrapper), so we inject true.
     */
    private List<Recipe> toRecipes(List<Recipe> rawRecipes) {
        return rawRecipes.stream()
                .filter(Objects::nonNull)
                .map(r -> new Recipe(true, r.schemaVersion(), r.recipeVersion(), r.metadata(),
                        r.description(), r.ingredients(), r.equipment(), r.instructions(),
                        r.nutrition(), r.notes(), r.storage()))
                .toList();
    }
}
