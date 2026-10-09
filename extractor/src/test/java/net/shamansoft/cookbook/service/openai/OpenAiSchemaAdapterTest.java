package net.shamansoft.cookbook.service.openai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiSchemaAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Object parse(String json) {
        return objectMapper.readValue(json, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> schema, String name) {
        return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get(name);
    }

    @Test
    void requiresEveryPropertyAndForbidsAdditionalOnes() {
        Object schema = parse("""
                {"type": "object", "required": ["title"],
                 "properties": {"title": {"type": "string"}, "author": {"type": "string"}}}
                """);

        Map<String, Object> strict = OpenAiSchemaAdapter.toStrictSchema(schema);

        assertThat(strict.get("required")).isEqualTo(List.of("title", "author"));
        assertThat(strict.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void makesOptionalPropertiesNullableAndLeavesRequiredOnesAlone() {
        Object schema = parse("""
                {"type": "object", "required": ["title"],
                 "properties": {
                   "title": {"type": "string"},
                   "author": {"type": "string"},
                   "tags": {"type": "array", "items": {"type": "string"}},
                   "difficulty": {"type": "string", "enum": ["easy", "hard"]}}}
                """);

        Map<String, Object> strict = OpenAiSchemaAdapter.toStrictSchema(schema);

        assertThat(property(strict, "title").get("type")).isEqualTo("string");
        assertThat(property(strict, "author").get("type")).isEqualTo(List.of("string", "null"));
        assertThat(property(strict, "tags").get("type")).isEqualTo(List.of("array", "null"));
        assertThat(property(strict, "difficulty").get("type")).isEqualTo(List.of("string", "null"));
        assertThat((List<Object>) property(strict, "difficulty").get("enum")).containsExactly("easy", "hard", null);
    }

    @Test
    void convertsNestedObjectsAndArrayItems() {
        Object schema = parse("""
                {"type": "object", "required": ["ingredients"],
                 "properties": {
                   "ingredients": {"type": "array", "minItems": 1,
                     "items": {"type": "object", "required": ["item"],
                       "properties": {"item": {"type": "string"}, "amount": {"type": "number", "minimum": 0}}}},
                   "storage": {"type": "object", "properties": {"freezer": {"type": "string"}}}}}
                """);

        Map<String, Object> strict = OpenAiSchemaAdapter.toStrictSchema(schema);

        @SuppressWarnings("unchecked")
        Map<String, Object> ingredient = (Map<String, Object>) property(strict, "ingredients").get("items");
        assertThat(ingredient.get("required")).isEqualTo(List.of("item", "amount"));
        assertThat(ingredient.get("additionalProperties")).isEqualTo(false);
        assertThat(property(ingredient, "amount").get("type")).isEqualTo(List.of("number", "null"));
        // Constraints other than unsupported formats are kept
        assertThat(property(ingredient, "amount").get("minimum")).isEqualTo(0);
        assertThat(property(strict, "ingredients").get("minItems")).isEqualTo(1);

        Map<String, Object> storage = property(strict, "storage");
        assertThat(storage.get("type")).isEqualTo(List.of("object", "null"));
        assertThat(storage.get("required")).isEqualTo(List.of("freezer"));
        assertThat(storage.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void dropsFormatsOpenAiRejectsAndKeepsSupportedOnes() {
        Object schema = parse("""
                {"type": "object", "required": ["source", "date_created"],
                 "properties": {
                   "source": {"type": "string", "format": "uri"},
                   "date_created": {"type": "string", "format": "date"}}}
                """);

        Map<String, Object> strict = OpenAiSchemaAdapter.toStrictSchema(schema);

        assertThat(property(strict, "source")).doesNotContainKey("format");
        assertThat(property(strict, "date_created").get("format")).isEqualTo("date");
    }

    @Test
    void doesNotModifyTheSourceSchema() {
        String json = """
                {"type": "object", "required": ["title"],
                 "properties": {"title": {"type": "string"}, "source": {"type": "string", "format": "uri"}}}
                """;
        Object schema = parse(json);

        OpenAiSchemaAdapter.toStrictSchema(schema);

        // The same parsed instance is sent to Gemini, so it must be untouched
        assertThat(schema).isEqualTo(parse(json));
    }

    @Test
    void rejectsNonObjectRoot() {
        assertThatThrownBy(() -> OpenAiSchemaAdapter.toStrictSchema(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realExtractionSchemaBecomesFullyStrict() throws Exception {
        Object schema;
        try (InputStream in = getClass().getResourceAsStream("/llm-recipe-schema.json")) {
            schema = objectMapper.readValue(in, Object.class);
        }

        Map<String, Object> strict = OpenAiSchemaAdapter.toStrictSchema(schema);

        assertEveryObjectIsStrict(strict, "$");
        assertThat(objectMapper.writeValueAsString(strict)).doesNotContain("\"uri\"");
    }

    @SuppressWarnings("unchecked")
    private void assertEveryObjectIsStrict(Map<String, Object> node, String path) {
        if (node.get("properties") instanceof Map<?, ?> properties) {
            assertThat(node.get("additionalProperties")).as(path + " additionalProperties").isEqualTo(false);
            assertThat((List<Object>) node.get("required")).as(path + " required")
                    .containsExactlyElementsOf((Iterable<Object>) properties.keySet());
            properties.forEach((name, child) ->
                    assertEveryObjectIsStrict((Map<String, Object>) child, path + "." + name));
        }
        if (node.get("items") instanceof Map<?, ?> items) {
            assertEveryObjectIsStrict((Map<String, Object>) items, path + "[]");
        }
    }
}
