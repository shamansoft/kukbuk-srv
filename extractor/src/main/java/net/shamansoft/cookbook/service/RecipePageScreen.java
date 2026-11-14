package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.JevClient;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.PageText;
import net.shamansoft.cookbook.html.strategy.Strategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Picks the HTML cleaning to send to Gemini, and drops pages with no recipe, by asking Jev (TypeSafe) about
 * each cleaner's output before any Gemini call.
 *
 * <p>Each Jev request asks three questions about a text:
 * <ul>
 *   <li>{@code complete} (Noul): a recipe's full ingredient list and all of its steps are there;</li>
 *   <li>{@code ingredients} (Choice): given as a {@code list}, only in {@code prose}, or {@code none};</li>
 *   <li>{@code steps} (Noul): the text says how to prepare a dish.</li>
 * </ul>
 * The cleaners' outputs are tried from most to least restrictive; the first with {@code complete >= threshold} is
 * {@link Use used}. If none is, the whole page's visible text is judged: with {@code P(ingredients = none) >=
 * threshold} and {@code steps <= 1 - threshold} the page is {@link NotRecipe not a recipe} and Gemini is skipped.
 * That decision rests on the whole page, never on a cleaner's output, which may have dropped the recipe.
 * Anything else is {@link Undecided}: extraction runs as it did before the screen.
 *
 * <p>The screen never blocks extraction: any failure, or running past {@code time-budget-ms}, is {@code Undecided}.
 * Texts are cut to {@code max-text-bytes} of UTF-8 (a conservative stand-in for Jev's 32k-token limit that also
 * holds for CJK text). A cut text can be judged complete, never "not a recipe": the recipe may be in the cut part.
 * Probing the cleaners records no {@code html.preprocessing} metrics; only the chosen cleaning is recorded.
 */
@Service
@Slf4j
public class RecipePageScreen {

    /** Raw HTML ({@link Strategy#FALLBACK}) is too large and noisy to send Gemini first; it stays the last resort. */
    static final List<Strategy> CANDIDATES = List.of(Strategy.STRUCTURED_DATA, Strategy.SECTION_BASED,
            Strategy.CONTENT_FILTER);

    static final String COMPLETE = "complete";
    static final String INGREDIENTS = "ingredients";
    static final String STEPS = "steps";
    static final String NO_INGREDIENTS = "none";

    private static final Map<String, Object> QUESTIONS = questions();

    private final HtmlCleaner htmlCleaner;
    private final JevClient jev;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final double threshold;
    private final int maxTextBytes;
    private final long timeBudgetNanos;
    private LongSupplier nanoTime = System::nanoTime;

    public RecipePageScreen(HtmlCleaner htmlCleaner, JevClient jev, MeterRegistry meterRegistry,
                            @Value("${cookbook.typesafe.page-screen.enabled:true}") boolean enabled,
                            @Value("${cookbook.typesafe.api-key:}") String apiKey,
                            @Value("${cookbook.typesafe.page-screen.threshold:0.8}") double threshold,
                            @Value("${cookbook.typesafe.page-screen.max-text-bytes:60000}") int maxTextBytes,
                            @Value("${cookbook.typesafe.page-screen.time-budget-ms:5000}") long timeBudgetMs) {
        this.htmlCleaner = htmlCleaner;
        this.jev = jev;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.threshold = threshold;
        this.maxTextBytes = Math.max(1, maxTextBytes);
        this.timeBudgetNanos = TimeUnit.MILLISECONDS.toNanos(timeBudgetMs);
        log.info("Recipe page screen (Jev): {}", this.enabled ? "enabled, threshold " + threshold : "disabled");
    }

