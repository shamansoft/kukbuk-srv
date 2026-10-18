package net.shamansoft.cookbook.html.jsonld;

import net.shamansoft.recipe.model.Nutrition;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the fields of a schema.org {@code Recipe} node that need no model: the page publishes them structured.
 * Every value is null when the node lacks it or holds a shape that cannot be read safely; the caller then keeps
 * whatever the model extracted.
 */
public final class JsonLdRecipeReader {

    private static final String RANGE_SEPARATOR = "\\s*(?:-|–|—|to|bis|a|à|до)\\s*";
    private static final Pattern RANGE = Pattern.compile("(\\d+)" + RANGE_SEPARATOR + "(\\d+)");
    // "1 Roast, 6 serving(s)": the number next to a servings word wins over the first number
    private static final Pattern NEXT_TO_SERVINGS = Pattern.compile(
            "(\\d+)(?:" + RANGE_SEPARATOR + "(\\d+))?\\s*(?:serv|portion|people|person|pers|porç|porci|porzi|porc|порц)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INTEGER = Pattern.compile("\\d+");
    // "1,200 mg" groups thousands; "12,5 g" is a decimal comma
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?)|(\\d+(?:[.,]\\d+)?)");
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final int MAX_SERVINGS_DIGITS = 4;

    private JsonLdRecipeReader() {
    }

    /**
     * @param title       {@code name}, entities and markup decoded
     * @param servings    first integer of {@code recipeYield}; a range gives its midpoint, rounded up
     * @param prepTime    {@code prepTime} as {@code 1h 30m}; null when not ISO 8601 or zero
     * @param cookTime    {@code cookTime}, as prepTime
     * @param totalTime   {@code totalTime}, as prepTime
     * @param image       first URL of {@code image} (string, list or {@code ImageObject})
     * @param nutrition   numbers parsed from {@code NutritionInformation}; null when it has none
     * @param ingredients {@code recipeIngredient} lines; {@code PropertyValue} objects joined to one line
     */
    public record JsonLdRecipe(String title, Integer servings, String prepTime, String cookTime, String totalTime,
                               String image, Nutrition nutrition, List<String> ingredients) {
    }

    public static JsonLdRecipe read(JsonNode recipe) {
        return new JsonLdRecipe(
                text(recipe.path("name")),
                servings(recipe.path("recipeYield")),
                duration(recipe.path("prepTime")),
                duration(recipe.path("cookTime")),
                duration(recipe.path("totalTime")),
                image(recipe.path("image")),
                nutrition(recipe.path("nutrition")),
                ingredients(recipe));
    }

    private static String text(JsonNode node) {
        if (!node.isTextual()) return null;
        String text = Jsoup.parse(node.asText()).text().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer servings(JsonNode yield) {
        if (yield.isNumber()) return yield.asInt() > 0 ? yield.asInt() : null;
        if (yield.isArray()) {
            for (JsonNode item : yield) {
                Integer servings = servings(item);
                if (servings != null) return servings;
            }
            return null;
        }
        if (!yield.isTextual()) return null;
        Matcher labelled = NEXT_TO_SERVINGS.matcher(yield.asText());
        if (labelled.find()) return servings(labelled.group(1), labelled.group(2));
        Matcher range = RANGE.matcher(yield.asText());
        if (range.find()) return servings(range.group(1), range.group(2));
        Matcher integer = INTEGER.matcher(yield.asText());
        return integer.find() ? servings(integer.group(), null) : null;
    }

    // a range gives its midpoint, rounded up
    private static Integer servings(String low, String high) {
        if (low.length() > MAX_SERVINGS_DIGITS || (high != null && high.length() > MAX_SERVINGS_DIGITS)) return null;
        int from = Integer.parseInt(low);
        int to = high == null ? from : Integer.parseInt(high);
        if (from <= 0 || to < from) return null;
        return (from + to + 1) / 2;
    }

    private static String duration(JsonNode node) {
        if (!node.isTextual()) return null;
        try {
            Duration duration = Duration.parse(node.asText().trim());
            long minutes = duration.toMinutes();
            if (duration.isNegative() || minutes == 0) return null;
            List<String> parts = new ArrayList<>();
            if (minutes / (24 * 60) > 0) parts.add(minutes / (24 * 60) + "d");
            if (minutes / 60 % 24 > 0) parts.add(minutes / 60 % 24 + "h");
            if (minutes % 60 > 0) parts.add(minutes % 60 + "m");
            return String.join(" ", parts);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String image(JsonNode image) {
        if (image.isArray()) {
            for (JsonNode item : image) {
                String url = image(item);
                if (url != null) return url;
            }
            return null;
        }
        if (image.isObject()) {
            String url = text(image.path("url"));
            return url != null ? url : text(image.path("contentUrl"));
        }
        return text(image);
    }

    private static Nutrition nutrition(JsonNode node) {
        if (!node.isObject()) return null;
        Double kcal = number(node.path("calories"));
        Integer calories = kcal == null ? null : (int) Math.round(kcal);
        Double protein = number(node.path("proteinContent"));
        Double carbohydrates = number(node.path("carbohydrateContent"));
        Double fat = number(node.path("fatContent"));
        Double fiber = number(node.path("fiberContent"));
        Double sugar = number(node.path("sugarContent"));
        Double sodium = sodiumMilligrams(node.path("sodiumContent"));
        if (calories == null && protein == null && carbohydrates == null && fat == null
                && fiber == null && sugar == null && sodium == null) {
            return null;
        }
        return new Nutrition(text(node.path("servingSize")), calories, protein, carbohydrates, fat, fiber, sugar,
                sodium, null);
    }

    // "240 kcal", "12,5 g", 240
    private static Double number(JsonNode node) {
        if (node.isNumber()) return node.asDouble() >= 0 ? node.asDouble() : null;
        if (!node.isTextual()) return null;
        Matcher m = NUMBER.matcher(node.asText());
        if (!m.find()) return null;
        return m.group(1) != null
                ? Double.parseDouble(m.group(1).replace(",", ""))
                : Double.parseDouble(m.group(2).replace(',', '.'));
    }

    private static Double sodiumMilligrams(JsonNode node) {
        Double value = number(node);
        if (value == null || !node.isTextual()) return value;
        String unit = node.asText().toLowerCase().replaceAll("[^a-zµ]", "");
        return unit.equals("g") || unit.equals("grams") ? value * 1000 : value;
    }

    /** One word per ingredient line (its longest), lower-cased: what to look for elsewhere to recognise the list. */
    public static List<String> keyWords(List<String> lines) {
        List<String> keys = new ArrayList<>();
        for (String line : lines) {
            Matcher m = WORD.matcher(Parser.unescapeEntities(line, false).toLowerCase(Locale.ROOT));
            String longest = null;
            while (m.find()) {
                if (longest == null || m.group().length() > longest.length()) longest = m.group();
            }
            if (longest != null) keys.add(longest);
        }
        return keys;
    }

    /** The {@code recipeIngredient} lines; some pages wrap each line in a {@code PropertyValue} object. */
    public static List<String> ingredients(JsonNode recipe) {
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
}
