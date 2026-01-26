package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.InvalidRecipeFormatException;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.YamlRecipeParser;
import org.springframework.stereotype.Component;

/**
 * Wrapper around Recipe SDK's YamlRecipeParser.
 * Provides consistent error handling for the mobile API.
 */
@Component
@Slf4j
public class RecipeParser {
    private final YamlRecipeParser yamlParser = new YamlRecipeParser();

    /**
     * Parse a YAML string into a Recipe object.
     *
     * @param yamlContent YAML string containing recipe data
     * @return Parsed Recipe object
     * @throws InvalidRecipeFormatException if YAML is malformed or invalid
     */
    public Recipe parse(String yamlContent) {
        try {
            log.debug("Parsing recipe YAML ({} bytes)", yamlContent.length());
            Recipe recipe = yamlParser.parse(yamlContent);
            log.debug("Successfully parsed recipe: {}", recipe.metadata().title());
            return recipe;
        } catch (Exception e) {
            // Log detailed error information for debugging
            log.error("Failed to parse recipe YAML: {}", e.getMessage());

            // Log the exception stack trace for more details
            if (log.isDebugEnabled()) {
                log.debug("Parse error stack trace:", e);

                // Log a preview of the YAML content
                int previewLength = Math.min(500, yamlContent.length());
                String preview = yamlContent.substring(0, previewLength);
                if (previewLength < yamlContent.length()) {
                    preview += "...";
                }
                log.debug("YAML content preview: {}", preview);
            }

            throw new InvalidRecipeFormatException(
                    "Failed to parse recipe YAML: " + e.getMessage(), e);
        }
    }
}