    /** For tests. */
    void nanoTime(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public Verdict screen(String rawHtml, String url) {
        if (!enabled || rawHtml == null || rawHtml.isBlank()) {
            return new Undecided("screen disabled");
        }
        Verdict verdict;
        try {
            verdict = judge(rawHtml, url, nanoTime.getAsLong() + timeBudgetNanos);
        } catch (RuntimeException e) {
            log.warn("Jev page screen failed, extracting without it - URL: {}: {}", url, e.toString());
            verdict = new Undecided("screen error");
        }
        meterRegistry.counter("recipe.page_screen", "verdict", verdict.name(), "strategy", verdict.strategy())
                .increment();
        return verdict;
    }

    private Verdict judge(String rawHtml, String url, long deadline) {
        for (Strategy strategy : CANDIDATES) {
            Optional<String> cleaned = htmlCleaner.tryStrategy(rawHtml, strategy);
            if (cleaned.isEmpty()) {
                continue;
            }
            String text = PageText.of(cleaned.get());
            if (text.isBlank()) {
                continue;
            }
            if (nanoTime.getAsLong() > deadline) {
                return new Undecided("time budget spent");
            }
            Judgement judgement = judgeText(strategy, url, text);
            if (judgement.complete() >= threshold) {
                return new Use(htmlCleaner.recordResult(rawHtml, cleaned.get(), strategy), judgement);
            }
        }
        // No cleaning holds a whole recipe. Only the whole page can show there is none.
        String pageText = PageText.of(rawHtml);
        if (pageText.isBlank()) {
            return new Undecided("page has no text");
        }
        if (nanoTime.getAsLong() > deadline) {
            return new Undecided("time budget spent");
        }
        Judgement page = judgeText(Strategy.FALLBACK, url, pageText);
        if (!page.truncated() && page.noIngredients() >= threshold && page.steps() <= 1 - threshold) {
            return new NotRecipe(page);
        }
        return new Undecided("no complete recipe found, but recipe not ruled out");
    }

    private Judgement judgeText(Strategy strategy, String url, String fullText) {
        String text = cutToBytes(fullText, maxTextBytes);
        boolean truncated = text.length() < fullText.length();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("url", url);
        state.put("text", text);
        JevClient.Answers answers = jev.ask(state, QUESTIONS);
        Map<String, Double> ingredients = answers.probabilities(INGREDIENTS);
        Judgement judgement = new Judgement(strategy, answers.noul(COMPLETE), answers.choice(INGREDIENTS),
                ingredients.getOrDefault(NO_INGREDIENTS, 0.0), answers.noul(STEPS), truncated, text.length(),
                answers.inputTokens(), answers.model());
        log.info("Jev page screen - URL: {}, {}", url, judgement);
        return judgement;
    }

    /** The longest prefix of {@code text} that is at most {@code maxBytes} in UTF-8, without splitting a character. */
    static String cutToBytes(String text, int maxBytes) {
        int bytes = 0;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int size = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + size > maxBytes) {
                return text.substring(0, i);
            }
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return text;
    }

    private static Map<String, Object> questions() {
        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put(COMPLETE, Map.of(
                "type", "noul",
                "instructions", "Does `text` contain at least one recipe with its complete ingredient list and all"
                        + " of its preparation steps?",
                "criteria", Map.of(
                        "true", "`text` has the full list of ingredients of a dish and every step to prepare it."
                                + " The text may be in any language and may be JSON. A page with several recipes"
                                + " counts when at least one of them is whole.",
                        "false", "The ingredients or the steps are missing, cut off part-way, or only referred to"
                                + " (for example \"see the recipe card below\" or \"get the full recipe\"), or"
                                + " `text` has no recipe.")));
        Map<String, Object> ingredientOptions = new LinkedHashMap<>();
        ingredientOptions.put("list", "A list of ingredients, usually with amounts, one ingredient per line, item"
                + " or array element.");
        ingredientOptions.put("prose", "Ingredients appear only inside sentences, such as \"toss the pasta with"
                + " two spoons of butter\", with no separate list.");
        ingredientOptions.put(NO_INGREDIENTS, "No ingredients of a dish to cook: the text is about something else,"
                + " or it only names dishes or links to recipes.");
        questions.put(INGREDIENTS, Map.of(
                "type", "choice",
                "instructions", "How does `text` give the ingredients of a dish to cook?",
                "criteria", ingredientOptions));
        questions.put(STEPS, Map.of(
                "type", "noul",
                "instructions", "Does `text` describe how to prepare a dish?",
                "criteria", Map.of(
                        "true", "It gives the method, as numbered steps or in paragraphs.",
                        "false", "It does not say how to prepare a dish, or only names or links to recipes.")));
        return Collections.unmodifiableMap(questions); // keeps the options' order
    }

    /**
     * Jev's answers about one cleaner output.
     *
     * @param noIngredients probability of {@code ingredients = none}
     * @param strategy      the cleaner whose output was judged; {@link Strategy#FALLBACK} for the whole page
     * @param truncated     the text was cut at {@code max-text-bytes}
     */
    public record Judgement(Strategy strategy, double complete, String ingredients, double noIngredients,
                            double steps, boolean truncated, int textChars, int inputTokens, String model) {

        @Override
        public String toString() {
            return String.format("strategy: %s, complete: %.2f, ingredients: %s (none %.2f), steps: %.2f,"
                            + " text: %d chars%s, %d tokens, model: %s", strategy, complete, ingredients,
                    noIngredients, steps, textChars, truncated ? " (truncated)" : "", inputTokens, model);
        }
    }

    public sealed interface Verdict permits Use, NotRecipe, Undecided {
        String name();

        default String strategy() {
            return "none";
        }
    }

    /** Send this cleaner output to Gemini. */
    public record Use(HtmlCleaner.Results cleaned, Judgement judgement) implements Verdict {
        @Override
        public String name() {
            return "use";
        }

        @Override
        public String strategy() {
            return judgement.strategy().name();
        }
    }

    /** The page has no recipe; skip Gemini. */
    public record NotRecipe(Judgement judgement) implements Verdict {
        @Override
        public String name() {
            return "not_recipe";
        }

        @Override
        public String strategy() {
            return judgement.strategy().name();
        }

        /** Probability that the page has ingredients, reported as the response's recipe confidence. */
        public double recipeConfidence() {
            return 1 - judgement.noIngredients();
        }
    }

    /** No decision; extract the way it was done without the screen. */
    public record Undecided(String reason) implements Verdict {
        @Override
        public String name() {
            return "undecided";
        }
    }
}
