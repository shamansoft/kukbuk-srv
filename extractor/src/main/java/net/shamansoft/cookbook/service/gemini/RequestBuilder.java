package net.shamansoft.cookbook.service.gemini;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.shamansoft.cookbook.service.ResourcesLoader;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RequestBuilder {

    private final ResourcesLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private String prompt;
    private String validationPrompt;
    private Object parsedJsonSchema;
    @Value("${cookbook.gemini.temperature}")
    private float temperature;
    @Value("${cookbook.gemini.top-p}")
    private float topP;
    @Value("${cookbook.gemini.max-output-tokens}")
    private int maxOutputTokens;
    @Value("${cookbook.gemini.safety-threshold}")
    private String safetyThreshold;

    @PostConstruct
    @SneakyThrows
    public void init() {
        this.prompt = resourceLoader.loadTextFile("classpath:prompt.md");
        this.validationPrompt = resourceLoader.loadTextFile("classpath:prompt_with_validation.md");
        String jsonSchemaString = loadSchema();
        this.parsedJsonSchema = objectMapper.readValue(jsonSchemaString, Object.class);
    }

    private String loadSchema() throws IOException {
        String jsonSchemaString = resourceLoader.loadTextFile("classpath:recipe-schema-1.0.0.json");
        // Parse JSON schema and remove $id and $schema fields as they're not needed for
        // Gemini API
        JsonNode schemaNode = objectMapper.readTree(jsonSchemaString);
        if (schemaNode instanceof ObjectNode objectNode) {
            objectNode.remove("$id");
            objectNode.remove("$schema");
            return objectMapper.writeValueAsString(objectNode);
        }
        return jsonSchemaString;
    }

    private String withDate() {
        LocalDate today = LocalDate.now(clock);
        return today.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
    }

    private String withHtml(String html) {
        // Note: prompt still references schema as string for documentation, but we use
        // parsedJsonSchema in the request
        return prompt.formatted(withDate(), html);
    }

    private String withHtmlAndFeedback(String html, Recipe previousRecipe, String validationError)
            throws JacksonException {
        String basePrompt = withHtml(html);
        return basePrompt
                + validationPrompt.formatted(validationError, objectMapper.writeValueAsString(previousRecipe));
    }

    public GeminiRequest buildRequest(String htmlContent) throws JacksonException {
        Objects.requireNonNull(htmlContent, "htmlContent cannot be null");
        String fullPrompt = withHtml(htmlContent);
        return buildRequestBodyWithSchema(fullPrompt);
    }

    public GeminiRequest buildRequest(String htmlContent, Recipe feedback, String validationError)
            throws JacksonException {
        Objects.requireNonNull(htmlContent, "htmlContent cannot be null");
        Objects.requireNonNull(feedback, "feedback cannot be null");
        Objects.requireNonNull(validationError, "validationError cannot be null");
        String fullPrompt = withHtmlAndFeedback(htmlContent, feedback, validationError);
        return buildRequestBodyWithSchema(fullPrompt);
    }

    private GeminiRequest buildRequestBodyWithSchema(String fullPrompt) throws JacksonException {
        return GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text(fullPrompt)
                                                .build()))
                                .build()))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .temperature(temperature)
                        .topP(topP)
                        .maxOutputTokens(maxOutputTokens)
                        .responseMimeType("application/json")
                        .responseSchema(parsedJsonSchema)
                        .build())
                .safetySettings(List.of(
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT")
                                .threshold(safetyThreshold).build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH")
                                .threshold(safetyThreshold).build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT")
                                .threshold(safetyThreshold).build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT")
                                .threshold(safetyThreshold).build()))
                .build();
    }

}
