package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter.Row;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.html.strategy.StructuredDataStrategy;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On the JSON-LD path, sets each ingredient's {@code component} from the recipe card's group headings. Given the
 * same card, the model sometimes files only the first lines of a group under its heading.
 *
 * <p>Applies only when the card lines up with the JSON-LD ingredient list, one card line per JSON-LD line (true of
 * every recipe-plugin card in the samples), and has at least one group heading. Otherwise the model's components
 * stay.
 */
@Service
@Slf4j
public class CardSectionAssigner {

    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,6})/(\\d{1,6})|\\d{1,6}(?:[.,]\\d{1,6})?");
    private static final String MAIN = "main";

    enum Outcome { APPLIED, NO_CARD, NO_GROUPS, LINES_DIFFER }

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${recipe.jsonld-sections.enabled:true}")
    private boolean enabled;

    public CardSectionAssigner(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param response  the model's answer for {@code cleaned}
     * @param cleaned   the content the model was given
     * @param sourceUrl the page URL, for the log
     * @return the response with card groups applied, or unchanged when the content was not JSON-LD, the answer has
     * more than one recipe, or the card does not qualify
     */
    public Transformer.Response apply(Transformer.Response response, HtmlCleaner.Results cleaned, String sourceUrl) {
        if (!enabled || !response.isRecipe() || response.recipes().size() != 1
                || cleaned.strategyUsed() != Strategy.STRUCTURED_DATA
                || response.recipe().ingredients() == null) {
            return response;
        }
        List<String> lines;
        try {
            lines = JsonLdRecipeReader.ingredients(
                    objectMapper.readTree(StructuredDataStrategy.jsonLd(cleaned.cleanedHtml())));
        } catch (Exception e) {
            log.debug("Could not read JSON-LD ingredients, keeping the model's components", e);
            return response;
        }
        Optional<String> block = StructuredDataStrategy.card(cleaned.cleanedHtml());
        List<Row> rows = block.map(RecipeCardCutter::rows).orElse(List.of());
        Outcome outcome = block.isEmpty() ? Outcome.NO_CARD
                : rows.size() != lines.size() ? Outcome.LINES_DIFFER
                : rows.stream().allMatch(r -> r.group() == null) ? Outcome.NO_GROUPS
                : Outcome.APPLIED;
        meterRegistry.counter("recipe.jsonld.sections", "outcome", outcome.name().toLowerCase(Locale.ROOT))
                .increment();

        Recipe recipe = response.recipe();
        if (outcome != Outcome.APPLIED) {
            log.info("Card sections - URL: {}, outcome: {}, card lines: {}, JSON-LD lines: {}",
                    sourceUrl, outcome, rows.size(), lines.size());
            return response;
        }
        List<Ingredient> assigned = assign(recipe.ingredients(), rows);
        long changed = 0;
        for (int i = 0; i < assigned.size(); i++) {
            if (!Objects.equals(assigned.get(i).component(), recipe.ingredients().get(i).component())) changed++;
        }
        log.info("Card sections - URL: {}, outcome: {}, {} of {} components changed",
                sourceUrl, outcome, changed, assigned.size());
        return Transformer.Response.withRawResponse(true, response.confidence(),
                List.of(withIngredients(recipe, assigned)), response.rawLlmResponse());
    }

    /**
     * The card gives every JSON-LD line its section: one card line per JSON-LD line, in the same order (each pair
     * shares a word), and at least one heading. A count alone would pass a card that lists the same number of
     * lines in another order.
     */
    static boolean givesSections(List<Row> rows, List<String> jsonLdLines) {
        if (rows.size() != jsonLdLines.size() || rows.stream().noneMatch(r -> r.group() != null)) return false;
        for (int i = 0; i < rows.size(); i++) {
            if (!sameLine(rows.get(i).text(), jsonLdLines.get(i))) return false;
        }
        return true;
    }

    // A line without a word of three letters or more ("2 개") has nothing to compare, so it does not break the order
    private static boolean sameLine(String cardLine, String jsonLdLine) {
        Set<String> card = words(cardLine);
        Set<String> line = words(jsonLdLine);
        return card.isEmpty() || line.isEmpty() || card.stream().anyMatch(line::contains);
    }

    /**
     * Each ingredient takes the group of the card line that matches it best: two points per shared word of the item,
     * one per shared word of the notes, one when the line holds the amount. Ties go to the first line at or after the
     * previous match, so the rows of a split line ("salt and pepper") stay in that line's group. An ingredient that
     * shares no word with any line keeps its component.
     */
    static List<Ingredient> assign(List<Ingredient> ingredients, List<Row> rows) {
        List<Set<String>> rowWords = rows.stream().map(r -> words(r.text())).toList();
        List<Set<Double>> rowNumbers = rows.stream().map(r -> numbers(r.text())).toList();
        List<Ingredient> result = new ArrayList<>();
        int previous = 0;
        for (Ingredient ingredient : ingredients) {
            Set<String> item = words(ingredient.item());
            // "xícara (chá)" in the notes must not outweigh the item: 2 cups of sugar is not the 1 cup further down
            Set<String> notes = words(Objects.toString(ingredient.notes(), ""));
            notes.removeAll(item);
            Double amount = number(ingredient.amount());
            int best = -1;
            int bestScore = 0;
            for (int j = 0; j < rows.size(); j++) {
                Set<String> line = rowWords.get(j);
                int itemWords = (int) item.stream().filter(line::contains).count();
                int noteWords = (int) notes.stream().filter(line::contains).count();
                if (itemWords + noteWords == 0) continue;
                int score = 2 * itemWords + noteWords + (amount != null && rowNumbers.get(j).contains(amount) ? 1 : 0);
                if (score > bestScore || (score == bestScore && best < previous && j >= previous)) {
                    best = j;
                    bestScore = score;
                }
            }
            if (best < 0) {
                result.add(ingredient);
                continue;
            }
            previous = best;
            String group = rows.get(best).group();
            result.add(withComponent(ingredient, group != null ? group : MAIN));
        }
        return result;
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) words.add(m.group());
        return words;
    }

    // "2", "1/2", "0,5": the numbers a line or an amount holds, rounded so 1/3 and 0.333 meet
    private static Set<Double> numbers(String text) {
        Set<Double> numbers = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            double value;
            if (m.group(1) != null) {
                int denominator = Integer.parseInt(m.group(2));
                if (denominator == 0) continue;
                value = Integer.parseInt(m.group(1)) / (double) denominator;
            } else {
                value = Double.parseDouble(m.group().replace(',', '.'));
            }
            numbers.add(Math.round(value * 1000) / 1000.0);
        }
        return numbers;
    }

    private static Double number(String amount) {
        if (amount == null) return null;
        try {
            return Math.round(Double.parseDouble(amount.strip().replace(',', '.')) * 1000) / 1000.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Ingredient withComponent(Ingredient i, String component) {
        return new Ingredient(i.item(), i.amount(), i.unit(), i.notes(), i.optional(), i.substitutions(), component);
    }

    private static Recipe withIngredients(Recipe r, List<Ingredient> ingredients) {
        return new Recipe(r.isRecipe(), r.schemaVersion(), r.recipeVersion(), r.metadata(), r.description(),
                ingredients, r.equipment(), r.instructions(), r.nutrition(), r.notes(), r.storage());
    }
}
