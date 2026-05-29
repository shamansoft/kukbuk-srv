package net.shamansoft.cookbook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;

@Service
@Slf4j
@Profile("local")
public class LocalDumpService implements DumpService {

    private final String dumpDir;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalDumpService(
            @Value("${cookbook.debug.dump-dir}") String dumpDir,
            ObjectMapper objectMapper,
            Clock clock) {
        this.dumpDir = dumpDir;
        this.objectMapper = objectMapper;
        this.clock = clock;
        log.info("DumpService initialized - Dump directory: {}", dumpDir);
    }

    @Override
    public String dump(String content, String prefix, String extension, String sessionId) {
        try {
            Path dumpPath = Paths.get(dumpDir, LocalDate.now(clock).toString());
            Files.createDirectories(dumpPath);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = String.format("%s-%s-%s.%s", sessionId, prefix, timestamp, extension);
            Path filePath = dumpPath.resolve(filename);

            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            String absolutePath = filePath.toAbsolutePath().toString();
            log.debug("Dumped {} bytes to: {}", content.length(), absolutePath);

            return absolutePath;

        } catch (IOException e) {
            log.warn("Failed to dump content (prefix: {}, extension: {}): {}", prefix, extension, e.getMessage());
            return null;
        }
    }

    @Override
    public String dumpRecipeJson(Recipe recipe, String sessionId) throws JsonProcessingException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recipe);
        return dump(json, "result-json", "json", sessionId);
    }

    @Override
    public String dumpRecipeYaml(String yaml, String sessionId) {
        return dump(yaml, "result-yaml", "yaml", sessionId);
    }
}
