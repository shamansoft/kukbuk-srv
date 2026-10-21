package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecipeCategories Tests")
class RecipeCategoriesTest {

    @Test
    @DisplayName("Keeps allowed values, lowercased, deduplicated, in order")
    void keepsAllowedValuesInOrder() {
        List<String> result = RecipeCategories.normalize(
                Arrays.asList("Dinner", " main ", "Main Course", null, "dinner", "dessert", "Huvudrätt"));

        assertThat(result).containsExactly("dinner", "main", "dessert");
    }

    @Test
    @DisplayName("Returns an empty list for null or no allowed values")
    void returnsEmptyListForNullOrNoAllowedValues() {
        assertThat(RecipeCategories.normalize(null)).isEmpty();
        assertThat(RecipeCategories.normalize(List.of("Christmas", "Seafood"))).isEmpty();
    }

    @Test
    @DisplayName("Matches the category enum in llm-recipe-schema.json")
    void matchesSchemaEnum() throws Exception {
        JsonNode schema;
        try (InputStream in = getClass().getResourceAsStream("/llm-recipe-schema.json")) {
            schema = new ObjectMapper().readTree(in);
        }
        JsonNode values = schema.at("/properties/recipes/items/properties/metadata/properties/category/items/enum");
        List<String> schemaValues = new ArrayList<>();
        values.forEach(v -> schemaValues.add(v.asText()));

        assertThat(schemaValues).isEqualTo(RecipeCategories.ALLOWED);
    }
}
