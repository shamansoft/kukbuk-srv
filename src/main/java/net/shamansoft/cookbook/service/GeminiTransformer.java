package net.shamansoft.cookbook.service;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@Slf4j
public class GeminiTransformer implements Transformer {

    private final Client geminiClient;
    private final String exampleYaml;
    private final CleanupService cleanupService;
    //    private final Schema schema;
//    @Value("${cookbook.gemini.model}")
    private final String model;
//    @Value("${cookbook.gemini.prompt}")
    private final String prompt;
//    @Value("${cookbook.gemini.temperature}")
    private final float temperature;

    public GeminiTransformer(Client geminiClient,
                             ResourceLoader resourceLoader,
                             CleanupService cleanupService,
                             @Value("${cookbook.gemini.model}") String model,
                             @Value("${cookbook.gemini.prompt}") String prompt,
                             @Value("${cookbook.gemini.temperature}") float temperature
                         ) throws IOException {
        this.geminiClient = geminiClient;
        this.exampleYaml = loadExampleYaml(resourceLoader);
//        this.schema = getSchema(resourceLoader);
        this.cleanupService = cleanupService;
        this.model = model;
        this.prompt = prompt;
        this.temperature = temperature;
        // print all the fields
        log.debug("GeminiTransformer: model={}, prompt={}, temperature={}", model, prompt, temperature);
        log.debug("Example YAML: {}", exampleYaml);
    }


    @Override
    public String transform(String what) {
        try {
            log.debug("transform: model={}, prompt={}, temperature={}", model, prompt, temperature);
            log.debug("Gemini client: {}", geminiClient);
            Models models = geminiClient.models;
            log.debug("Transforming content with model; {}", model);
            var response = models.generateContent(model,
                    Content.builder().parts(List.of(
                            Part.builder().text(what).build(),
                            Part.builder().text(exampleYaml).build(),
                            Part.builder().text(prompt).build()
                    )).build(),
                    GenerateContentConfig.builder()
                            .temperature(temperature)
                            .topP(0.8f)
                            .build());
            return cleanup(response.text());
        } catch (Exception e) {
            log.error("Failed to transform content", e);
        }
        return "Could not transform content. Try again later.";
    }

    private String cleanup(String text) {
        return cleanupService.removeYamlSign(text);
    }

    private Schema getSchema(ResourceLoader resourceLoader) throws IOException {
        return Schema.fromJson(resourceLoader.getResource("classpath:recipe-schema-1.0.0.json")
                .getContentAsString(Charset.defaultCharset()));
    }

    private String loadExampleYaml(ResourceLoader resourceLoader) throws IOException {
        return resourceLoader.getResource("classpath:example.yaml")
                .getContentAsString(Charset.defaultCharset());
    }
}
