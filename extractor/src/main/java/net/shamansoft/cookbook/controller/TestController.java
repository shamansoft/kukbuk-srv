package net.shamansoft.cookbook.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.TestTransformRequest;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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
@RequestMapping("/v1/recipes")
@RequiredArgsConstructor
@Slf4j
@Profile("!prod & !gcp")  // Only active when NOT in prod or gcp profiles
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "false")
public class TestController {

    private final HtmlExtractor htmlExtractor;
    private final Transformer transformer;
    private final RecipeValidationService validationService;

    /**
     * TEST ENDPOINT - Only available in non-production environments
     * <p>
     * POST /v1/recipes/test-transform - Test Gemini transformation without authentication
     * <p>
     * This endpoint allows testing the Gemini AI transformation directly without:
     * - Firebase authentication
     * - Google Drive storage
     * - User profile requirements
     * <p>
     * Request body:
     * - url: URL to fetch recipe from (optional if text is provided)
     * - text: Raw HTML text to transform (optional if url is provided)
     * <p>
     * Response: Raw YAML string of the transformed recipe
     *
     * @param request TestTransformRequest with url OR text
     * @return YAML string representation of the recipe
     */
    @PostMapping(
            value = "/test-transform",
            consumes = "application/json",
            produces = "text/plain"
    )
    public ResponseEntity<String> testTransform(@RequestBody TestTransformRequest request)
            throws IOException, RecipeSerializeException {

        log.info("🧪 TEST ENDPOINT - /test-transform (active profile: local/dev only)");

        // Validate request
        if (!request.hasUrl() && !request.hasText()) {
            return ResponseEntity.badRequest()
                    .body("Error: Either 'url' or 'text' field is required");
        }

        String htmlContent;

        // Extract HTML from URL or use provided text
        if (request.hasUrl() && !request.hasText()) {
            log.info("Fetching HTML from URL: {}", request.url());
            htmlContent = htmlExtractor.extractHtml(request.url(), null, null);
            log.info("Fetched HTML - Length: {} chars", htmlContent.length());
        } else if (request.hasText()) {
            log.info("Using provided text - Length: {} chars", request.text().length());
            htmlContent = request.text();
        } else {
            log.info("Both URL and text provided, using text");
            htmlContent = request.text();
        }

        // Transform HTML to Recipe using Gemini
        log.info("Transforming HTML with Gemini...");
        Transformer.Response response = transformer.transform(htmlContent);

        // Check if it's a recipe
        if (!response.isRecipe()) {
            log.warn("Gemini determined content is NOT a recipe");
            return ResponseEntity.ok()
                    .header("X-Is-Recipe", "false")
                    .body("is_recipe: false\n# Gemini determined this content is not a cooking recipe");
        }

        // Convert Recipe to YAML
        Recipe recipe = response.recipe();
        log.info("Recipe extracted - Title: '{}', Ingredients: {}, Instructions: {}",
                recipe.metadata() != null ? recipe.metadata().title() : "N/A",
                recipe.ingredients() != null ? recipe.ingredients().size() : 0,
                recipe.instructions() != null ? recipe.instructions().size() : 0);

        String yaml = validationService.toYaml(recipe);

        log.info("✅ Transformation successful - YAML length: {} chars", yaml.length());

        return ResponseEntity.ok()
                .header("X-Is-Recipe", "true")
                .header("X-Recipe-Title", recipe.metadata() != null ? recipe.metadata().title() : "Unknown")
                .body(yaml);
    }
}
