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

/**
 * Service for dumping intermediate processing states to disk for debugging.
 * <p>
 * Only active in local environment via @Profile("local").
 * Dumps are written to a configurable directory (default: /tmp/sar-srv/dumps/)
 * with timestamped filenames for correlation and easy inspection.
 * <p>
 * File naming pattern:
 * {path-from-property}/yyyy-mm-dd/{sessionId}-{prefix}-{timestamp}.{extension}
 * Example:
 * /tmp/sar-srv/dumps/2026-02-01/abc123def456-raw-html-1738425600000.html
 */
@Service
@Slf4j
@Profile("local") // Only active in local environment
public class DumpService {

    private final String dumpDir;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DumpService(
            @Value("${cookbook.debug.dump-dir}") String dumpDir,
            ObjectMapper objectMapper,
            Clock clock) {
        this.dumpDir = dumpDir;
        this.objectMapper = objectMapper;
        this.clock = clock;
        log.info("DumpService initialized - Dump directory: {}", dumpDir);
    }

    /**
     * Dumps content to a file and returns the absolute path.
     *
     * @param content   Content to write
     * @param prefix    File prefix (e.g., "raw-html", "cleaned-html")
     * @param extension File extension (e.g., "html", "json", "yaml")
     * @param sessionId Unique session identifier (e.g., contentHash or timestamp)
     * @return Absolute path to the dumped file
     */
    public String dump(String content, String prefix, String extension, String sessionId) {
        try {
            // Create dumps directory with daily subfolder: {dumpDir}/yyyy-mm-dd
            Path dumpPath = Paths.get(dumpDir, LocalDate.now(clock).toString());
            Files.createDirectories(dumpPath);

            // Generate filename: {sessionId}-{prefix}-{timestamp}.{extension}
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = String.format("%s-%s-%s.%s", sessionId, prefix, timestamp, extension);
            Path filePath = dumpPath.resolve(filename);

            // Write content to file
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            String absolutePath = filePath.toAbsolutePath().toString();
            log.debug("Dumped {} bytes to: {}", content.length(), absolutePath);

            return absolutePath;

        } catch (IOException e) {
            log.warn("Failed to dump content (prefix: {}, extension: {}): {}", prefix, extension, e.getMessage());
            return null;
        }
    }

    /**
     * Dumps Recipe object as JSON with pretty printing.
     *
     * @param recipe    Recipe to serialize
     * @param sessionId Unique session identifier
     * @return Absolute path to the dumped file
     * @throws JsonProcessingException if JSON serialization fails
     */
    public String dumpRecipeJson(Recipe recipe, String sessionId) throws JsonProcessingException {
        // Serialize Recipe to pretty-printed JSON
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recipe);

        // Call dump() with "result-json" prefix
        return dump(json, "result-json", "json", sessionId);
    }

    /**
     * Dumps Recipe as YAML.
     *
     * @param yaml      YAML content
     * @param sessionId Unique session identifier
     * @return Absolute path to the dumped file
     */
    public String dumpRecipeYaml(String yaml, String sessionId) {
        // Call dump() with "result-yaml" prefix
        return dump(yaml, "result-yaml", "yaml", sessionId);
    }
}
