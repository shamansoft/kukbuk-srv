package net.shamansoft.recipe.parser;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.shamansoft.recipe.model.Recipe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Parser for converting YAML content to Recipe model objects.
 * Uses Jackson YAML mapper with support for Java 8 date/time types.
 * Configured to be lenient - ignores unknown properties and provides detailed error messages.
 */
public class YamlRecipeParser {

    private final ObjectMapper mapper;

    /**
     * Creates a new parser with default configuration.
     */
    public YamlRecipeParser() {
        this.mapper = createDefaultMapper();
    }

    /**
     * Creates a new parser with a custom ObjectMapper.
     *
     * @param mapper the ObjectMapper to use for parsing
     */
    public YamlRecipeParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Creates a default ObjectMapper configured for YAML parsing with Java 8 date/time support.
     * Configured to be lenient:
     * - Ignores unknown properties
     * - Accepts empty strings as null
     * - Coerces empty arrays to null for scalar properties
     *
     * @return configured ObjectMapper
     */
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());

        // Make the parser more lenient - ignore unknown properties
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Accept empty strings as null
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        // Accept empty arrays as null for non-collection properties
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

        return mapper;
    }

    /**
     * Parses a YAML string into a Recipe object.
     *
     * @param yaml the YAML string to parse
     * @return the parsed Recipe object
     * @throws RecipeParseException if parsing fails
     */
    public Recipe parse(String yaml) throws RecipeParseException {
        try {
            return mapper.readValue(yaml, Recipe.class);
        } catch (JsonProcessingException e) {
            throw createDetailedParseException("Failed to parse YAML content", e, yaml);
        }
    }

    /**
     * Parses a YAML file into a Recipe object.
     *
     * @param path the path to the YAML file
     * @return the parsed Recipe object
     * @throws RecipeParseException if parsing fails
     */
    public Recipe parse(Path path) throws RecipeParseException {
        return parse(path.toFile());
    }

    /**
     * Parses a YAML file into a Recipe object.
     *
     * @param file the YAML file to parse
     * @return the parsed Recipe object
     * @throws RecipeParseException if parsing fails
     */
    public Recipe parse(File file) throws RecipeParseException {
        try {
            return mapper.readValue(file, Recipe.class);
        } catch (IOException e) {
            if (e instanceof JsonProcessingException jpe) {
                throw createDetailedParseException("Failed to parse YAML file: " + file.getAbsolutePath(), jpe, null);
            }
            throw new RecipeParseException("Failed to read YAML file: " + file.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }

    /**
     * Parses YAML content from an InputStream into a Recipe object.
     *
     * @param inputStream the input stream containing YAML content
     * @return the parsed Recipe object
     * @throws RecipeParseException if parsing fails
     */
    public Recipe parse(InputStream inputStream) throws RecipeParseException {
        try {
            return mapper.readValue(inputStream, Recipe.class);
        } catch (IOException e) {
            if (e instanceof JsonProcessingException jpe) {
                throw createDetailedParseException("Failed to parse YAML from input stream", jpe, null);
            }
            throw new RecipeParseException("Failed to read YAML from input stream: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a detailed parse exception with location information and context.
     *
     * @param baseMessage the base error message
     * @param e           the JSON processing exception
     * @param yamlContent the YAML content (optional, for context)
     * @return a detailed RecipeParseException
     */
    private RecipeParseException createDetailedParseException(String baseMessage, JsonProcessingException e, String yamlContent) {
        StringBuilder message = new StringBuilder(baseMessage);

        // Add specific error details
        message.append(": ").append(e.getOriginalMessage());

        // Add location information if available
        JsonLocation location = e.getLocation();
        if (location != null) {
            message.append(" at line ").append(location.getLineNr())
                    .append(", column ").append(location.getColumnNr());
        }

        // Add content context if available (first 500 chars or around error location)
        if (yamlContent != null && !yamlContent.isEmpty()) {
            int contextLength = Math.min(500, yamlContent.length());
            String context = yamlContent.substring(0, contextLength);
            if (contextLength < yamlContent.length()) {
                context += "...";
            }
            message.append("\nYAML preview: ").append(context);
        }

        return new RecipeParseException(message.toString(), e);
    }

    /**
     * Gets the underlying ObjectMapper used by this parser.
     *
     * @return the ObjectMapper
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
