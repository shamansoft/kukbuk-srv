package net.shamansoft.cookbook.service.gemini;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.shamansoft.cookbook.service.ResourcesLoader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class Prompt {

    private String prompt;
    private String exampleYaml;
    private String jsonSchema;

    private final ResourcesLoader resourceLoader;

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

    public String withHtml(String html) {
        return prompt.formatted(withDate(), jsonSchema, exampleYaml, html);
    }


}
