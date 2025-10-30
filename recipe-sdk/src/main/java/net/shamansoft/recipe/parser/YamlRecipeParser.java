package net.shamansoft.recipe.parser;

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
     * Parses a YAML string into a Recipe object.
     *
     * @param yaml the YAML string to parse
     * @return the parsed Recipe object
     * @throws RecipeParseException if parsing fails
     */
    public Recipe parse(String yaml) throws RecipeParseException {
        try {
            return mapper.readValue(yaml, Recipe.class);
        } catch (IOException e) {
            throw new RecipeParseException("Failed to parse YAML content", e);
        }
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
            throw new RecipeParseException("Failed to parse YAML file: " + file.getAbsolutePath(), e);
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
            throw new RecipeParseException("Failed to parse YAML from input stream", e);
        }
    }

    /**
     * Creates a default ObjectMapper configured for YAML parsing with Java 8 date/time support.
     *
     * @return configured ObjectMapper
     */
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
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
