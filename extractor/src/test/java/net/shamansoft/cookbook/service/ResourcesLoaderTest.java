package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = {ResourcesLoader.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.main.banner-mode=off"
        })
class ResourcesLoaderTest {

    @Autowired
    private ResourcesLoader resourcesLoader;

    @Test
    void loadSimpleYaml_shouldLoadAndTransformSuccessfully() throws IOException {
        // Load test resource from test/resources directory
        String result = resourcesLoader.loadTextFile("classpath:test-simple.yaml");

        assertThat(result).contains("name: test-recipe");
        assertThat(result).contains("description: A simple test recipe");
    }

    @Test
    void loadComplexYaml_shouldPreserveStructure() throws IOException {
        String result = resourcesLoader.loadTextFile("classpath:test-complex.yaml");

        assertThat(result).contains("ingredients:");
        assertThat(result).contains("- name: flour");
        assertThat(result).contains("quantity: 200");
        assertThat(result).contains("unit: g");
    }

    @Test
    void loadTextFileWithLists_shouldHandleListsCorrectly() throws IOException {
        String result = resourcesLoader.loadTextFile("classpath:test-with-lists.yaml");

        assertThat(result).contains("steps:");
        assertThat(result).contains("- \"Mix ingredients\"");
        assertThat(result).contains("- \"Bake for 30 minutes\"");
    }

    @Test
    void loadEmptyYaml_shouldReturnEmptyString() throws IOException {
        String result = resourcesLoader.loadTextFile("classpath:test-empty.yaml");

        assertThat(result).isEqualTo("");
    }

    @Test
    void loadNonExistentYaml_shouldThrowException() {
        assertThatThrownBy(() -> resourcesLoader.loadTextFile("classpath:non-existent.yaml"))
                .isInstanceOf(IOException.class);
    }
}