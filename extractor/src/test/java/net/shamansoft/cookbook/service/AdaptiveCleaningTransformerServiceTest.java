package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveCleaningTransformerServiceTest {

    @Mock
    private ValidatingTransformerService innerTransformer;

    @Mock
    private HtmlCleaner htmlCleaner;

    private AdaptiveCleaningTransformerService service;

    private JsonLdFieldOverwriter jsonLdFields;

    private CardSectionAssigner cardSections;

    private static final String RAW_HTML = "<html><body>Recipe content</body></html>";
    private static final String CLEANED_HTML = "<div>Recipe content</div>";
    private static final String SOURCE_URL = "https://example.com/recipe";

    @BeforeEach
    void setUp() {
        jsonLdFields = new JsonLdFieldOverwriter(new ObjectMapper());
        cardSections = new CardSectionAssigner(new ObjectMapper(), new SimpleMeterRegistry());
        service = new AdaptiveCleaningTransformerService(innerTransformer, htmlCleaner, jsonLdFields, cardSections);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.5);
    }

    @Test
    @DisplayName("Returns recipe immediately when LLM finds one on first attempt")
    void transform_returnsRecipeOnFirstAttempt() {
        Recipe recipe = createRecipe("Pasta");
        HtmlCleaner.Results cleanedResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        Transformer.Response recipeResponse = Transformer.Response.recipe(recipe);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(cleanedResult);
        when(innerTransformer.transform(CLEANED_HTML, SOURCE_URL)).thenReturn(recipeResponse);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isTrue();
        assertThat(result.recipe().metadata().title()).isEqualTo("Pasta");
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("No retry when adaptive cleaning is disabled")
    void transform_noRetryWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        HtmlCleaner.Results cleanedResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.8);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(cleanedResult);
        when(innerTransformer.transform(CLEANED_HTML, SOURCE_URL)).thenReturn(notRecipe);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isFalse();
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("No retry when confidence is below threshold")
    void transform_noRetryWhenConfidenceLow() {
        HtmlCleaner.Results cleanedResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.2); // below 0.5 threshold

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(cleanedResult);
        when(innerTransformer.transform(CLEANED_HTML, SOURCE_URL)).thenReturn(notRecipe);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isFalse();
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("Retries with next strategy when confidence is high")
    void transform_retriesWithNextStrategyOnHighConfidence() {
        Recipe recipe = createRecipe("Found Recipe");
        HtmlCleaner.Results initialResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        HtmlCleaner.Results retryResult = buildCleanerResult(RAW_HTML, Strategy.SECTION_BASED);

        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.8); // above threshold
        Transformer.Response foundRecipe = Transformer.Response.recipe(recipe);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(initialResult);
        when(innerTransformer.transform(CLEANED_HTML, SOURCE_URL)).thenReturn(notRecipe);
        when(htmlCleaner.processWithStrategy(eq(RAW_HTML), eq(SOURCE_URL), eq(Strategy.SECTION_BASED)))
                .thenReturn(retryResult);
        when(innerTransformer.transform(RAW_HTML, SOURCE_URL)).thenReturn(foundRecipe);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isTrue();
        assertThat(result.recipe().metadata().title()).isEqualTo("Found Recipe");
        verify(htmlCleaner).processWithStrategy(RAW_HTML, SOURCE_URL, Strategy.SECTION_BASED);
    }

    @Test
    @DisplayName("Stops retrying when confidence drops below threshold during adaptive loop")
    void transform_stopsWhenConfidenceDrops() {
        HtmlCleaner.Results initialResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        HtmlCleaner.Results retryResult = buildCleanerResult(RAW_HTML, Strategy.SECTION_BASED);

        Transformer.Response highConfidence = Transformer.Response.notRecipe(0.8);
        Transformer.Response lowConfidence = Transformer.Response.notRecipe(0.1); // drops below threshold

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(initialResult);
        when(innerTransformer.transform(CLEANED_HTML, SOURCE_URL)).thenReturn(highConfidence);
        when(htmlCleaner.processWithStrategy(eq(RAW_HTML), eq(SOURCE_URL), eq(Strategy.SECTION_BASED)))
                .thenReturn(retryResult);
        when(innerTransformer.transform(RAW_HTML, SOURCE_URL)).thenReturn(lowConfidence);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isFalse();
        // Should stop after SECTION_BASED, not try CONTENT_FILTER
        verify(htmlCleaner, times(1)).processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("Exhausts all strategies when all return not-recipe with high confidence")
    void transform_exhaustsAllStrategies() {
        HtmlCleaner.Results initialResult = buildCleanerResult(CLEANED_HTML, Strategy.STRUCTURED_DATA);
        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.8);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(initialResult);
        when(innerTransformer.transform(anyString(), eq(SOURCE_URL))).thenReturn(notRecipe);

        // All retries return notRecipe with high confidence
        when(htmlCleaner.processWithStrategy(eq(RAW_HTML), eq(SOURCE_URL), any(Strategy.class)))
                .thenAnswer(inv -> buildCleanerResult(RAW_HTML, inv.getArgument(2)));

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isFalse();
        // Verify it tried strategies beyond the initial one
        verify(htmlCleaner, times(Strategy.ADAPTIVE_ORDER.size() - 1))
                .processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("Uses FALLBACK strategy when initial was already FALLBACK (no retries possible)")
    void transform_noRetryWhenAlreadyFallback() {
        // If initial strategy was FALLBACK, startIdx is at end of list, loop doesn't run
        HtmlCleaner.Results fallbackResult = buildCleanerResult(RAW_HTML, Strategy.FALLBACK);
        Transformer.Response notRecipe = Transformer.Response.notRecipe(0.9);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(fallbackResult);
        when(innerTransformer.transform(RAW_HTML, SOURCE_URL)).thenReturn(notRecipe);

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.isRecipe()).isFalse();
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
    }

    @Test
    @DisplayName("Markdown skips HTML cleaning and goes straight to the inner transformer")
    void transformMarkdown_skipsCleaning() {
        String markdown = "# Pasta\n\n* 1 cup flour";
        Transformer.Response recipeResponse = Transformer.Response.recipe(createRecipe("Pasta"));
        when(innerTransformer.transformMarkdown(markdown, SOURCE_URL)).thenReturn(recipeResponse);

        Transformer.Response result = service.transformMarkdown(markdown, SOURCE_URL);

        assertThat(result).isSameAs(recipeResponse);
        verify(htmlCleaner, never()).process(any(), any());
        verify(htmlCleaner, never()).processWithStrategy(any(), any(), any());
    }

    // ---- helpers ------------------------------------------------------------

    @Test
    @DisplayName("Applies the page's JSON-LD fields to a recipe found on the JSON-LD path")
    void transform_appliesJsonLdFields() {
        ReflectionTestUtils.setField(jsonLdFields, "enabled", true);
        String jsonLd = "{\"@type\":\"Recipe\",\"name\":\"Pasta alla Norma\",\"recipeYield\":\"2\"}";
        HtmlCleaner.Results cleanedResult = buildCleanerResult(jsonLd, Strategy.STRUCTURED_DATA);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(cleanedResult);
        when(innerTransformer.transform(jsonLd, SOURCE_URL)).thenReturn(Transformer.Response.recipe(createRecipe("Pasta")));

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.recipe().metadata().title()).isEqualTo("Pasta alla Norma");
        assertThat(result.recipe().metadata().servings()).isEqualTo(2);
    }

    @Test
    @DisplayName("Takes ingredient sections from the recipe card on the JSON-LD path")
    void transform_appliesCardSections() {
        ReflectionTestUtils.setField(cardSections, "enabled", true);
        String cleaned = "{\"@type\":\"Recipe\",\"recipeIngredient\":[\"1 cup flour\",\"1 cup sugar\"]}"
                + "\n\n<RECIPE_CARD_INGREDIENTS>\n<div><ul><li>1 cup flour</li></ul>"
                + "<h4>Glaze</h4><ul><li>1 cup sugar</li></ul></div>\n</RECIPE_CARD_INGREDIENTS>";
        Recipe recipe = createRecipe("Cake");
        Recipe twoLines = new Recipe(true, "1.0.0", "1.0.0", recipe.metadata(), recipe.description(),
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main"),
                        new Ingredient("sugar", "1", "cup", null, false, null, "main")),
                null, recipe.instructions(), null, null, null);

        when(htmlCleaner.process(RAW_HTML, SOURCE_URL)).thenReturn(buildCleanerResult(cleaned, Strategy.STRUCTURED_DATA));
        when(innerTransformer.transform(cleaned, SOURCE_URL)).thenReturn(Transformer.Response.recipe(twoLines));

        Transformer.Response result = service.transform(RAW_HTML, SOURCE_URL);

        assertThat(result.recipe().ingredients()).extracting(Ingredient::component).containsExactly("main", "Glaze");
    }

    private HtmlCleaner.Results buildCleanerResult(String html, Strategy strategy) {
        int size = html.length();
        return new HtmlCleaner.Results(html, size, size, 0.0, strategy,
                "Strategy: " + strategy + ", " + size + " chars");
    }

    private Recipe createRecipe(String title) {
        return new Recipe(
                true, "1.0.0", "1.0.0",
                new RecipeMetadata(title, "https://example.com", null, null, LocalDate.now(),
                        null, null, 4, null, null, null, null, null),
                "Description",
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main")),
                null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }
}
