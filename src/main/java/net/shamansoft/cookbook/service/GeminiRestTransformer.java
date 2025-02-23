package net.shamansoft.cookbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@Service
@Slf4j
public class GeminiRestTransformer implements Transformer {

    private final WebClient geminiWebClient;
    private final CleanupService cleanupService;
    private final String apiKey;
    private final String model;
    private final String prompt;
    private final float temperature;
    private final String exampleYaml;

    public GeminiRestTransformer(WebClient geminiWebClient,
                                 ResourceLoader resourceLoader,
                                 CleanupService cleanupService,
                                 @Value("${cookbook.gemini.api-key}") String apiKey,
                                 @Value("${cookbook.gemini.model}") String model,
                                 @Value("${cookbook.gemini.prompt}") String prompt,
                                 @Value("${cookbook.gemini.temperature}") float temperature) throws IOException {
        this.geminiWebClient = geminiWebClient;
        this.cleanupService = cleanupService;
        this.apiKey = apiKey;
        this.model = model;
        this.prompt = prompt;
        this.temperature = temperature;
        this.exampleYaml = loadExampleYaml(resourceLoader);
    }

    @Override
    public String transform(String what) {
        try {
            final String requestBody = String.format("""                                                                                                                  
                    {                                                                                                                                                             
                        "contents": [{                                                                                                                                            
                            "parts": [                                                                                                                                            
                                {"text": "%s"},                                                                                                                                   
                                {"text": "%s"},                                                                                                                                   
                                {"text": "%s"}                                                                                                                                    
                            ]                                                                                                                                                     
                        }],                                                                                                                                                       
                        "generationConfig": {                                                                                                                                     
                            "temperature": %.1f,                                                                                                                                  
                            "topP": 0.8                                                                                                                                           
                        }                                                                                                                                                         
                    }""", escapeJson(what), escapeJson(exampleYaml), escapeJson(prompt), temperature);
            String url = "/models/%s:generateContent?key=%s".formatted(model, apiKey);
            JsonNode response = geminiWebClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
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

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String loadExampleYaml(ResourceLoader resourceLoader) throws IOException {
        return resourceLoader.getResource("classpath:example.yaml")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}