package net.shamansoft.cookbook.html.strategy;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts JSON-LD schema.org Recipe structured data from HTML, followed by the recipe card's ingredient block
 * (see {@link RecipeCardCutter}) so the model can see the section headers JSON-LD lacks.
 */
@Component
@Slf4j
@Order(1)
public class StructuredDataStrategy implements CleanupStrategy {

    private final HtmlCleanupConfig config;
    private final ObjectMapper objectMapper;
    // Some pages put raw CR/LF inside JSON-LD strings; accept them without touching the shared mapper
    private final ObjectReader jsonLdReader;
    private final MeterRegistry meterRegistry;

    public StructuredDataStrategy(HtmlCleanupConfig config, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.jsonLdReader = objectMapper.reader().with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<String> clean(String html) {
        if (!config.isEnabled() || !config.getStructuredData().isEnabled()) return Optional.empty();
        try {
            Document doc = Jsoup.parse(html);
            Elements scripts = doc.select("script[type=application/ld+json]");

            for (Element script : scripts) {
                try {
                    JsonNode json = jsonLdReader.readTree(script.html());
                    List<JsonNode> candidates = findRecipeNodes(json);

                    for (JsonNode node : candidates) {
                        if (isRecipeType(node)) {
                            int completeness = scoreCompleteness(node);
                            if (completeness >= config.getStructuredData().getMinCompleteness()) {
                                log.debug("Found structured recipe data, completeness: {}%", completeness);
                                String jsonString = objectMapper.writeValueAsString(node);
                                return Optional.of(withIngredientCard(doc, node, jsonString));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Invalid JSON-LD in script tag, skipping", e);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing HTML for structured data", e);
        }
        return Optional.empty();
    }

    @Override
    public Strategy getStrategy() {
        return Strategy.STRUCTURED_DATA;
    }

    private String withIngredientCard(Document doc, JsonNode recipe, String jsonString) {
        if (!config.getStructuredData().isIncludeIngredientCard()) return jsonString;
        Optional<String> card;
        try {
            card = RecipeCardCutter.cut(doc, ingredientLines(recipe));
        } catch (Exception e) {
            // the card is an extra; never lose the JSON-LD over it
            log.debug("Ingredient card cut failed, sending JSON-LD alone", e);
            card = Optional.empty();
        }
        meterRegistry.counter("html.preprocessing.ingredient_card", "found", String.valueOf(card.isPresent()))
                .increment();
        return card
                .map(block -> jsonString + "\n\n<" + RecipeCardCutter.TAG + ">\n" + block
                        + "\n</" + RecipeCardCutter.TAG + ">")
                .orElse(jsonString);
    }

    // recipeIngredient is usually strings; some pages wrap each line in a PropertyValue object
    private List<String> ingredientLines(JsonNode recipe) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : recipe.path("recipeIngredient")) {
            if (item.isTextual()) {
                lines.add(item.asText());
            } else if (item.isObject()) {
                StringBuilder line = new StringBuilder();
                for (var field : item.properties()) {
                    if (!field.getKey().startsWith("@") && field.getValue().isTextual()) {
                        line.append(field.getValue().asText()).append(' ');
                    }
                }
                lines.add(line.toString().trim());
            }
        }
        return lines;
    }

    private List<JsonNode> findRecipeNodes(JsonNode json) {
        List<JsonNode> candidates = new ArrayList<>();
        Iterable<JsonNode> roots = json.isArray() ? json : List.of(json);
        for (JsonNode root : roots) {
            JsonNode graph = root.path("@graph");
            if (graph.isArray()) {
                graph.forEach(node -> addWithMainEntity(node, candidates));
            } else {
                addWithMainEntity(root, candidates);
            }
        }
        return candidates;
    }

    private void addWithMainEntity(JsonNode node, List<JsonNode> candidates) {
        candidates.add(node);
        JsonNode mainEntity = node.path("mainEntity");
        if (mainEntity.isArray()) {
            mainEntity.forEach(candidates::add);
        } else if (mainEntity.isObject()) {
            candidates.add(mainEntity);
        }
    }

    private boolean isRecipeType(JsonNode node) {
        if (node.has("@type")) {
            JsonNode type = node.get("@type");
            if (type.isTextual()) return "Recipe".equals(type.asText());
            if (type.isArray()) {
                for (JsonNode t : type) if ("Recipe".equals(t.asText())) return true;
            }
        }
        return false;
    }

    private int scoreCompleteness(JsonNode recipe) {
        int score = 0;
        if (recipe.has("name")) score += 20;
        if (recipe.has("recipeIngredient")) score += 20;
        if (recipe.has("recipeInstructions")) score += 20;
        if (recipe.has("totalTime")) score += 10;
        if (recipe.has("recipeYield")) score += 10;
        if (recipe.has("description")) score += 10;
        if (recipe.has("image")) score += 10;
        return Math.min(100, score);
    }
}
