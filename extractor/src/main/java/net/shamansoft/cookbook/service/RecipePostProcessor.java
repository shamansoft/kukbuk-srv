package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Post-processes recipes after AI transformation to populate deterministic fields.
 * This ensures consistency across all recipes regardless of AI output variations.
 */
@Service
@Slf4j
public class RecipePostProcessor {

    private final String schemaVersion;
    private final Clock clock;

    public RecipePostProcessor(
            @Value("${recipe.schema.version:1.0.0}") String schemaVersion,
            Clock clock) {
        this.schemaVersion = schemaVersion;
        this.clock = clock;
    }

    /**
     * Post-processes a recipe to populate deterministic fields.
     *
     * @param recipe    The recipe to process (immutable)
     * @param sourceUrl The source URL of the recipe
     * @return A new Recipe instance with populated fields
     */
    public Recipe process(Recipe recipe, String sourceUrl) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }

        log.debug("Post-processing recipe: schemaVersion={}, sourceUrl={}", schemaVersion, sourceUrl);

        // Handle null metadata case
        RecipeMetadata originalMetadata = recipe.metadata();
        RecipeMetadata updatedMetadata;

        if (originalMetadata == null) {
            // Create new metadata with required fields
            updatedMetadata = new RecipeMetadata(
                    "Untitled Recipe", // title is required
                    sourceUrl,
                    null, // author
                    null, // language (will default to "en")
                    LocalDate.now(clock),
                    null, // category
                    null, // tags
                    null, // servings
                    null, // prepTime
                    null, // cookTime
                    null, // totalTime
                    null, // difficulty (will default to "medium")
                    null  // coverImage
            );
        } else {
            // Update existing metadata with deterministic fields
            updatedMetadata = new RecipeMetadata(
                    originalMetadata.title(),
                    sourceUrl, // Override source
                    originalMetadata.author(),
                    originalMetadata.language(),
                    LocalDate.now(clock), // Override dateCreated
                    originalMetadata.category(),
                    originalMetadata.tags(),
                    originalMetadata.servings(),
                    originalMetadata.prepTime(),
                    originalMetadata.cookTime(),
                    originalMetadata.totalTime(),
                    originalMetadata.difficulty(),
                    originalMetadata.coverImage()
            );
        }

        // Create new Recipe with updated fields
        return new Recipe(
                recipe.isRecipe(),
                schemaVersion, // Override schemaVersion
                "1.0.0", // Override recipeVersion
                updatedMetadata,
                recipe.description(),
                recipe.ingredients(),
                recipe.equipment(),
                recipe.instructions(),
                recipe.nutrition(),
                recipe.notes(),
                recipe.storage()
        );
    }
}
