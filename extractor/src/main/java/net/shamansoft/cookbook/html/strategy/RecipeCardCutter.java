package net.shamansoft.cookbook.html.strategy;

import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cuts the recipe card's ingredient block, with its section headers, out of a page.
 * JSON-LD lists ingredients flat; the card is where the page shows which ingredients belong to which part.
 */
public final class RecipeCardCutter {

    public static final String TAG = "RECIPE_CARD_INGREDIENTS";

    // Recipe plugins hold exactly the ingredient list and its group names
    private static final String PLUGIN_SELECTOR =
            ".wprm-recipe-ingredients-container, .tasty-recipes-ingredients, .mv-create-ingredients";
    private static final String GENERIC_SELECTOR = "[id*=ingredient], [class*=ingredient]";
    private static final String NOISE_SELECTOR =
            "script, style, noscript, template, img, picture, source, svg, video, audio, iframe, "
                    + "button, input, select, textarea";
    // Group names are a <div> in some WPRM themes; an <h4> keeps them recognisable once attributes are gone
    private static final String GROUP_NAME_SELECTOR = ".wprm-recipe-group-name";
    private static final String HEADING = "h1, h2, h3, h4, h5, h6";
    // The card's own title, not a group: the lines after it have no group
    private static final Pattern INGREDIENTS_TITLE = Pattern.compile(
            "(?:ingredients?|zutaten|ingr[eé]dients?|ingredientes|ingredienti|ingredi[eë]nten|ingredienser|"
                    + "ainekset|składniki|ингредиенты|продукты|재료|材料)\\W*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FOR_THE = Pattern.compile("^(?:for|für|pour|para|per|voor|til|för)\\s+"
                    + "(?:(?:the|den|die|das|le|la|les|el|los|las|il|lo|de|het)\\s+)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OWN_TAG = Pattern.compile("</?" + TAG + "\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    static final int MAX_CANDIDATE_TEXT = 8_000;
    static final int MAX_BLOCK_CHARS = 12_000;
    private static final double MIN_COVERAGE = 0.5;

    private RecipeCardCutter() {
    }

    /**
     * One ingredient line of the card.
     *
     * @param group the group heading above the line, named as the prompt asks ("For the Sauce:" → "Sauce");
     *              null when no group heading precedes it
     * @param text  the line's text
     */
    public record Row(String group, String text) {
    }

    /**
     * @param doc             the parsed page; not modified
     * @param ingredientLines the JSON-LD {@code recipeIngredient} strings, used to recognise the right block
     * @return the block's compact HTML, or empty when no element covers the JSON-LD ingredients
     */
    public static Optional<String> cut(Document doc, List<String> ingredientLines) {
        List<String> keys = JsonLdRecipeReader.keyWords(ingredientLines);
        if (keys.isEmpty()) return Optional.empty();
        return best(doc.select(PLUGIN_SELECTOR), keys)
                .or(() -> best(doc.select(GENERIC_SELECTOR), keys))
                .map(RecipeCardCutter::compact)
                .filter(block -> !block.isBlank() && block.length() <= MAX_BLOCK_CHARS);
    }

    /**
     * The ingredient lines of a block returned by {@link #cut}, each with the group heading above it.
     * A heading is an {@code h1}–{@code h6}, or a paragraph that is only bold text (Tasty Recipes); a line is
     * an {@code li} without nested lines.
     */
    public static List<Row> rows(String block) {
        List<Row> rows = new ArrayList<>();
        String group = null;
        for (Element el : Jsoup.parseBodyFragment(block).body().getAllElements()) {
            if (el.closest("li") == null && isHeading(el)) {
                group = groupName(el.text());
            } else if (el.is("li") && el.select("li").size() == 1) {
                String text = el.text().strip();
                if (!text.isEmpty()) rows.add(new Row(group, text));
            }
        }
        return rows;
    }

    private static boolean isHeading(Element el) {
        if (el.is(HEADING)) return true;
        return el.is("p, div") && el.childrenSize() == 1 && el.child(0).is("strong, b")
                && !el.text().isBlank() && el.text().equals(el.child(0).text());
    }

    /** "For the Sauce:" → "Sauce", "CHICKEN" → "Chicken"; null for the card's "Ingredients" title. */
    public static String groupName(String heading) {
        String name = heading.strip();
        if (INGREDIENTS_TITLE.matcher(name).matches()) return null;
        name = FOR_THE.matcher(name).replaceFirst("").replaceAll("[\\s:]+$", "").strip();
        if (name.isEmpty()) return null;
        if (name.equals(name.toUpperCase(Locale.ROOT)) && !name.equals(name.toLowerCase(Locale.ROOT))) {
            name = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The smallest candidate with the best coverage of the key words, so no group is cut off. */
    private static Optional<Element> best(Elements candidates, List<String> keys) {
        record Scored(Element element, int textLength, int coverage) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Element candidate : candidates) {
            String text = candidate.text().toLowerCase(Locale.ROOT);
            if (text.length() > MAX_CANDIDATE_TEXT) continue;
            int coverage = (int) keys.stream().filter(text::contains).count();
            scored.add(new Scored(candidate, text.length(), coverage));
        }
        int bestCoverage = scored.stream().mapToInt(Scored::coverage).max().orElse(0);
        if (bestCoverage < keys.size() * MIN_COVERAGE) return Optional.empty();
        return scored.stream()
                .filter(s -> s.coverage() == bestCoverage)
                .min(Comparator.comparingInt(Scored::textLength))
                .map(Scored::element);
    }

    /** Markup only: no media, controls or attributes, inline wrappers unwrapped, blank lines collapsed. */
    private static String compact(Element element) {
        Element block = element.clone();
        block.select(NOISE_SELECTOR).remove();
        block.select(GROUP_NAME_SELECTOR).tagName("h4");
        block.select("span, a, font").unwrap();
        for (Element el : block.getAllElements()) {
            el.clearAttributes();
        }
        String html = block.outerHtml().replaceAll("\\s*\\n\\s*", "\n").trim();
        return OWN_TAG.matcher(html).replaceAll("");
    }
}
