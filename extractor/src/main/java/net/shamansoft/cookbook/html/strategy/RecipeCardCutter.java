package net.shamansoft.cookbook.html.strategy;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
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
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final Pattern OWN_TAG = Pattern.compile("</?" + TAG + "\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    static final int MAX_CANDIDATE_TEXT = 8_000;
    static final int MAX_BLOCK_CHARS = 12_000;
    private static final double MIN_COVERAGE = 0.5;
    private static final double NEAR_BEST = 0.9;

    private RecipeCardCutter() {
    }

    /**
     * @param doc             the parsed page; not modified
     * @param ingredientLines the JSON-LD {@code recipeIngredient} strings, used to recognise the right block
     * @return the block's compact HTML, or empty when no element covers the JSON-LD ingredients
     */
    public static Optional<String> cut(Document doc, List<String> ingredientLines) {
        List<String> keys = keyWords(ingredientLines);
        if (keys.isEmpty()) return Optional.empty();
        return best(doc.select(PLUGIN_SELECTOR), keys)
                .or(() -> best(doc.select(GENERIC_SELECTOR), keys))
                .map(RecipeCardCutter::compact)
                .filter(block -> !block.isBlank() && block.length() <= MAX_BLOCK_CHARS);
    }

    /** One word per ingredient line (its longest), lower-cased, to look for in candidate blocks. */
    private static List<String> keyWords(List<String> lines) {
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

    /** The smallest candidate whose coverage of the key words is close to the best one's. */
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
                .filter(s -> s.coverage() >= bestCoverage * NEAR_BEST)
                .min(Comparator.comparingInt(Scored::textLength))
                .map(Scored::element);
    }

    /** Markup only: no media, controls or attributes, inline wrappers unwrapped, blank lines collapsed. */
    private static String compact(Element element) {
        Element block = element.clone();
        block.select(NOISE_SELECTOR).remove();
        block.select("span, a, font").unwrap();
        for (Element el : block.getAllElements()) {
            el.clearAttributes();
        }
        String html = block.outerHtml().replaceAll("\\s*\\n\\s*", "\n").trim();
        return OWN_TAG.matcher(html).replaceAll("");
    }
}
