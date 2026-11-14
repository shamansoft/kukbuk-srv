package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.client.JevClient;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipePageScreenTest {

    /** The whole page's visible text is "PAGE text". */
    private static final String RAW = "<html><body><p>PAGE text</p></body></html>";
    private static final String URL = "https://example.com/soup";

    @Mock
    private HtmlCleaner htmlCleaner;

    @Mock
    private JevClient jev;

    private SimpleMeterRegistry meterRegistry;
    private RecipePageScreen screen;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        screen = screen(true, "key", 60_000);
        // every strategy produces "<p><STRATEGY> text</p>" unless a test says otherwise
        lenient().when(htmlCleaner.tryStrategy(eq(RAW), any()))
                .thenAnswer(inv -> Optional.of(html(inv.getArgument(1))));
        lenient().when(htmlCleaner.recordResult(eq(RAW), any(), any()))
                .thenAnswer(inv -> new HtmlCleaner.Results(inv.getArgument(1), RAW.length(), 10, 0.0,
                        inv.getArgument(2), "Strategy: " + inv.getArgument(2)));
    }

    private RecipePageScreen screen(boolean enabled, String apiKey, int maxTextBytes) {
        return new RecipePageScreen(htmlCleaner, jev, meterRegistry, enabled, apiKey, 0.8, maxTextBytes, 5000);
    }

    @Test
    void usesTheMostRestrictiveCompleteOutput() {
        answer("STRUCTURED_DATA", 0.95, 0.0, 0.99);

        RecipePageScreen.Verdict verdict = screen.screen(RAW, URL);

        assertThat(verdict).isInstanceOf(RecipePageScreen.Use.class);
        assertThat(((RecipePageScreen.Use) verdict).cleaned().strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
        verify(jev, times(1)).ask(any(), anyMap());
        // only the chosen cleaning is recorded in the html.preprocessing metrics
        verify(htmlCleaner).recordResult(RAW, html(Strategy.STRUCTURED_DATA), Strategy.STRUCTURED_DATA);
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
        assertThat(meterRegistry.counter("recipe.page_screen", "verdict", "use", "strategy", "STRUCTURED_DATA")
                .count()).isEqualTo(1.0);
    }

    @Test
    void movesToALessRestrictiveOutputWhenTheRecipeIsIncomplete() {
        answer("STRUCTURED_DATA", 0.4, 0.0, 0.2);
        answer("SECTION_BASED", 0.85, 0.0, 0.95);

        RecipePageScreen.Verdict verdict = screen.screen(RAW, URL);

        assertThat(((RecipePageScreen.Use) verdict).judgement().strategy()).isEqualTo(Strategy.SECTION_BASED);
        verify(htmlCleaner, never()).tryStrategy(any(), eq(Strategy.CONTENT_FILTER));
    }

    @Test
    void completeBelowThresholdIsNotEnough() {
        answer("STRUCTURED_DATA", 0.79, 0.0, 0.9);
        answer("SECTION_BASED", 0.79, 0.0, 0.9);
        answer("CONTENT_FILTER", 0.79, 0.0, 0.9);
        answer("PAGE", 0.79, 0.0, 0.9);

        assertThat(screen.screen(RAW, URL)).isInstanceOf(RecipePageScreen.Undecided.class);
        verify(htmlCleaner, never()).recordResult(any(), any(), any());
    }

    @Test
    void skipsStrategiesThatProduceNothing() {
        when(htmlCleaner.tryStrategy(RAW, Strategy.STRUCTURED_DATA)).thenReturn(Optional.empty());
        answer("SECTION_BASED", 0.9, 0.0, 0.9);

        RecipePageScreen.Verdict verdict = screen.screen(RAW, URL);

        assertThat(((RecipePageScreen.Use) verdict).judgement().strategy()).isEqualTo(Strategy.SECTION_BASED);
        verify(jev, times(1)).ask(any(), anyMap());
    }

    @Test
    void notARecipeIsDecidedOnTheWholePage() {
        answer("STRUCTURED_DATA", 0.02, 0.99, 0.01);
        answer("SECTION_BASED", 0.02, 0.99, 0.01);
        answer("CONTENT_FILTER", 0.02, 0.99, 0.01);
        answer("PAGE", 0.01, 0.97, 0.05);

        RecipePageScreen.Verdict verdict = screen.screen(RAW, URL);

        assertThat(verdict).isInstanceOf(RecipePageScreen.NotRecipe.class);
        RecipePageScreen.NotRecipe notRecipe = (RecipePageScreen.NotRecipe) verdict;
        assertThat(notRecipe.judgement().strategy()).isEqualTo(Strategy.FALLBACK);
        assertThat(notRecipe.recipeConfidence()).isCloseTo(0.03, within(1e-9));
        assertThat(meterRegistry.counter("recipe.page_screen", "verdict", "not_recipe", "strategy", "FALLBACK")
                .count()).isEqualTo(1.0);
    }

    @Test
    void aCleaningThatLostTheRecipeDoesNotRuleItOut() {
        // the cleanings dropped the recipe, but the whole page still has it
        answer("STRUCTURED_DATA", 0.02, 0.99, 0.01);
        answer("SECTION_BASED", 0.02, 0.99, 0.01);
        answer("CONTENT_FILTER", 0.02, 0.99, 0.01);
        answer("PAGE", 0.5, 0.0, 0.95);

        assertThat(screen.screen(RAW, URL)).isInstanceOf(RecipePageScreen.Undecided.class);
    }

    @Test
    void truncatedPageCanNeverBeNotARecipe() {
        screen = screen(true, "key", 5);
        answer("STRUC", 0.0, 0.99, 0.0);
        answer("SECTI", 0.0, 0.99, 0.0);
        answer("CONTE", 0.0, 0.99, 0.0);
        answer("PAGE", 0.0, 0.99, 0.0);

        assertThat(screen.screen(RAW, URL)).isInstanceOf(RecipePageScreen.Undecided.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> state = ArgumentCaptor.forClass(Map.class);
        verify(jev, times(4)).ask(state.capture(), anyMap());
        assertThat(state.getValue()).containsEntry("url", URL).containsEntry("text", "PAGE ");
    }

    @Test
    void stopsAskingOnceTheTimeBudgetIsSpent() {
        AtomicLong now = new AtomicLong();
        screen.nanoTime(now::get);
        when(jev.ask(any(), anyMap())).thenAnswer(inv -> {
            now.addAndGet(6_000_000_000L); // a 6 s answer
            return answers(0.1, 0.0, 0.9);
        });

        assertThat(screen.screen(RAW, URL)).isEqualTo(new RecipePageScreen.Undecided("time budget spent"));
        verify(jev, times(1)).ask(any(), anyMap());
    }

    @Test
    void anyFailureFailsOpen() {
        when(jev.ask(any(), anyMap())).thenThrow(new JevClient.JevException("429", null));

        assertThat(screen.screen(RAW, URL)).isEqualTo(new RecipePageScreen.Undecided("screen error"));
        assertThat(meterRegistry.counter("recipe.page_screen", "verdict", "undecided", "strategy", "none")
                .count()).isEqualTo(1.0);
    }

    @Test
    void unexpectedExceptionsFailOpenToo() {
        when(htmlCleaner.tryStrategy(RAW, Strategy.STRUCTURED_DATA)).thenThrow(new IllegalStateException("boom"));

        assertThat(screen.screen(RAW, URL)).isEqualTo(new RecipePageScreen.Undecided("screen error"));
    }

    @Test
    void withoutApiKeyTheScreenIsOff() {
        screen = screen(true, " ", 60_000);

        assertThat(screen.screen(RAW, URL)).isInstanceOf(RecipePageScreen.Undecided.class);
        verifyNoInteractions(jev, htmlCleaner);
    }

    @Test
    void disabledScreenDoesNothing() {
        screen = screen(false, "key", 60_000);

        assertThat(screen.screen(RAW, URL)).isInstanceOf(RecipePageScreen.Undecided.class);
        verifyNoInteractions(jev, htmlCleaner);
    }

    @Test
    void asksCompleteIngredientsAndSteps() {
        answer("STRUCTURED_DATA", 0.95, 0.0, 0.99);

        screen.screen(RAW, URL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> questions = ArgumentCaptor.forClass(Map.class);
        verify(jev).ask(any(), questions.capture());
        assertThat(questions.getValue()).containsOnlyKeys("complete", "ingredients", "steps");
        @SuppressWarnings("unchecked")
        Map<String, Object> ingredients = (Map<String, Object>) questions.getValue().get("ingredients");
        assertThat(ingredients).containsEntry("type", "choice");
        assertThat(List.copyOf(((Map<?, ?>) ingredients.get("criteria")).keySet()))
                .isEqualTo(List.of("list", "prose", "none"));
    }

    @Test
    void cutToBytesNeverSplitsACharacter() {
        assertThat(RecipePageScreen.cutToBytes("abc", 10)).isEqualTo("abc");
        assertThat(RecipePageScreen.cutToBytes("abcdef", 3)).isEqualTo("abc");
        assertThat(RecipePageScreen.cutToBytes("감자볶음", 7)).isEqualTo("감자"); // 3 bytes each
        assertThat(RecipePageScreen.cutToBytes("щи", 3)).isEqualTo("щ"); // 2 bytes each
        assertThat(RecipePageScreen.cutToBytes("a🍝b", 4)).isEqualTo("a"); // emoji is 4 bytes
    }

    /** Jev's answers when the text it sees starts with {@code prefix}. */
    private void answer(String prefix, double complete, double noIngredients, double steps) {
        lenient().when(jev.ask(argThat(state -> state instanceof Map<?, ?> m
                        && String.valueOf(m.get("text")).startsWith(prefix)), anyMap()))
                .thenReturn(answers(complete, noIngredients, steps));
    }

    private static JevClient.Answers answers(double complete, double noIngredients, double steps) {
        return new JevClient.Answers("jev-1.14.0", Map.of(
                "complete", Map.of("type", "noul", "noul", complete),
                "ingredients", Map.of("type", "choice", "choice", noIngredients >= 0.5 ? "none" : "list",
                        "probabilities", Map.of("list", 1 - noIngredients, "prose", 0.0, "none", noIngredients)),
                "steps", Map.of("type", "noul", "noul", steps)), 100);
    }

    private static String html(Strategy strategy) {
        return "<p>" + strategy.name() + " text</p>";
    }
}
