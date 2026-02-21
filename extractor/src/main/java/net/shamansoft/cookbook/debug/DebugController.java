package net.shamansoft.cookbook.debug;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DumpService;
import net.shamansoft.cookbook.service.RecipeParser;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TESTING CONTROLLER - Only active in local/development environments
 * <p>
 * This controller is DISABLED in production via @Profile annotation.
 * It provides unauthenticated endpoints for testing Gemini transformation.
 * <p>
 * Active when Spring profile is:
 * - "local" (local development)
 * - "dev" (development environment)
 * - NOT "prod" or "gcp" (production environments)
 * <p>
 * To activate locally, run with: --spring.profiles.active=local
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Slf4j
@Profile("local")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "false")
public class DebugController {

    private final HtmlExtractor htmlExtractor;
    private final HtmlCleaner htmlPreprocessor;
    private final Transformer transformer;
    private final RecipeValidationService validationService;
    private final ContentHashService contentHashService;
    private final RecipeStoreService recipeStoreService;
    private final RecipeParser recipeParser;

    // DumpService is optional - only available in local profile
    @Autowired(required = false)
    private DumpService dumpService;

    /**
     * TEST ENDPOINT - Only available in non-production environments
     * <p>
     * POST /debug/v1/recipes - Test full recipe extraction flow without
     * authentication
     * <p>
     * This endpoint mirrors the production POST /v1/recipes flow but allows testing
     * without:
     * - Firebase authentication
     * - Google Drive storage
     * - User profile requirements
     * <p>
     * Request body options:
     * - url: URL to fetch recipe from (optional if text is provided)
     * - text: Raw HTML text to transform (optional if url is provided)
     * - compression: Compression type (optional)
     * - returnFormat: "yaml" (default) or "json"
     * - cleanHtml: "auto" (default), "structured", "section", "content", "raw",
     * "disabled"
     * - skipCache: Skip recipe caching (default: false)
     * - verbose: Include detailed processing metadata (default: false)
     * <p>
     * Response format depends on returnFormat and verbose flags:
     * - returnFormat=yaml, verbose=false: Plain text YAML (legacy behavior)
     * - returnFormat=json or verbose=true: JSON with TestTransformResponse
     *
     * @param request TestTransformRequest with configuration options
     * @return Recipe in requested format with optional metadata
     */
    @PostMapping(value = "/v1/recipes", consumes = "application/json", produces = {MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> testTransform(
            @RequestBody RecipeRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId)
            throws IOException, RecipeSerializeException {

        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String sessionId = (headerSessionId != null && !headerSessionId.isBlank())
                ? headerSessionId + "." + requestId
                : requestId;

        log.info("🧪 DEBUG ENDPOINT - /debug/v1/recipes [session: {}]", sessionId);
        log.info("Options: returnFormat={}, cleanHtml={}, skipCache={}, verbose={}",
                request.getReturnFormat(), request.getCleanHtml(), request.isSkipCache(), request.isVerbose());

        // Validate request
        if (!request.hasUrl() && !request.hasText()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Either 'url' or 'text' field is required\"}");
        }

        // Build response with metadata tracking
        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder();
        RecipeResponse.ProcessingMetadata.ProcessingMetadataBuilder metadataBuilder = request.isVerbose()
                ? RecipeResponse.ProcessingMetadata.builder().sessionId(sessionId)
                : null;

        try {
            // Step 1: Check cache (unless skipCache=true)
            String url = request.hasUrl() ? request.url() : "text-input";
            String contentHash = contentHashService.generateContentHash(url);
            boolean cacheHit = false;
            Transformer.Response transformResponse = null;

            if (request.isVerbose()) {
                metadataBuilder.contentHash(contentHash);
            }

            if (!request.isSkipCache()) {
                Optional<RecipeStoreService.CachedRecipes> cached = recipeStoreService.findCachedRecipes(contentHash);
                if (cached.isPresent()) {
                    cacheHit = true;
                    RecipeStoreService.CachedRecipes hit = cached.get();
                    log.info("✅ Cache HIT for hash: {}", contentHash);

                    if (hit.valid()) {
                        List<Recipe> recipes = hit.recipes();
                        transformResponse = recipes.size() == 1
                                ? Transformer.Response.recipe(recipes.get(0))
                                : Transformer.Response.recipes(recipes);
                    } else {
                        transformResponse = Transformer.Response.notRecipe();
                    }
                }
            }

            if (request.isVerbose()) {
                metadataBuilder.cacheHit(cacheHit);
            }

            // Step 2: If not cached, perform full extraction flow
            if (transformResponse == null) {
                long transformStart = System.currentTimeMillis();

                // 2a. Extract HTML
                String htmlContent = extractHtml(request);
                int originalHtmlSize = htmlContent.length();

                // Dump raw HTML if flag enabled
                if (request.isDumpRawHtml() && dumpService != null) {
                    try {
                        String path = dumpService.dump(htmlContent, "raw-html", "html", sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedRawHtmlPath(path);
                        }
                        log.info("📝 Dumped raw HTML to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump raw HTML: {}", e.getMessage());
                    }
                }

                // Dump extracted HTML if flag enabled (same as raw, for clarity)
                if (request.isDumpExtractedHtml() && dumpService != null) {
                    try {
                        String path = dumpService.dump(htmlContent, "extracted-html", "html", sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedExtractedHtmlPath(path);
                        }
                        log.info("📝 Dumped extracted HTML to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump extracted HTML: {}", e.getMessage());
                    }
                }

                // 2b. Preprocess HTML (with configurable strategy)
                HtmlCleaner.Results preprocessed = preprocessHtml(htmlContent, url, request.getCleanHtml());

                if (request.isVerbose()) {
                    metadataBuilder
                            .htmlCleanupStrategy(preprocessed.strategyUsed().name())
                            .originalHtmlSize(originalHtmlSize)
                            .cleanedHtmlSize(preprocessed.cleanedSize())
                            .reductionRatio(preprocessed.reductionRatio());
                }

                log.info("HTML preprocessing - {}", preprocessed.metricsMessage());

                // Dump cleaned HTML if flag enabled
                if (request.isDumpCleanedHtml() && dumpService != null) {
                    try {
                        String path = dumpService.dump(preprocessed.cleanedHtml(), "cleaned-html", "html", sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedCleanedHtmlPath(path);
                        }
                        log.info("📝 Dumped cleaned HTML to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump cleaned HTML: {}", e.getMessage());
                    }
                }

                // 2c. Transform to Recipe using Gemini
                transformResponse = transformer.transform(preprocessed.cleanedHtml(), url);

                long transformTime = System.currentTimeMillis() - transformStart;
                if (request.isVerbose()) {
                    metadataBuilder
                            .transformationTimeMs(transformTime)
                            .geminiModel("gemini-2.5-flash-lite"); // TODO: Get from config
                }

                // Dump raw LLM response if flag enabled
                if (request.isDumpLLMResponse() && dumpService != null && transformResponse.rawLlmResponse() != null) {
                    try {
                        String path = dumpService.dump(transformResponse.rawLlmResponse(), "llm-response", "json",
                                sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedLLMResponsePath(path);
                        }
                        log.info("📝 Dumped LLM response to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump LLM response: {}", e.getMessage());
                    }
                }

                // 2d. Cache result (unless skipCache=true)
                if (!request.isSkipCache()) {
                    if (transformResponse.isRecipe()) {
                        recipeStoreService.storeValidRecipes(contentHash, url, transformResponse.recipes());
                        log.info("Cached {} recipe(s) - Hash: {}", transformResponse.recipes().size(), contentHash);
                    } else {
                        recipeStoreService.storeInvalidRecipe(contentHash, url);
                        log.info("Cached invalid recipe - Hash: {}", contentHash);
                    }
                }
            }

            // Step 3: Build response based on format
            responseBuilder.isRecipe(transformResponse.isRecipe());

            if (!transformResponse.isRecipe()) {
                // Not a recipe - return minimal response
                log.warn("Gemini determined content is NOT a recipe");

                if (request.isVerbose()) {
                    metadataBuilder.totalProcessingTimeMs(System.currentTimeMillis() - startTime);
                    responseBuilder.metadata(metadataBuilder.build());
                }

                if (request.getReturnFormat().equals("json") || request.isVerbose()) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Is-Recipe", "false")
                            .body(responseBuilder.build());
                } else {
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .header("X-Is-Recipe", "false")
                            .body("is_recipe: false\n# Gemini determined this content is not a cooking recipe");
                }
            }

            // Step 4: Format recipe output
            Recipe recipe = transformResponse.recipe();
            log.info("Recipe extracted - Title: '{}', Ingredients: {}, Instructions: {}",
                    recipe.metadata() != null ? recipe.metadata().title() : "N/A",
                    recipe.ingredients() != null ? recipe.ingredients().size() : 0,
                    recipe.instructions() != null ? recipe.instructions().size() : 0);

            if (request.getReturnFormat().equals("json")) {
                responseBuilder.recipeJson(recipe);

                // Dump result JSON if flag enabled
                if (request.isDumpResultJson() && dumpService != null) {
                    try {
                        String path = dumpService.dumpRecipeJson(recipe, sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedResultJsonPath(path);
                        }
                        log.info("📝 Dumped result JSON to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump result JSON: {}", e.getMessage());
                    }
                }
            } else {
                String yaml = validationService.toYaml(recipe);
                responseBuilder.recipeYaml(yaml);

                // Dump result YAML if flag enabled
                if (request.isDumpResultYaml() && dumpService != null) {
                    try {
                        String path = dumpService.dumpRecipeYaml(yaml, sessionId);
                        if (metadataBuilder != null && path != null) {
                            metadataBuilder.dumpedResultYamlPath(path);
                        }
                        log.info("📝 Dumped result YAML to: {}", path);
                    } catch (Exception e) {
                        log.warn("Failed to dump result YAML: {}", e.getMessage());
                    }
                }
            }

            // Step 5: Add verbose metadata if requested
            if (request.isVerbose()) {
                metadataBuilder
                        .validationPassed(true)
                        .totalProcessingTimeMs(System.currentTimeMillis() - startTime);
                responseBuilder.metadata(metadataBuilder.build());
            }

            log.info("✅ Transformation successful - Total time: {}ms", System.currentTimeMillis() - startTime);

            // Return response based on format
            String recipeTitle = recipe.metadata() != null ? recipe.metadata().title() : "Unknown";

            if (request.getReturnFormat().equals("json") || request.isVerbose()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Is-Recipe", "true")
                        .header("X-Recipe-Title", recipeTitle)
                        .body(responseBuilder.build());
            } else {
                // Legacy plain text YAML response
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Is-Recipe", "true")
                        .header("X-Recipe-Title", recipeTitle)
                        .body(responseBuilder.build().getRecipeYaml());
            }

        } catch (Exception e) {
            log.error("Error processing request", e);

            if (request.isVerbose() && metadataBuilder != null) {
                metadataBuilder
                        .validationPassed(false)
                        .validationError(e.getMessage())
                        .totalProcessingTimeMs(System.currentTimeMillis() - startTime);

                return ResponseEntity.status(500)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(RecipeResponse.builder()
                                .isRecipe(false)
                                .metadata(metadataBuilder.build())
                                .build());
            }

            throw e;
        }
    }

