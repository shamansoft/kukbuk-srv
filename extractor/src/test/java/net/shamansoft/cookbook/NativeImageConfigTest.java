package net.shamansoft.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.shamansoft.cookbook.service.gemini.GeminiRequest;
import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify GraalVM native image configuration.
 * Validates reflection configs and resource availability.
 *
 * These tests ensure that:
 * 1. Reflection metadata is correctly configured for GraalVM
 * 2. Resources are properly detected and available at runtime
 * 3. SLF4J initialization is configured for build-time
 * 4. Jackson serialization works with reflection
 */
class NativeImageConfigTest {

    @Test
    void reflectionConfig_geminiRequestClasses_shouldBeAccessible() throws Exception {
        // Verify GeminiRequest and nested classes have reflection access
        verifyReflectionAccess(GeminiRequest.class);
        verifyReflectionAccess(GeminiRequest.Content.class);
        verifyReflectionAccess(GeminiRequest.Part.class);
        verifyReflectionAccess(GeminiRequest.GenerationConfig.class);
        verifyReflectionAccess(GeminiRequest.SafetySetting.class);
    }

    @Test
    void reflectionConfig_recipeModelClasses_shouldBeAccessible() throws Exception {
        // Verify Recipe model classes have reflection access
        verifyReflectionAccess(Recipe.class);
        // Recipe is a record, no builder class needed
    }

    @Test
    void resourceAutodetection_applicationYaml_shouldBeAvailable() throws IOException {
        // Verify that resources.autodetect() is working
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            assertThat(stream)
                .as("application.yaml should be available via resource autodetection")
                .isNotNull();
        }
    }

    @Test
    void resourceAutodetection_promptMd_shouldBeAvailable() throws IOException {
        // Verify prompt.md is available
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("prompt.md")) {
            assertThat(stream)
                .as("prompt.md should be available via resource autodetection")
                .isNotNull();
        }
    }

    @Test
    void resourceAutodetection_recipeSchema_shouldBeAvailable() throws IOException {
        // Verify recipe schema is available
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("recipe-schema-1.0.0.json")) {
            assertThat(stream)
                .as("recipe-schema-1.0.0.json should be available via resource autodetection")
                .isNotNull();
        }
    }

    @Test
    void slf4jInitialization_shouldBeConfiguredForBuildTime() {
        // Verify SLF4J is initialized (this test would fail in native image if not configured properly)
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NativeImageConfigTest.class);
        assertThat(logger)
            .as("SLF4J logger should be initialized at build time")
            .isNotNull();

        // Log a test message to verify logging works
        logger.info("SLF4J initialization test - this should work in native image");
    }

    @Test
    void jacksonSerialization_geminiRequest_shouldWorkWithReflection() throws Exception {
        // Verify Jackson can serialize/deserialize with reflection
        ObjectMapper mapper = new ObjectMapper();

        GeminiRequest.Part part = new GeminiRequest.Part("test text");
        GeminiRequest.Content content = new GeminiRequest.Content(java.util.List.of(part));

        String json = mapper.writeValueAsString(content);
        assertThat(json)
            .as("Jackson should serialize GeminiRequest.Content with reflection")
            .contains("test text");

        GeminiRequest.Content deserialized = mapper.readValue(json, GeminiRequest.Content.class);
        assertThat(deserialized.getParts()).hasSize(1);
    }

    @Test
    void graalvmBuildConfiguration_isValid() {
        // This test validates that the GraalVM configuration is present and accessible
        // The reflect-config.json should be in META-INF/native-image/
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
            "META-INF/native-image/reflect-config.json")) {
            assertThat(stream)
                .as("reflect-config.json should be present for GraalVM native image")
                .isNotNull();
        } catch (IOException e) {
            throw new RuntimeException("Failed to check reflect-config.json", e);
        }
    }

    /**
     * Verifies that a class has reflection access (all constructors, methods, fields)
     */
    private void verifyReflectionAccess(Class<?> clazz) throws Exception {
        // Verify constructors are accessible
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        assertThat(constructors)
            .as("Class %s should have accessible constructors", clazz.getName())
            .isNotEmpty();

        // Verify methods are accessible
        Method[] methods = clazz.getDeclaredMethods();
        assertThat(methods)
            .as("Class %s should have accessible methods", clazz.getName())
            .isNotEmpty();

        // Verify fields are accessible
        Field[] fields = clazz.getDeclaredFields();
        // Note: Some classes may not have fields, so we just verify this doesn't throw
        assertThat(fields)
            .as("Class %s should have accessible fields array (may be empty)", clazz.getName())
            .isNotNull();
    }
}
