package net.shamansoft.cookbook.service.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts the shared extraction schema ({@code llm-recipe-schema.json}) into the form OpenAI
 * Structured Outputs requires in strict mode, so both providers stay on one schema file.
 * <p>
 * Strict mode requires every object to list all of its properties as {@code required} and to
 * set {@code additionalProperties: false}. A property the source schema treats as optional is
 * therefore made nullable instead: the model must emit it, but may emit {@code null}.
 * <p>
 * The input is never modified — the same parsed schema instance is sent to Gemini.
 */
final class OpenAiSchemaAdapter {

    // String formats strict mode accepts. Anything else (the schema uses "uri") is rejected by
    // the API with 400 invalid_json_schema, so the keyword is dropped.
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "date-time", "time", "date", "duration", "email", "hostname", "ipv4", "ipv6", "uuid");

    private OpenAiSchemaAdapter() {
    }

    static Map<String, Object> toStrictSchema(Object schema) {
        if (!(schema instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON schema root must be an object");
        }
        return convert(map);
    }

    private static Map<String, Object> convert(Map<?, ?> source) {
        Map<String, Object> node = new LinkedHashMap<>();
        source.forEach((key, value) -> node.put(String.valueOf(key), value));

        if (node.get("format") instanceof String format && !SUPPORTED_FORMATS.contains(format)) {
            node.remove("format");
        }

        if (node.get("properties") instanceof Map<?, ?> properties) {
            Set<?> required = node.get("required") instanceof List<?> list ? Set.copyOf(list) : Set.of();
            Map<String, Object> strictProperties = new LinkedHashMap<>();
            properties.forEach((name, child) -> {
                Object converted = child instanceof Map<?, ?> childMap ? convert(childMap) : child;
                if (!required.contains(name) && converted instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> optional = (Map<String, Object>) converted;
                    makeNullable(optional);
                }
                strictProperties.put(String.valueOf(name), converted);
            });
            node.put("properties", strictProperties);
            node.put("required", new ArrayList<>(strictProperties.keySet()));
            node.put("additionalProperties", false);
        }

        if (node.get("items") instanceof Map<?, ?> items) {
            node.put("items", convert(items));
        }
        return node;
    }

    private static void makeNullable(Map<String, Object> node) {
        if (node.get("type") instanceof String type) {
            node.put("type", List.of(type, "null"));
        }
        if (node.get("enum") instanceof List<?> values && !values.contains(null)) {
            List<Object> withNull = new ArrayList<>(values);
            withNull.add(null);
            node.put("enum", withNull);
        }
    }
}
