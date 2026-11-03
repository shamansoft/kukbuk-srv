package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter.Row;
import net.shamansoft.cookbook.service.gemini.JsonLdEnrichment;
import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the recipe on the JSON-LD path from the fields code read and the model's enrichment, and checks the
 * model's ingredient rows against the JSON-LD ingredient lines: every line used, no ingredient of a line listed
 * twice, nothing that neither the lines nor the recipe card hold.
 */
final class JsonLdRecipeAssembler {

    private static final String MAIN = "main";
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final Pattern QUANTITY = Pattern.compile("[\\d½¼¾⅓⅔⅛⅜⅝⅞]");
    private static final int MAX_QUOTE = 80;

    private JsonLdRecipeAssembler() {
    }

    /**
     * @param recipe       the recipe, built even when there are violations (for the feedback attempt)
     * @param violations   the coverage rules the answer breaks, worded as feedback for the model
     * @param cardOnlyRows ingredients the model took from the card because no JSON-LD line holds them
     */
    record Assembly(Recipe recipe, List<String> violations, int cardOnlyRows) {
        boolean ok() {
            return violations.isEmpty();
        }
    }

    /**
     * @param fields    what code read from the JSON-LD
     * @param cardRows  the card's lines when they line up with the JSON-LD lines and have group headings (they then
     *                  give each line's section); null otherwise
     * @param card      the card block the model saw, or null
     * @param answer    the model's enrichment
     * @param sourceUrl the page URL, to resolve a relative image
     */
    static Assembly assemble(JsonLdRecipe fields, List<Row> cardRows, String card, JsonLdEnrichment answer,
                             String sourceUrl) {
        List<String> lines = fields.ingredients();
        List<String> violations = new ArrayList<>();
        String cardText = card == null ? null : Jsoup.parse(card).text().toLowerCase(Locale.ROOT);
        Set<Integer> used = new HashSet<>();
        Set<String> seen = new HashSet<>();
        List<Ingredient> ingredients = new ArrayList<>();
        int cardOnly = 0;
        String section = MAIN;

        for (JsonLdEnrichment.Row row : answer.ingredients()) {
            Integer index = row.sourceIndex();
            String item = row.item() == null ? "" : row.item().strip();
            if (index != null && (index < 0 || index >= lines.size())) {
                violations.add("Row \"%s\" has source_index %d, but the lines are numbered 0 to %d."
                        .formatted(quote(item), index, lines.size() - 1));
                continue;
            }
            if (index != null) used.add(index);
            if (row.isHeader()) {
                // a line with a quantity is an ingredient: as a header it would cover its line and drop the ingredient
                if (index != null && QUANTITY.matcher(lines.get(index)).find()) {
                    violations.add(("Line %d (\"%s\") has a quantity, so it is an ingredient, not a section header. "
                            + "Give it an ingredient row.").formatted(index, quote(lines.get(index))));
                    continue;
                }
                String name = RecipeCardCutter.groupName(item);
                section = name != null ? name : MAIN;
                continue;
            }
            if (item.isEmpty()) {
                violations.add("A row for line %s has no item.".formatted(index));
                continue;
            }
            if (index == null && (cardText == null || !mentions(cardText, item))) {
                violations.add(("Row \"%s\" has no source_index and is not in <%s>. Give it the number of the "
                        + "line it comes from, or drop it if no line holds it.").formatted(quote(item),
                        RecipeCardCutter.TAG));
                continue;
            }
            if (!seen.add(String.join("|", String.valueOf(index), normalized(item), normalized(row.amount()),
                    normalized(row.unit())))) {
                // the same quantity twice is a repeat; "115 g butter, plus extra for greasing" is two rows
                violations.add(index == null
                        ? "The row \"%s\" without source_index is there twice. Return each ingredient once."
                        .formatted(quote(item))
                        : "Line %d (\"%s\") has the row \"%s\" twice. Return each ingredient of a line once."
                        .formatted(index, quote(lines.get(index)), quote(item)));
                continue;
            }
            if (index == null) cardOnly++;
            String component = component(row, index, cardRows, section, ingredients);
            ingredients.add(new Ingredient(item, row.amount(), row.unit(), row.notes(), row.optional(),
                    row.substitutions(), component));
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!used.contains(i)) {
                violations.add("Line %d (\"%s\") has no row. Every line from 0 to %d needs at least one row with its "
                        .formatted(i, quote(lines.get(i)), lines.size() - 1) + "source_index.");
            }
        }
        if (ingredients.isEmpty()) violations.add("The answer has no ingredient rows.");

        return new Assembly(recipe(fields, answer, ingredients, sourceUrl), List.copyOf(violations), cardOnly);
    }

    /** The card's group for the row's line when the card gives sections; else the model's, or the header above. */
    private static String component(JsonLdEnrichment.Row row, Integer index, List<Row> cardRows, String section,
                                    List<Ingredient> previous) {
        if (cardRows != null) {
            Row cardRow = index != null ? cardRows.get(index) : cardRow(row.item(), cardRows);
            if (cardRow == null) return previous.isEmpty() ? MAIN : previous.getLast().component();
            String group = cardRow.group();
            return group != null ? group : MAIN;
        }
        String model = row.component();
        return model == null || model.isBlank() || MAIN.equalsIgnoreCase(model.strip()) ? section : model.strip();
    }

    private static Recipe recipe(JsonLdRecipe fields, JsonLdEnrichment answer, List<Ingredient> ingredients,
                                 String sourceUrl) {
        String image = ImageUrlResolver.resolve(fields.image(), sourceUrl).orElse(null);
        RecipeMetadata metadata = new RecipeMetadata(
                JsonLdFieldOverwriter.title(fields.title(), answer.title()),
                sourceUrl,
                fields.author(),
                or(fields.language(), answer.language()),
                null,
                fields.category(),
                fields.tags(),
                or(fields.servings(), answer.servings()),
                or(fields.prepTime(), answer.prepTime()),
                or(fields.cookTime(), answer.cookTime()),
                or(fields.totalTime(), answer.totalTime()),
                answer.difficulty(),
                image == null ? null : new CoverImage(image, null));
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction step : answer.instructions()) {
            if (step.description() == null || step.description().isBlank()) continue;
            instructions.add(new Instruction(instructions.size() + 1, step.description().strip(), step.time(),
                    step.temperature(), null));
        }
        // versions and date are the post-processor's to set; the versions only have to validate until then
        return new Recipe(true, SCHEMA_VERSION, SCHEMA_VERSION, metadata, answer.description(), List.copyOf(ingredients),
                answer.equipment(), List.copyOf(instructions), fields.nutrition(), answer.notes(), answer.storage());
    }

    // The card lists an ingredient when every word of it appears there: "fresh parsley" is not "fresh lemon"
    private static boolean mentions(String cardText, String item) {
        Set<String> words = words(item);
        return !words.isEmpty() && words.stream().allMatch(cardText::contains);
    }

    // The card line sharing the most words with a card-only ingredient, or null when none shares one
    private static Row cardRow(String item, List<Row> cardRows) {
        Set<String> words = words(item);
        Row best = null;
        long bestCount = 0;
        for (Row r : cardRows) {
            Set<String> line = words(r.text());
            long count = words.stream().filter(line::contains).count();
            if (count > bestCount) {
                best = r;
                bestCount = count;
            }
        }
        return best;
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) words.add(m.group());
        return words;
    }

    private static String normalized(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String quote(String text) {
        String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= MAX_QUOTE ? line : line.substring(0, MAX_QUOTE) + "…";
    }

    private static <T> T or(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
