package net.shamansoft.cookbook.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiTransformer implements Transformer {

    private final Client geminiClient;
    @Value("${cookbook.gemini.model}")
    private String model;
    @Value("${cookbook.gemini.prompt}")
    private String prompt;
    @Value("${cookbook.gemini.temperature}")
    private float temperature;

    @Override
    public String transform(String what) {
        // get file content from resource folder
        String exampleYaml = getExampleYaml();
        Schema schema = getRecipeSchema();
        try {
            var response = geminiClient.models.generateContent(model,
                    Content.builder().parts(List.of(
                            Part.builder().text(what).build(),
                            Part.builder().text(exampleYaml).build(),
                            Part.builder().text(prompt).build()
                    )).build(),
                    GenerateContentConfig.builder()
                            .temperature(temperature)
                            .topP(0.8f)
                            .responseMimeType("application/x-yaml")
                            .responseSchema(schema)
                            .build());
            return response.text();
        } catch (Exception e) {
            log.error("Failed to transform content", e);
        }
        return "Could not transform content";
    }

    private Schema getRecipeSchema() {
        //TODO implement: get schema from resources folder
        return Schema.fromJson("");

    }

    private String getExampleYaml() {
        //TODO implement
        return "";
    }
}
