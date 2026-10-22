package net.shamansoft.cookbook.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader;
import net.shamansoft.cookbook.html.jsonld.JsonLdRecipeReader.JsonLdRecipe;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter;
import net.shamansoft.cookbook.html.strategy.RecipeCardCutter.Row;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.html.strategy.StructuredDataStrategy;
import net.shamansoft.cookbook.service.JsonLdRecipeAssembler.Assembly;
import net.shamansoft.cookbook.service.gemini.GeminiResponse;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.cookbook.service.gemini.JsonLdEnrichment;
import net.shamansoft.cookbook.service.gemini.JsonLdRequestBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The JSON-LD path (story 9, increment D): when the page publishes a schema.org {@code Recipe}, code reads its
 * fields, the model only parses the ingredient lines, rewrites the steps and writes the descriptive fields, and code
 * checks that every JSON-LD ingredient line is used once. An answer that breaks the check gets one more attempt with
 * the violations as feedback; if that fails too, the caller falls back to the full-extraction path.
 */
@Service
@Slf4j
public class AnchoredJsonLdExtractor {

    enum Outcome { APPLIED, APPLIED_AFTER_RETRY, SKIPPED, FALLBACK_VIOLATIONS, FALLBACK_MODEL, FALLBACK_ERROR }

    private final ObjectMapper objectMapper;
    private final JsonLdRequestBuilder requestBuilder;
    private final GeminiRestTransformer gemini;
    private final RecipeValidationService validationService;
    private final RecipePostProcessor postProcessor;
    private final MeterRegistry meterRegistry;

    @Value("${recipe.jsonld-anchored.enabled:false}")
    private boolean enabled;

    public AnchoredJsonLdExtractor(ObjectMapper objectMapper, JsonLdRequestBuilder requestBuilder,
                                   @Qualifier("geminiTransformer") GeminiRestTransformer gemini,
                                   RecipeValidationService validationService, RecipePostProcessor postProcessor,
                                   MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.requestBuilder = requestBuilder;
        this.gemini = gemini;
        this.validationService = validationService;
        this.postProcessor = postProcessor;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param cleaned   the cleaner's result for the page
     * @param sourceUrl the page URL
     * @return the recipe, or empty when the content is not JSON-LD, the path is off, or the answer failed the
     * check twice; the caller then runs the full-extraction path
     */
    public Optional<Transformer.Response> extract(HtmlCleaner.Results cleaned, String sourceUrl) {
        if (!enabled || cleaned.strategyUsed() != Strategy.STRUCTURED_DATA) return Optional.empty();
        long start = System.nanoTime();
        try {
            JsonNode node = objectMapper.readTree(StructuredDataStrategy.jsonLd(cleaned.cleanedHtml()));
            JsonLdRecipe fields = JsonLdRecipeReader.read(node);
            if (fields.ingredients().isEmpty() || fields.steps().isEmpty()) {
                return done(Outcome.SKIPPED, null, sourceUrl, fields, start);
            }
            String card = StructuredDataStrategy.card(cleaned.cleanedHtml()).orElse(null);
            List<Row> rows = card == null ? List.of() : RecipeCardCutter.rows(card);
            List<Row> cardRows = CardSectionAssigner.givesSections(rows, fields.ingredients()) ? rows : null;

            GeminiResponse<JsonLdEnrichment> answer = gemini.enrich(requestBuilder.build(node, fields, card), false);
            if (answer.code() != GeminiResponse.Code.SUCCESS) {
                return done(Outcome.FALLBACK_MODEL, null, sourceUrl, fields, start);
            }
            Attempt first = check(fields, cardRows, card, answer, sourceUrl);
            if (first.errors().isEmpty()) return done(Outcome.APPLIED, first, sourceUrl, fields, start);

            log.info("JSON-LD answer broke {} rule(s), retrying with feedback - URL: {}:\n{}",
                    first.errors().size(), sourceUrl, String.join("\n", first.errors()));
            GeminiResponse<JsonLdEnrichment> retry = gemini.enrich(
                    requestBuilder.build(node, fields, card, answer.rawResponse(), first.errors()), true);
            if (retry.code() != GeminiResponse.Code.SUCCESS) {
                return done(Outcome.FALLBACK_MODEL, null, sourceUrl, fields, start);
            }
            Attempt second = check(fields, cardRows, card, retry, sourceUrl);
            if (second.errors().isEmpty()) return done(Outcome.APPLIED_AFTER_RETRY, second, sourceUrl, fields, start);
            log.warn("JSON-LD answer still broke {} rule(s) after feedback - URL: {}:\n{}",
                    second.errors().size(), sourceUrl, String.join("\n", second.errors()));
            return done(Outcome.FALLBACK_VIOLATIONS, null, sourceUrl, fields, start);
        } catch (Exception e) {
            log.warn("JSON-LD path failed, falling back to full extraction - URL: {}", sourceUrl, e);
            return done(Outcome.FALLBACK_ERROR, null, sourceUrl, null, start);
        }
    }

    private record Attempt(Assembly assembly, List<String> errors, String rawResponse) {
    }

    private Attempt check(JsonLdRecipe fields, List<Row> cardRows, String card,
                          GeminiResponse<JsonLdEnrichment> answer, String sourceUrl) {
        Assembly assembly = JsonLdRecipeAssembler.assemble(fields, cardRows, card, answer.data(), sourceUrl);
        List<String> errors = new ArrayList<>(assembly.violations());
        meterRegistry.counter("recipe.jsonld.anchored.violations").increment(errors.size());
        RecipeValidationService.ValidationResult validation = validationService.validate(assembly.recipe());
        if (!validation.isValid()) errors.add(validation.getErrorMessage());
        return new Attempt(assembly, errors, answer.rawResponse());
    }

    private Optional<Transformer.Response> done(Outcome outcome, Attempt attempt, String sourceUrl,
                                                JsonLdRecipe fields, long start) {
        // post-process first: if it throws, the caller's fallback is the only outcome counted
        Optional<Transformer.Response> response = attempt == null ? Optional.empty()
                : Optional.of(Transformer.Response.withRawResponse(true, 1.0,
                List.of(postProcessor.process(attempt.assembly().recipe(), sourceUrl)), attempt.rawResponse()));
        meterRegistry.counter("recipe.jsonld.anchored", "outcome", outcome.name().toLowerCase(Locale.ROOT))
                .increment();
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (attempt == null) {
            log.info("JSON-LD anchored - URL: {}, outcome: {}, lines: {}, {} ms", sourceUrl, outcome,
                    fields == null ? "?" : fields.ingredients().size(), ms);
            return response;
        }
        Assembly assembly = attempt.assembly();
        meterRegistry.counter("recipe.jsonld.anchored.card_only_rows").increment(assembly.cardOnlyRows());
        log.info("JSON-LD anchored - URL: {}, outcome: {}, lines: {}, ingredients: {}, card-only: {}, {} ms",
                sourceUrl, outcome, fields.ingredients().size(), assembly.recipe().ingredients().size(),
                assembly.cardOnlyRows(), ms);
        return response;
    }
}
