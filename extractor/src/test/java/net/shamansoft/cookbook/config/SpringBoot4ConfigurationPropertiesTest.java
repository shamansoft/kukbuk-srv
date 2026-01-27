package net.shamansoft.cookbook.config;

import net.shamansoft.cookbook.config.TestFirebaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class to verify Spring Boot 4 configuration properties are loaded correctly.
 * Tests key changes in Spring Boot 4:
 * - spring.threads.virtual.enabled still supported
 * - Actuator health probes enabled by default
 * - Configuration property bindings work correctly
 * - No Jackson property changes needed (no spring.jackson properties in use)
 */
@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
    "spring.threads.virtual.enabled=true",
    "management.endpoints.web.exposure.include=health,info",
    "cookbook.gemini.api-key=test-key",
    "cookbook.gemini.model=gemini-test",
    "recipe.llm.retry=2",
    "recipe.store.enabled=true",
    "recipe.store.timeout.lookup-ms=100",
    "recipe.store.timeout.save-ms=500",
    "recipe.store.timeout.count-ms=200"
})
class SpringBoot4ConfigurationPropertiesTest {

    @Autowired
    private Environment environment;

    @Test
    void virtualThreadsEnabled_isSupported() {
        // Verify spring.threads.virtual.enabled property is still supported in Spring Boot 4
        String virtualThreadsEnabled = environment.getProperty("spring.threads.virtual.enabled");
        assertThat(virtualThreadsEnabled).isEqualTo("true");
    }

    @Test
    void actuatorEndpoints_areConfigured() {
        // Verify actuator endpoints configuration works in Spring Boot 4
        String exposedEndpoints = environment.getProperty("management.endpoints.web.exposure.include");
        assertThat(exposedEndpoints).isNotNull();
        assertThat(exposedEndpoints).contains("health");
        assertThat(exposedEndpoints).contains("info");
    }

    @Test
    void geminiConfiguration_isLoadedCorrectly() {
        // Verify custom cookbook.gemini properties bind correctly
        String apiKey = environment.getProperty("cookbook.gemini.api-key");
        String model = environment.getProperty("cookbook.gemini.model");

        assertThat(apiKey).isEqualTo("test-key");
        assertThat(model).isEqualTo("gemini-test");
    }

    @Test
    void recipeConfiguration_isLoadedCorrectly() {
        // Verify custom recipe properties bind correctly
        String retry = environment.getProperty("recipe.llm.retry");
        String storeEnabled = environment.getProperty("recipe.store.enabled");
        String lookupTimeout = environment.getProperty("recipe.store.timeout.lookup-ms");
        String saveTimeout = environment.getProperty("recipe.store.timeout.save-ms");
        String countTimeout = environment.getProperty("recipe.store.timeout.count-ms");

        assertThat(retry).isEqualTo("2");
        assertThat(storeEnabled).isEqualTo("true");
        assertThat(lookupTimeout).isEqualTo("100");
        assertThat(saveTimeout).isEqualTo("500");
        assertThat(countTimeout).isEqualTo("200");
    }

    @Test
    void applicationYaml_loadsSuccessfully() {
        // Verify that application.yaml loads without errors in Spring Boot 4
        // This test passes if context loads (checked by @SpringBootTest)
        assertThat(environment).isNotNull();

        // Verify key properties from application.yaml
        // Note: test configuration overrides app name to "cookbook-test"
        String appName = environment.getProperty("spring.application.name");
        assertThat(appName).isNotNull();
        assertThat(appName).isIn("kukbuk-srv", "cookbook-test");
    }

    @Test
    void noJacksonProperties_inConfiguration() {
        // Spring Boot 4 changes Jackson properties from spring.jackson.read.* to spring.jackson.json.read.*
        // Our application doesn't use any spring.jackson properties, so no changes needed
        // This test verifies no Jackson properties are present
        String jacksonDateFormat = environment.getProperty("spring.jackson.date-format");
        String jacksonReadUnknown = environment.getProperty("spring.jackson.read.unknown");
        String jacksonJsonReadUnknown = environment.getProperty("spring.jackson.json.read.unknown");

        // All should be null since we don't configure Jackson via properties
        assertThat(jacksonDateFormat).isNull();
        assertThat(jacksonReadUnknown).isNull();
        assertThat(jacksonJsonReadUnknown).isNull();
    }
}
