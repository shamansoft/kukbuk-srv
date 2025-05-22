package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.service.CleanupService;
import net.shamansoft.cookbook.service.ResourcesLoader;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiRestTransformer implements Transformer {

    private final WebClient geminiWebClient;
    private final CleanupService cleanupService;
    private final ResourcesLoader resourceLoader;
    private final ObjectMapper objectMapper;
    @Value("${cookbook.gemini.api-key}")
    private String apiKey;
    @Value("${cookbook.gemini.model}")
    private String model;
    @Value("${cookbook.gemini.temperature}")
    private float temperature;
    @Value("${cookbook.gemini.top-p}")
    private float topP;
    private String prompt;
    private String exampleYaml;
    private String jsonSchema;


    @PostConstruct
    @SneakyThrows
    public void init() {
        this.prompt = resourceLoader.loadYaml("classpath:prompt.md");
        this.jsonSchema = resourceLoader.loadYaml("classpath:recipe-schema-1.0.0.json");
        this.exampleYaml = resourceLoader.loadYaml("classpath:example.yaml");
    }

    @Override
    public String transform(String what) {
        try {
            String url = "/models/%s:generateContent?key=%s".formatted(model, apiKey);
            String body = buildRequestJson(what);
            JsonNode response = geminiWebClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("candidates")) {
                return cleanup(response.get("candidates").get(0)
                        .get("content").get("parts").get(0)
                        .get("text").asText());
            }
        } catch (Exception e) {
            log.error("Failed to transform content via Gemini API", e);
        }
        return "Could not transform content. Try again later.";
    }

    String buildRequestJson(String htmlContent) throws JsonProcessingException {
        LocalDate today = LocalDate.now();
        String currentDateFormatted = today.format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD
        String fullPrompt = String.format(prompt,
                currentDateFormatted,
                jsonSchema,
                exampleYaml,
                htmlContent
        );

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
                        .maxOutputTokens(10000)
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

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }


}