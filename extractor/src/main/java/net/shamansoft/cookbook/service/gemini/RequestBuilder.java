package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.shamansoft.cookbook.service.ResourcesLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestBuilder {

    private String prompt;
    private String exampleYaml;
    private String jsonSchema;

    @Value("${cookbook.gemini.temperature}")
    private float temperature;
    @Value("${cookbook.gemini.top-p}")
    private float topP;

    private final ResourcesLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @SneakyThrows
    public void init() {
        this.prompt = resourceLoader.loadYaml("classpath:prompt.md");
        this.jsonSchema = resourceLoader.loadYaml("classpath:recipe-schema-1.0.0.json");
        this.exampleYaml = resourceLoader.loadYaml("classpath:example.yaml");
    }

    private String withDate() {
        LocalDate today = LocalDate.now();
        return today.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
    }

    String withHtml(String html) {
        return prompt.formatted(withDate(), jsonSchema, exampleYaml, html);
    }

    String withHtmlAndFeedback(String html, String previousYaml, String validationError) {
        String basePrompt = withHtml(html);
        StringBuilder feedbackPrompt = new StringBuilder(basePrompt);
        feedbackPrompt.append("\n\n---VALIDATION FEEDBACK---\n\n");
        feedbackPrompt.append("Your previous attempt produced YAML that failed validation:\n\n");
        feedbackPrompt.append("```yaml\n");
        feedbackPrompt.append(previousYaml);
        feedbackPrompt.append("\n```\n\n");
        feedbackPrompt.append("Validation Error:\n");
        feedbackPrompt.append(validationError);
        feedbackPrompt.append("\n\nPlease correct these issues and provide a valid recipe YAML that conforms to the schema.");
        return feedbackPrompt.toString();
    }

    public String buildBodyString(String htmlContent) throws JsonProcessingException {
        String fullPrompt = withHtml(htmlContent);
        return buildRequestBody(fullPrompt);
    }

    public String buildBodyStringWithFeedback(String htmlContent, String previousYaml, String validationError) throws JsonProcessingException {
        String fullPrompt = withHtmlAndFeedback(htmlContent, previousYaml, validationError);
        return buildRequestBody(fullPrompt);
    }

    private String buildRequestBody(String fullPrompt) throws JsonProcessingException {
        GeminiRequest request = GeminiRequest.builder()
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
                        .build())
                .safetySettings(List.of(
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT").threshold("BLOCK_NONE").build(),
                        GeminiRequest.SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT").threshold("BLOCK_NONE").build()
                ))
                .build();
        return objectMapper.writeValueAsString(request);
    }

}
