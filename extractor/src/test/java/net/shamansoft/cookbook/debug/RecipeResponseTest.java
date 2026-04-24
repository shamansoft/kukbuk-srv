package net.shamansoft.cookbook.debug;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecipeResponse tests")
class RecipeResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private Recipe createTestRecipe(String title) {
        RecipeMetadata metadata = new RecipeMetadata(
                title, "https://example.com", "Test Author", "en",
                null, null, null, 4, null, null, null, null, null
        );
        return new Recipe(
                true, "1.0.0", "1.0.0", metadata, null,
                List.of(new Ingredient("Sugar", "1", "cup", null, null, null, null)),
                null,
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null, null, null
        );
    }

    @Test
    @DisplayName("RecipeResponse builder creates instance with all fields")
    void builderWithAllFields() {
        Recipe recipe = createTestRecipe("Test Recipe");
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("session-123")
                .htmlCleanupStrategy("auto")
                .originalHtmlSize(5000)
                .cleanedHtmlSize(3000)
                .reductionRatio(0.4)
                .cacheHit(false)
                .contentHash("hash-abc")
                .geminiModel("gemini-2.5-flash-lite")
                .transformationTimeMs(1500L)
                .validationPassed(true)
                .totalProcessingTimeMs(2000L)
                .build();

        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("recipe: yaml content")
                .recipeJson(recipe)
                .metadata(metadata)
                .build();

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.getRecipeYaml()).isEqualTo("recipe: yaml content");
        assertThat(response.getRecipeJson()).isEqualTo(recipe);
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getSessionId()).isEqualTo("session-123");
        assertThat(response.getMetadata().getOriginalHtmlSize()).isEqualTo(5000);
    }

    @Test
    @DisplayName("RecipeResponse builder with minimal fields")
    void builderMinimal() {
        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(false)
                .build();

        assertThat(response.isRecipe()).isFalse();
        assertThat(response.getRecipeYaml()).isNull();
        assertThat(response.getRecipeJson()).isNull();
        assertThat(response.getMetadata()).isNull();
    }

    @Test
    @DisplayName("RecipeResponse with only recipeYaml")
    void responseWithYamlOnly() {
        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("recipe: test yaml")
                .build();

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.getRecipeYaml()).isEqualTo("recipe: test yaml");
        assertThat(response.getRecipeJson()).isNull();
    }

    @Test
    @DisplayName("RecipeResponse with only recipeJson")
    void responseWithJsonOnly() {
        Recipe recipe = createTestRecipe("Test Recipe");
        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(true)
                .recipeJson(recipe)
                .build();

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.getRecipeJson()).isEqualTo(recipe);
        assertThat(response.getRecipeYaml()).isNull();
    }

    @Test
    @DisplayName("ProcessingMetadata builder with all fields")
    void processingMetadataComplete() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("session-abc")
                .htmlCleanupStrategy("section")
                .originalHtmlSize(10000)
                .cleanedHtmlSize(7000)
                .reductionRatio(0.3)
                .cacheHit(true)
                .contentHash("hash-xyz")
                .geminiModel("gemini-2.5-flash-lite")
                .transformationTimeMs(2000L)
                .validationPassed(true)
                .validationError(null)
                .totalProcessingTimeMs(2500L)
                .dumpedRawHtmlPath("/path/to/raw.html")
                .dumpedExtractedHtmlPath("/path/to/extracted.html")
                .dumpedCleanedHtmlPath("/path/to/cleaned.html")
                .dumpedLLMResponsePath("/path/to/llm.json")
                .dumpedResultJsonPath("/path/to/result.json")
                .dumpedResultYamlPath("/path/to/result.yaml")
                .build();

        assertThat(metadata.getSessionId()).isEqualTo("session-abc");
        assertThat(metadata.getHtmlCleanupStrategy()).isEqualTo("section");
        assertThat(metadata.getOriginalHtmlSize()).isEqualTo(10000);
        assertThat(metadata.getCleanedHtmlSize()).isEqualTo(7000);
        assertThat(metadata.getReductionRatio()).isEqualTo(0.3);
        assertThat(metadata.getCacheHit()).isTrue();
        assertThat(metadata.getContentHash()).isEqualTo("hash-xyz");
        assertThat(metadata.getGeminiModel()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(metadata.getTransformationTimeMs()).isEqualTo(2000L);
        assertThat(metadata.getValidationPassed()).isTrue();
        assertThat(metadata.getTotalProcessingTimeMs()).isEqualTo(2500L);
        assertThat(metadata.getDumpedRawHtmlPath()).isEqualTo("/path/to/raw.html");
        assertThat(metadata.getDumpedLLMResponsePath()).isEqualTo("/path/to/llm.json");
    }

    @Test
    @DisplayName("ProcessingMetadata with validation error")
    void processingMetadataWithValidationError() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("session-fail")
                .validationPassed(false)
                .validationError("Missing required field: title")
                .build();

        assertThat(metadata.getSessionId()).isEqualTo("session-fail");
        assertThat(metadata.getValidationPassed()).isFalse();
        assertThat(metadata.getValidationError()).isEqualTo("Missing required field: title");
    }

    @Test
    @DisplayName("JSON serialization includes all non-null fields")
    void jsonSerializationNonNull() throws JacksonException {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("s123")
                .totalProcessingTimeMs(1000L)
                .build();

        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("test: yaml")
                .metadata(metadata)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).containsIgnoringCase("recipe");
        assertThat(json).contains("true");
        assertThat(json).contains("\"recipeYaml\":\"test: yaml\"");
        assertThat(json).contains("\"sessionId\":\"s123\"");
        assertThat(json).contains("\"totalProcessingTimeMs\":1000");
    }

    @Test
    @DisplayName("JSON serialization excludes null fields (NON_NULL)")
    void jsonSerializationExcludesNull() throws JacksonException {
        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("test: yaml")
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("\"recipeJson\"");
        assertThat(json).doesNotContain("\"metadata\"");
        assertThat(json).contains("true");
        assertThat(json).contains("\"recipeYaml\"");
    }

    @Test
    @DisplayName("RecipeResponse not-a-recipe with metadata")
    void notARecipeWithMetadata() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("session-notrecipe")
                .totalProcessingTimeMs(500L)
                .validationPassed(false)
                .validationError("is_recipe: false")
                .build();

        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(false)
                .metadata(metadata)
                .build();

        assertThat(response.isRecipe()).isFalse();
        assertThat(response.getRecipeYaml()).isNull();
        assertThat(response.getRecipeJson()).isNull();
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getValidationPassed()).isFalse();
    }

    @Test
    @DisplayName("ProcessingMetadata with cache hit information")
    void processingMetadataCacheInfo() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .cacheHit(true)
                .contentHash("abc123def456")
                .totalProcessingTimeMs(10L)
                .build();

        assertThat(metadata.getCacheHit()).isTrue();
        assertThat(metadata.getContentHash()).isEqualTo("abc123def456");
        assertThat(metadata.getTotalProcessingTimeMs()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ProcessingMetadata with HTML size reduction metrics")
    void processingMetadataHtmlMetrics() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .htmlCleanupStrategy("content")
                .originalHtmlSize(50000)
                .cleanedHtmlSize(15000)
                .reductionRatio(0.7)
                .build();

        assertThat(metadata.getHtmlCleanupStrategy()).isEqualTo("content");
        assertThat(metadata.getOriginalHtmlSize()).isEqualTo(50000);
        assertThat(metadata.getCleanedHtmlSize()).isEqualTo(15000);
        assertThat(metadata.getReductionRatio()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("RecipeResponse can be modified via builder")
    void recipeResponseBuilderModification() {
        RecipeResponse response = RecipeResponse.builder()
                .isRecipe(false)
                .recipeYaml("original: yaml")
                .build();

        RecipeResponse modified = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("modified: yaml")
                .build();

        assertThat(response.isRecipe()).isFalse();
        assertThat(modified.isRecipe()).isTrue();
        assertThat(modified.getRecipeYaml()).isEqualTo("modified: yaml");
    }

    @Test
    @DisplayName("ProcessingMetadata can be modified via setter")
    void processingMetadataSetterModification() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .sessionId("original")
                .build();

        metadata.setSessionId("modified");
        metadata.setTotalProcessingTimeMs(999L);

        assertThat(metadata.getSessionId()).isEqualTo("modified");
        assertThat(metadata.getTotalProcessingTimeMs()).isEqualTo(999L);
    }

    @Test
    @DisplayName("Multiple responses can be created independently")
    void multipleResponsesIndependent() {
        RecipeResponse response1 = RecipeResponse.builder()
                .isRecipe(true)
                .recipeYaml("recipe1: yaml")
                .build();

        RecipeResponse response2 = RecipeResponse.builder()
                .isRecipe(false)
                .build();

        assertThat(response1.isRecipe()).isTrue();
        assertThat(response1.getRecipeYaml()).isEqualTo("recipe1: yaml");
        assertThat(response2.isRecipe()).isFalse();
        assertThat(response2.getRecipeYaml()).isNull();
    }

    @Test
    @DisplayName("ProcessingMetadata with both dump paths set")
    void processingMetadataWithDumpPaths() {
        RecipeResponse.ProcessingMetadata metadata = RecipeResponse.ProcessingMetadata.builder()
                .dumpedRawHtmlPath("/tmp/raw-abc123.html")
                .dumpedCleanedHtmlPath("/tmp/cleaned-abc123.html")
                .dumpedResultJsonPath("/tmp/result-abc123.json")
                .dumpedLLMResponsePath("/tmp/llm-abc123.json")
                .build();

        assertThat(metadata.getDumpedRawHtmlPath()).isEqualTo("/tmp/raw-abc123.html");
        assertThat(metadata.getDumpedCleanedHtmlPath()).isEqualTo("/tmp/cleaned-abc123.html");
        assertThat(metadata.getDumpedResultJsonPath()).isEqualTo("/tmp/result-abc123.json");
        assertThat(metadata.getDumpedLLMResponsePath()).isEqualTo("/tmp/llm-abc123.json");
    }
}
