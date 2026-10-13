package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.CoverImage;
import net.shamansoft.recipe.model.ImageMedia;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import net.shamansoft.recipe.model.VideoMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePostProcessorTest {

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String SOURCE_URL = "https://example.com/recipe";
    private RecipePostProcessor postProcessor;
    private Clock clock;

    @BeforeEach
    void setUp() {
        // Use a fixed clock to make tests deterministic
        Instant fixedInstant = Instant.parse("2026-02-03T10:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        postProcessor = new RecipePostProcessor(SCHEMA_VERSION, clock, new RecipeAdjustmentService());
    }

    @Test
    void testProcess_normalCase() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe",
                "https://old-source.com",
                "Test Author",
                "en",
                LocalDate.of(2020, 1, 1),
                List.of("dessert"),
                List.of("chocolate"),
                4,
                "15m",
                "30m",
                "45m",
                "easy",
                null);

        Recipe recipe = new Recipe(
                true,
                "0.9.0", // Old schema version
                "2.0.0", // Old recipe version
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, SOURCE_URL);

        // Then
        assertNotNull(processed);
        assertEquals(SCHEMA_VERSION, processed.schemaVersion(), "Schema version should be updated");
        assertEquals("1.0.0", processed.recipeVersion(), "Recipe version should be set to 1.0.0");
        assertEquals(SOURCE_URL, processed.metadata().source(), "Source URL should be updated");
        assertEquals(LocalDate.now(clock), processed.metadata().dateCreated(), "Date created should be today");

        // Verify other fields are preserved
        assertEquals("Test Recipe", processed.metadata().title());
        assertEquals("Test Author", processed.metadata().author());
        assertEquals("en", processed.metadata().language());
        assertEquals(List.of("dessert"), processed.metadata().category());
        assertEquals(List.of("chocolate"), processed.metadata().tags());
        assertEquals(4, processed.metadata().servings());
        assertEquals("15m", processed.metadata().prepTime());
        assertEquals("30m", processed.metadata().cookTime());
        assertEquals("45m", processed.metadata().totalTime());
        assertEquals("easy", processed.metadata().difficulty());

        // Verify recipe content is preserved
        assertTrue(processed.isRecipe());
        assertEquals("Test description", processed.description());
        assertEquals(1, processed.ingredients().size());
        assertEquals(1, processed.equipment().size());
        assertEquals(1, processed.instructions().size());
        assertEquals("Test notes", processed.notes());
    }

    @Test
    void testProcess_nullMetadata() {
        // Given
        Recipe recipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                null, // Null metadata
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, SOURCE_URL);

        // Then
        assertNotNull(processed);
        assertNotNull(processed.metadata(), "Metadata should be created");
        assertEquals("Untitled Recipe", processed.metadata().title());
        assertEquals(SOURCE_URL, processed.metadata().source());
        assertEquals(LocalDate.now(clock), processed.metadata().dateCreated());
        assertEquals("en", processed.metadata().language()); // Default value
        assertEquals("medium", processed.metadata().difficulty()); // Default value
        assertEquals(SCHEMA_VERSION, processed.schemaVersion());
        assertEquals("1.0.0", processed.recipeVersion());
    }

    @Test
    void testProcess_metadataWithNullFields() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe",
                null, // No source
                null, // No author
                null, // No language (will default)
                null, // No date created
                null, // No category
                null, // No tags
                null, // No servings
                null, // No prep time
                null, // No cook time
                null, // No total time
                null, // No difficulty (will default)
                null // No cover image
        );

        Recipe recipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, SOURCE_URL);

        // Then
        assertNotNull(processed);
        assertEquals(SOURCE_URL, processed.metadata().source());
        assertEquals(LocalDate.now(clock), processed.metadata().dateCreated());
        assertEquals("Test Recipe", processed.metadata().title());
        assertNull(processed.metadata().author());
        assertNull(processed.metadata().servings());
        assertEquals(SCHEMA_VERSION, processed.schemaVersion());
        assertEquals("1.0.0", processed.recipeVersion());
    }

    @Test
    void testProcess_immutability() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe",
                "https://old-source.com",
                "Test Author",
                "en",
                LocalDate.of(2020, 1, 1),
                List.of("dessert"),
                List.of("chocolate"),
                4,
                "15m",
                "30m",
                "45m",
                "easy",
                null);

        Recipe originalRecipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(originalRecipe, SOURCE_URL);

        // Then
        assertNotSame(originalRecipe, processed, "Should return a new instance");
        assertEquals("0.9.0", originalRecipe.schemaVersion(), "Original schema version should be unchanged");
        assertEquals("2.0.0", originalRecipe.recipeVersion(), "Original recipe version should be unchanged");
        assertEquals("https://old-source.com", originalRecipe.metadata().source(),
                "Original source should be unchanged");
        assertEquals(LocalDate.of(2020, 1, 1), originalRecipe.metadata().dateCreated(),
                "Original date should be unchanged");
    }

    @Test
    void testProcess_nullRecipe() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> postProcessor.process(null, SOURCE_URL));
    }

    @Test
    void testProcess_emptySourceUrl() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe",
                "https://old-source.com",
                "Test Author",
                "en",
                LocalDate.of(2020, 1, 1),
                null, null, null, null, null, null, null, null);

        Recipe recipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, "");

        // Then
        assertNotNull(processed);
        assertEquals("", processed.metadata().source(), "Empty source URL should be set");
    }

    @Test
    void testProcess_nullSourceUrl() {
        // Given
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe",
                "https://old-source.com",
                "Test Author",
                "en",
                LocalDate.of(2020, 1, 1),
                null, null, null, null, null, null, null, null);

        Recipe recipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, null);

        // Then
        assertNotNull(processed);
        assertNull(processed.metadata().source(), "Null source URL should be set");
    }

    @Test
    void testProcess_customSchemaVersion() {
        // Given
        RecipePostProcessor customProcessor = new RecipePostProcessor("2.5.0", clock, new RecipeAdjustmentService());
        Recipe recipe = new Recipe(
                true,
                "0.9.0",
                "2.0.0",
                new RecipeMetadata("Test Recipe", null, null, null, null, null, null, null, null, null,
                        null, null, null),
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = customProcessor.process(recipe, SOURCE_URL);

        // Then
        assertEquals("2.5.0", processed.schemaVersion(), "Custom schema version should be used");
        assertEquals("1.0.0", processed.recipeVersion(), "Recipe version should still be 1.0.0");
    }

    @Test
    void testProcess_isRecipeFalse() {
        // Given
        Recipe recipe = new Recipe(
                false, // Not a recipe
                "0.9.0",
                "2.0.0",
                new RecipeMetadata("Not a Recipe", null, null, null, null, null, null, null, null, null,
                        null, null, null),
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null);

        // When
        Recipe processed = postProcessor.process(recipe, SOURCE_URL);

        // Then
        assertFalse(processed.isRecipe(), "isRecipe flag should be preserved");
        assertEquals(SCHEMA_VERSION, processed.schemaVersion());
        assertEquals("1.0.0", processed.recipeVersion());
    }

    @ParameterizedTest(name = "[{index}] {1} against {0} -> {2}")
    @CsvSource(value = {
            // resolvable: stored as absolute URL
            "https://site.com/recipes/x/ | https://cdn.site.com/a.jpg         | https://cdn.site.com/a.jpg",
            "https://site.com/recipes/x/ | //cdn.example.com/a.jpg            | https://cdn.example.com/a.jpg",
            "https://site.com/recipes/x/ | /assets/a.png                      | https://site.com/assets/a.png",
            "https://site.com/recipes/x/ | images/a.png                       | https://site.com/recipes/x/images/a.png",
            // unresolvable: stored as extracted
            "https://site.com/recipes/x/ | data:image/png;base64,iVBORw0KGgo= | data:image/png;base64,iVBORw0KGgo=",
            "https://site.com/recipes/x/ | /images/%zz.png                    | /images/%zz.png",
            "NULL                        | /assets/a.png                      | /assets/a.png",
            "''                          | /assets/a.png                      | /assets/a.png",
            "not a url                   | images/a.png                       | images/a.png",
    }, delimiterString = " | ", nullValues = "NULL")
    void testProcess_resolvesImagePaths(String sourceUrl, String path, String expected) {
        // Given
        Recipe recipe = recipeWithImages(
                new CoverImage(path, "Cover"),
                List.of(new Instruction(1, "Mix ingredients", "5m", "180°C", List.of(new ImageMedia(path, "Step")))));

        // When
        Recipe processed = postProcessor.process(recipe, sourceUrl);

        // Then
        assertEquals(new CoverImage(expected, "Cover"), processed.metadata().coverImage());
        assertEquals(
                List.of(new Instruction(1, "Mix ingredients", "5m", "180°C", List.of(new ImageMedia(expected, "Step")))),
                processed.instructions());
    }

    @Test
    void testProcess_leavesNonImageMediaUntouched() {
        // Given
        VideoMedia video = new VideoMedia("/videos/step.mp4", "/videos/step.jpg", "2:30");
        List<Instruction> instructions = List.of(
                new Instruction(1, "Mix ingredients", null, null, null),
                new Instruction(2, "Bake", null, null, List.of()),
                new Instruction(3, "Serve", null, null, List.of(video, new ImageMedia("/assets/a.png", "Plated"))));
        Recipe recipe = recipeWithImages(null, instructions);

        // When
        Recipe processed = postProcessor.process(recipe, SOURCE_URL);

        // Then
        assertNull(processed.metadata().coverImage());
        assertEquals(
                List.of(
                        new Instruction(1, "Mix ingredients", null, null, null),
                        new Instruction(2, "Bake", null, null, List.of()),
                        new Instruction(3, "Serve", null, null,
                                List.of(video, new ImageMedia("https://example.com/assets/a.png", "Plated")))),
                processed.instructions());
    }

    private Recipe recipeWithImages(CoverImage coverImage, List<Instruction> instructions) {
        RecipeMetadata metadata = new RecipeMetadata(
                "Test Recipe", "https://old-source.com", null, null, null, null, null, null, null, null,
                null, null, coverImage);
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                metadata,
                "Test description",
                List.of(new Ingredient("flour", "2 cups", "cups", null, null, null, null)),
                List.of("bowl"),
                instructions,
                null,
                "Test notes",
                null);
    }
}
