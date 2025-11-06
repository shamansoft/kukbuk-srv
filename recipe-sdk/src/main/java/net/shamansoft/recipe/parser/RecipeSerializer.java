package net.shamansoft.recipe.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.shamansoft.recipe.model.Recipe;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;

/**
 * Serializer for converting Recipe model objects to YAML format.
 * Uses Jackson YAML mapper with support for Java 8 date/time types.
 */
public class RecipeSerializer {

    private final ObjectMapper mapper;

    /**
     * Creates a new serializer with default configuration.
     */
    public RecipeSerializer() {
        this.mapper = createDefaultMapper();
    }

    /**
     * Creates a new serializer with a custom ObjectMapper.
     *
     * @param mapper the ObjectMapper to use for serialization
     */
    public RecipeSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Serializes a Recipe object to a YAML string.
     *
     * @param recipe the Recipe to serialize
     * @return YAML string representation
     * @throws RecipeSerializeException if serialization fails
     */
    public String serialize(Recipe recipe) throws RecipeSerializeException {
        try {
            return mapper.writeValueAsString(recipe);
        } catch (IOException e) {
            throw new RecipeSerializeException("Failed to serialize recipe to YAML", e);
        }
    }

    /**
     * Serializes a Recipe object to a YAML file.
     *
     * @param recipe the Recipe to serialize
     * @param file   the file to write to
     * @throws RecipeSerializeException if serialization fails
     */
    public void serialize(Recipe recipe, File file) throws RecipeSerializeException {
        try {
            mapper.writeValue(file, recipe);
        } catch (IOException e) {
            throw new RecipeSerializeException("Failed to serialize recipe to file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Serializes a Recipe object to a YAML file.
     *
     * @param recipe the Recipe to serialize
     * @param path   the path to write to
     * @throws RecipeSerializeException if serialization fails
     */
    public void serialize(Recipe recipe, Path path) throws RecipeSerializeException {
        serialize(recipe, path.toFile());
    }

    /**
     * Serializes a Recipe object to an OutputStream.
     *
     * @param recipe       the Recipe to serialize
     * @param outputStream the output stream to write to
     * @throws RecipeSerializeException if serialization fails
     */
    public void serialize(Recipe recipe, OutputStream outputStream) throws RecipeSerializeException {
        try {
            mapper.writeValue(outputStream, recipe);
        } catch (IOException e) {
            throw new RecipeSerializeException("Failed to serialize recipe to output stream", e);
        }
    }

    /**
     * Serializes a Recipe object to a Writer.
     *
     * @param recipe the Recipe to serialize
     * @param writer the writer to write to
     * @throws RecipeSerializeException if serialization fails
     */
    public void serialize(Recipe recipe, Writer writer) throws RecipeSerializeException {
        try {
            mapper.writeValue(writer, recipe);
        } catch (IOException e) {
            throw new RecipeSerializeException("Failed to serialize recipe to writer", e);
        }
    }

    /**
     * Creates a default ObjectMapper configured for YAML serialization with Java 8 date/time support.
     *
     * @return configured ObjectMapper
     */
    private static ObjectMapper createDefaultMapper() {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // Don't write "---" at the start
                .build();

        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);  // Write dates as strings
        return mapper;
    }

    /**
     * Gets the underlying ObjectMapper used by this serializer.
     *
     * @return the ObjectMapper
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
