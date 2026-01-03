package net.shamansoft.cookbook.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.RecipeListResponse;
import net.shamansoft.cookbook.service.RecipeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * REST controller for mobile recipe endpoints.
 * Provides list and detail views for recipes stored in Google Drive.
 * <p>
 * All endpoints require Firebase authentication - userId is injected by FirebaseAuthFilter.
 */
@RestController
@RequestMapping("/v1/recipes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(
        originPatterns = "*",  // Configure for mobile app (restrict in production)
        allowedHeaders = "*",
        exposedHeaders = {"Cache-Control"},
        allowCredentials = "true"
)
public class RecipeController {

    private final RecipeService recipeService;

    /**
     * GET /v1/recipes - List all recipes with full parsing and pagination.
     * <p>
     * Returns complete recipe data (not just metadata) for mobile consumption.
     * Each recipe is parsed from YAML in Google Drive.
     *
     * @param userId    Firebase user ID (injected by FirebaseAuthFilter)
     * @param pageSize  Number of items per page (default 20, max 100)
     * @param pageToken Pagination token from previous response (null for first page)
     * @return RecipeListResponse with recipes and nextPageToken
     * <p>
     * HTTP Status Codes:
     * - 200 OK: Success
     * - 401 Unauthorized: No Firebase token
     * - 428 Precondition Required: Storage not connected
     * - 500 Internal Server Error: Drive API or parsing failure
     */
    @GetMapping
    public ResponseEntity<RecipeListResponse> listRecipes(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String pageToken) {

        log.info("Listing recipes for user: {}, pageSize: {}, pageToken: {}",
                userId, pageSize, pageToken);

        // Get recipes with pagination
        RecipeService.RecipeListResult result = recipeService.listRecipes(
                userId, pageSize, pageToken);

        // Build response
        RecipeListResponse response = RecipeListResponse.builder()
                .recipes(result.recipes())
                .nextPageToken(result.nextPageToken())
                .count(result.recipes().size())
                .build();

        log.info("Returning {} recipes, hasMore: {}",
                response.getCount(), response.getNextPageToken() != null);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(response);
    }

    /**
     * GET /v1/recipes/{id} - Get single recipe by Drive file ID.
     * <p>
     * Returns complete recipe data, useful for deep links, single refresh,
     * or notifications.
     *
     * @param userId Firebase user ID (injected by FirebaseAuthFilter)
     * @param id     Google Drive file ID
     * @return RecipeDto with full recipe data
     * <p>
     * HTTP Status Codes:
     * - 200 OK: Success
     * - 401 Unauthorized: No Firebase token
     * - 404 Not Found: Recipe not found
     * - 422 Unprocessable Entity: Invalid YAML format
     * - 428 Precondition Required: Storage not connected
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeDto> getRecipe(
            @RequestAttribute("userId") String userId,
            @PathVariable String id) {

        log.info("Getting recipe: {} for user: {}", id, userId);

        RecipeDto recipe = recipeService.getRecipe(userId, id);

        log.info("Successfully retrieved recipe: {}", recipe.getTitle());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(recipe);
    }
}
