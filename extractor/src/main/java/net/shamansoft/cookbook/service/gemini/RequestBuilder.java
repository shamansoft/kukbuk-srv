package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.shamansoft.cookbook.service.ResourcesLoader;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestBuilder {

    private String prompt;
    private String validationPrompt;
    private Object parsedJsonSchema;

    @Value("${cookbook.gemini.temperature}")
    private float temperature;
    @Value("${cookbook.gemini.top-p}")
    private float topP;

    private final ResourcesLoader resourceLoader;
    private final ObjectMapper objectMapper;

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
        // Parse JSON schema and remove $id and $schema fields as they're not needed for Gemini API
        JsonNode schemaNode = objectMapper.readTree(jsonSchemaString);
        if (schemaNode instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.remove("$id");
            objectNode.remove("$schema");
            return objectMapper.writeValueAsString(objectNode);
        }
        return jsonSchemaString;
    }

    private String withDate() {
        LocalDate today = LocalDate.now();
        return today.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
    }

    private String withHtml(String html) {
        // Note: prompt still references schema as string for documentation, but we use parsedJsonSchema in the request
        return prompt.formatted(withDate(), html);
    }

    String withHtmlAndFeedback(String html, Recipe previousRecipe, String validationError) throws JsonProcessingException {
        String basePrompt = withHtml(html);
        return basePrompt + validationPrompt.formatted(validationError, objectMapper.writeValueAsString(previousRecipe));
    }

    public GeminiRequest buildRequest(String htmlContent) throws JsonProcessingException {
        String fullPrompt = withHtml(htmlContent);
        return buildRequestBodyWithSchema(fullPrompt);
    }

    public GeminiRequest buildRequest(String htmlContent, Recipe feedback, String validationError) throws JsonProcessingException {
        String fullPrompt = withHtmlAndFeedback(htmlContent, feedback, validationError);
        return buildRequestBodyWithSchema(fullPrompt);
    }

    private GeminiRequest buildRequestBodyWithSchema(String fullPrompt) throws JsonProcessingException {
        return GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text(fullPrompt)
                                                .build()
                                ))
                                .build()
                ))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .temperature(temperature)
                        .topP(topP)
                        .maxOutputTokens(4096)
                        .responseMimeType("application/json")
                        .responseSchema(parsedJsonSchema)
                        .build())
                .safetySettings(List.of(
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT").threshold("BLOCK_NONE").build()
                ))
                .build();
    }

}
