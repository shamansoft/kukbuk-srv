package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.ImageMedia;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Media;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Post-processes recipes after AI transformation to populate deterministic fields.
 * This ensures consistency across all recipes regardless of AI output variations.
 */
@Service
@Slf4j
public class RecipePostProcessor {

    private final String schemaVersion;
    private final Clock clock;
    private final RecipeAdjustmentService adjustmentService;

    public RecipePostProcessor(
            @Value("${recipe.schema.version:1.0.0}") String schemaVersion,
            Clock clock,
            RecipeAdjustmentService adjustmentService) {
        this.schemaVersion = schemaVersion;
        this.clock = clock;
        this.adjustmentService = adjustmentService;
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

        recipe = adjustmentService.adjust(recipe);

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
                    LanguageCodes.normalize(originalMetadata.language()).orElse(null), // null → SDK default "en"
                    LocalDate.now(clock), // Override dateCreated
                    RecipeCategories.normalize(originalMetadata.category()),
                    originalMetadata.tags(),
                    originalMetadata.servings(),
                    originalMetadata.prepTime(),
                    originalMetadata.cookTime(),
                    originalMetadata.totalTime(),
                    originalMetadata.difficulty(),
                    resolveCoverImage(originalMetadata.coverImage(), sourceUrl)
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
                resolveInstructionImages(recipe.instructions(), sourceUrl),
                recipe.nutrition(),
                recipe.notes(),
                recipe.storage()
        );
    }

    private CoverImage resolveCoverImage(CoverImage coverImage, String sourceUrl) {
        if (coverImage == null) {
            return null;
        }
        return new CoverImage(resolveImagePath(coverImage.path(), sourceUrl), coverImage.alt());
    }

    private List<Instruction> resolveInstructionImages(List<Instruction> instructions, String sourceUrl) {
        if (instructions == null) {
            return null;
        }
        return instructions.stream()
                .map(instruction -> instruction.media() == null ? instruction : new Instruction(
                        instruction.step(),
                        instruction.description(),
                        instruction.time(),
                        instruction.temperature(),
                        instruction.media().stream().map(media -> resolveImage(media, sourceUrl)).toList()))
                .toList();
    }

    private Media resolveImage(Media media, String sourceUrl) {
        if (media instanceof ImageMedia image) {
            return new ImageMedia(resolveImagePath(image.path(), sourceUrl), image.alt());
        }
        return media;
    }

    /**
     * Makes a relative image path absolute using the page URL. A path that cannot be resolved
     * is stored as extracted, so nothing the model returned is lost; RecipeMapper omits such
     * paths from API responses.
     */
    private String resolveImagePath(String path, String sourceUrl) {
        return ImageUrlResolver.resolve(path, sourceUrl).orElse(path);
    }
}
