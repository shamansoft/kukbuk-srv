package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.service.gemini.GeminiRequest;
import net.shamansoft.cookbook.service.gemini.GeminiResponse;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.cookbook.service.gemini.JsonLdEnrichment;
import net.shamansoft.cookbook.service.gemini.JsonLdRequestBuilder;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnchoredJsonLdExtractorTest {

    private static final String URL = "https://example.com/pie";
    private static final String JSON_LD = "{\"@type\":\"Recipe\",\"name\":\"Pie\","
            + "\"recipeIngredient\":[\"500 g flour\",\"200 g onions\"],\"recipeInstructions\":[\"Bake.\"]}";
    private static final GeminiRequest FIRST = new GeminiRequest();
    private static final GeminiRequest FEEDBACK = new GeminiRequest();

    @Mock
    private JsonLdRequestBuilder requestBuilder;
    @Mock
    private GeminiRestTransformer gemini;
    @Mock
    private RecipePostProcessor postProcessor;

    private SimpleMeterRegistry meterRegistry;
    private AnchoredJsonLdExtractor extractor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        extractor = new AnchoredJsonLdExtractor(new ObjectMapper(), requestBuilder, gemini,
                new RecipeValidationService(), postProcessor, meterRegistry);
        ReflectionTestUtils.setField(extractor, "enabled", true);
        lenient().when(requestBuilder.build(any(), any(), isNull())).thenReturn(FIRST);
        lenient().when(requestBuilder.build(any(), any(), isNull(), anyString(), anyList())).thenReturn(FEEDBACK);
        lenient().when(postProcessor.process(any(Recipe.class), eq(URL))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void returnsTheAssembledRecipeWhenTheAnswerCoversEveryLine() {
        when(gemini.enrich(FIRST, false)).thenReturn(answer(row(0, "flour"), row(1, "onions")));

        Optional<Transformer.Response> result = extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL);

        assertThat(result).hasValueSatisfying(response -> {
            assertThat(response.isRecipe()).isTrue();
            assertThat(response.recipe().metadata().title()).isEqualTo("Pie");
            assertThat(response.recipe().ingredients()).extracting(Ingredient::item).containsExactly("flour", "onions");
        });
        verify(gemini, never()).enrich(FEEDBACK, true);
        assertThat(counter("applied")).isEqualTo(1);
    }

    @Test
    void retriesOnceWithTheViolations() {
        when(gemini.enrich(FIRST, false)).thenReturn(answer(row(0, "flour")));
        when(gemini.enrich(FEEDBACK, true)).thenReturn(answer(row(0, "flour"), row(1, "onions")));

        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isPresent();
        verify(requestBuilder).build(any(), any(), isNull(), anyString(),
                eq(List.of("Line 1 (\"200 g onions\") has no row. Every line from 0 to 1 needs at least one row "
                        + "with its source_index.")));
        assertThat(counter("applied_after_retry")).isEqualTo(1);
    }

    @Test
    void fallsBackWhenTheRetryStillBreaksTheRules() {
        when(gemini.enrich(FIRST, false)).thenReturn(answer(row(0, "flour")));
        when(gemini.enrich(FEEDBACK, true)).thenReturn(answer(row(0, "flour")));

        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isEmpty();
        assertThat(counter("fallback_violations")).isEqualTo(1);
        verify(postProcessor, never()).process(any(), any());
    }

    @Test
    void fallsBackWhenTheModelFails() {
        when(gemini.enrich(FIRST, false)).thenReturn(GeminiResponse.failure(GeminiResponse.Code.TRUNCATED, "cut"));

        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isEmpty();
        assertThat(counter("fallback_model")).isEqualTo(1);
    }

    @Test
    void fallsBackOnAnUnexpectedError() {
        when(gemini.enrich(FIRST, false)).thenThrow(new IllegalStateException("boom"));

        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isEmpty();
        assertThat(counter("fallback_error")).isEqualTo(1);
    }

    @Test
    void skipsJsonLdWithoutIngredientsOrSteps() {
        String noSteps = "{\"@type\":\"Recipe\",\"name\":\"Pie\",\"recipeIngredient\":[\"500 g flour\"]}";

        assertThat(extractor.extract(cleaned(noSteps, Strategy.STRUCTURED_DATA), URL)).isEmpty();
        assertThat(counter("skipped")).isEqualTo(1);
        verifyNoInteractions(gemini);
    }

    @Test
    void leavesOtherPathsAndTheDisabledFlagAlone() {
        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.CONTENT_FILTER), URL)).isEmpty();
        ReflectionTestUtils.setField(extractor, "enabled", false);
        assertThat(extractor.extract(cleaned(JSON_LD, Strategy.STRUCTURED_DATA), URL)).isEmpty();
        verifyNoInteractions(gemini);
        assertThat(meterRegistry.find("recipe.jsonld.anchored").counters()).isEmpty();
    }

    private double counter(String outcome) {
        return meterRegistry.counter("recipe.jsonld.anchored", "outcome", outcome).count();
    }

    private static HtmlCleaner.Results cleaned(String content, Strategy strategy) {
        return new HtmlCleaner.Results(content, content.length(), content.length(), 0.0, strategy, "");
    }

    private static GeminiResponse<JsonLdEnrichment> answer(JsonLdEnrichment.Row... rows) {
        return GeminiResponse.success(new JsonLdEnrichment("Pie", "A pie.", "en", "easy", null, null, null, null,
                List.of(rows), List.of(new Instruction(null, "Bake.", null, null, null)), null, null, null), "{}");
    }

    private static JsonLdEnrichment.Row row(int index, String item) {
        return new JsonLdEnrichment.Row(index, null, item, "1", "g", null, false, null, null);
    }
}