    /**
     * Extract HTML from URL or provided text based on request configuration.
     */
    private String extractHtml(RecipeRequest request) throws IOException {
        if (request.hasUrl() && !request.hasText()) {
            log.info("Fetching HTML from URL: {}", request.url());
            String html = htmlExtractor.extractHtml(request.url(), null, request.compression());
            log.info("Fetched HTML - Length: {} chars", html.length());
            return html;
        } else if (request.hasText()) {
            log.info("Using provided text - Length: {} chars", request.text().length());
            return request.text();
        } else {
            log.info("Both URL and text provided, using text");
            return request.text();
        }
    }

    /**
     * Preprocess HTML using the specified strategy.
     * Supports: auto (default cascade), structured, section, content, raw (no
     * preprocessing), disabled.
     */
    private HtmlCleaner.Results preprocessHtml(String html, String url, String strategy) {
        switch (strategy) {
            case "raw":
            case "disabled":
                log.info("HTML preprocessing DISABLED by request");
                return new HtmlCleaner.Results(
                        html,
                        html.length(),
                        html.length(),
                        0.0,
                        Strategy.DISABLED,
                        "Strategy: DISABLED (no preprocessing)");

            case "auto":
            default:
                // Use default cascade (structured → section → content → fallback)
                return htmlPreprocessor.process(html, url);

            // TODO: Implement single-strategy execution
            // case "structured": return runSingleStrategy(html, url,
            // StructuredDataStrategy.class);
            // case "section": return runSingleStrategy(html, url,
            // SectionBasedStrategy.class);
            // case "content": return runSingleStrategy(html, url,
            // ContentFilterStrategy.class);
        }
    }
}
