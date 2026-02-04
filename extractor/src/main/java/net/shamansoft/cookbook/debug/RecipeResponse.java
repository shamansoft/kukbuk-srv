package net.shamansoft.cookbook.debug;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import net.shamansoft.recipe.model.Recipe;

/**
 * Response DTO for debug/test endpoint with optional verbose metadata.
 * Only available in non-production environments (local/dev profiles).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecipeResponse {

    // Core response
    private boolean isRecipe;
    private String recipeYaml; // Only if returnFormat=yaml
    private Recipe recipeJson; // Only if returnFormat=json

    // Verbose metadata (only if verbose=true)
    private ProcessingMetadata metadata;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessingMetadata {
        private String sessionId;

        // HTML preprocessing
        private String htmlCleanupStrategy;
        private Integer originalHtmlSize;
        private Integer cleanedHtmlSize;
        private Double reductionRatio;

        // Caching
        private Boolean cacheHit;
        private String contentHash;

        // Transformation
        private String geminiModel;
        private Long transformationTimeMs;

        // Validation
        private Boolean validationPassed;
        private String validationError;

        // Overall timing
        private Long totalProcessingTimeMs;

        // Debug dump file paths (only if dump flags enabled)
        private String dumpedRawHtmlPath;
        private String dumpedExtractedHtmlPath;
        private String dumpedCleanedHtmlPath;
        private String dumpedLLMResponsePath;
        private String dumpedResultJsonPath;
        private String dumpedResultYamlPath;
    }
}
