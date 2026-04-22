package net.shamansoft.cookbook.debug;

import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.html.strategy.Strategy;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DumpService;
import net.shamansoft.cookbook.service.RecipeParser;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DebugController tests")
class DebugControllerTest {

    @Mock private HtmlExtractor htmlExtractor;
    @Mock private HtmlCleaner htmlPreprocessor;
    @Mock private Transformer transformer;
    @Mock private RecipeValidationService validationService;
    @Mock private ContentHashService contentHashService;
    @Mock private RecipeStoreService recipeStoreService;
    @Mock private RecipeParser recipeParser;
    @Mock private DumpService dumpService;

    private DebugController controller;

    @BeforeEach
    void setUp() {
        controller = new DebugController(
                htmlExtractor,
                htmlPreprocessor,
                transformer,
                validationService,
                contentHashService,
                recipeStoreService,
                recipeParser
        );

        lenient().when(contentHashService.generateContentHash(anyString())).thenReturn("hash-abc123");
        lenient().when(recipeStoreService.findCachedRecipes(anyString())).thenReturn(Optional.empty());
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
    @DisplayName("testTransform with URL - happy path")
    void testTransformWithUrlHappyPath() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                null, null, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe content</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml content");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Is-Recipe")).isEqualTo("true");
        verify(htmlExtractor).extractHtml("https://example.com/recipe", null);
        verify(transformer).transform(htmlContent, "https://example.com/recipe");
    }

    @Test
    @DisplayName("testTransform with text input - happy path")
    void testTransformWithTextHappyPath() throws IOException, Exception {
        String htmlText = "<html><body>Recipe content</body></html>";
        RecipeRequest request = new RecipeRequest(
                null, htmlText, null, "yaml", "auto",
                null, null, null, null, null, null, null, null
        );
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlText, htmlText.length(), htmlText.length(), 0.0, Strategy.DISABLED, "test"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlPreprocessor.process(htmlText, "text-input")).thenReturn(cleanResults);
        when(transformer.transform(htmlText, "text-input")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Is-Recipe")).isEqualTo("true");
        verify(transformer).transform(htmlText, "text-input");
    }

    @Test
    @DisplayName("testTransform without URL or text - bad request")
    void testTransformWithoutUrlOrText() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                null, null, null, "yaml", "auto",
                null, null, null, null, null, null, null, null
        );

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("error");
    }

    @Test
    @DisplayName("testTransform with not-a-recipe response - yaml format")
    void testTransformNotRecipeYamlFormat() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/notrecipe", null, null, "yaml", "auto",
                null, null, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Not a recipe</body></html>";
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.notRecipe();

        when(htmlExtractor.extractHtml("https://example.com/notrecipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/notrecipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/notrecipe")).thenReturn(transformResponse);

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Is-Recipe")).isEqualTo("false");
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("is_recipe: false");
    }

    @Test
    @DisplayName("testTransform with JSON format output")
    void testTransformJsonFormat() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "json", "auto",
                null, null, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Is-Recipe")).isEqualTo("true");
        assertThat(response.getBody()).isInstanceOf(RecipeResponse.class);
    }

    @Test
    @DisplayName("testTransform with verbose mode enabled")
    void testTransformVerboseMode() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                null, true, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length() - 10, 0.1, Strategy.DISABLED, "Strategy: DISABLED (10 byte reduction)"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(RecipeResponse.class);
        RecipeResponse recipeResponse = (RecipeResponse) response.getBody();
        assertThat(recipeResponse.getMetadata()).isNotNull();
        assertThat(recipeResponse.getMetadata().getSessionId()).isNotNull();
    }

    @Test
    @DisplayName("testTransform with skipCache option")
    void testTransformSkipCache() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                true, null, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(recipeStoreService, never()).storeValidRecipes(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("testTransform with custom sessionId header")
    void testTransformCustomSessionHeader() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                null, true, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, "custom-session");

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        RecipeResponse recipeResponse = (RecipeResponse) response.getBody();
        assertThat(recipeResponse.getMetadata().getSessionId()).startsWith("custom-session");
    }

    @Test
    @DisplayName("testTransform with cache hit - valid recipe")
    void testTransformCacheHitValidRecipe() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                null, true, null, null, null, null, null, null
        );
        Recipe recipe = createTestRecipe("Cached Recipe");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                "cached", 6, 6, 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        RecipeStoreService.CachedRecipes cached = new RecipeStoreService.CachedRecipes(true, List.of(recipe));

        when(recipeStoreService.findCachedRecipes("hash-abc123")).thenReturn(Optional.of(cached));
        when(validationService.toYaml(recipe)).thenReturn("cached: recipe yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        RecipeResponse recipeResponse = (RecipeResponse) response.getBody();
        assertThat(recipeResponse.getMetadata().getCacheHit()).isTrue();
        verify(transformer, never()).transform(anyString(), anyString());
    }

    @Test
    @DisplayName("testTransform with cache hit - invalid recipe")
    void testTransformCacheHitInvalidRecipe() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/notrecipe", null, null, "yaml", "auto",
                null, true, null, null, null, null, null, null
        );
        RecipeStoreService.CachedRecipes cached = new RecipeStoreService.CachedRecipes(false, List.of());

        when(recipeStoreService.findCachedRecipes("hash-abc123")).thenReturn(Optional.of(cached));

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        RecipeResponse recipeResponse = (RecipeResponse) response.getBody();
        assertThat(recipeResponse.getMetadata().getCacheHit()).isTrue();
        assertThat(recipeResponse.isRecipe()).isFalse();
    }

    @Test
    @DisplayName("testTransform with multiple recipes")
    void testTransformMultipleRecipes() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipes", null, null, "json", "auto",
                null, false, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Multiple recipes</body></html>";
        Recipe recipe1 = createTestRecipe("Recipe 1");
        Recipe recipe2 = createTestRecipe("Recipe 2");
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );
        Transformer.Response transformResponse = Transformer.Response.recipes(List.of(recipe1, recipe2));

        when(htmlExtractor.extractHtml("https://example.com/recipes", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipes")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipes")).thenReturn(transformResponse);

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(RecipeResponse.class);
        verify(recipeStoreService).storeValidRecipes(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("testTransform with transformer exception")
    void testTransformTransformerException() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "auto",
                null, true, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        HtmlCleaner.Results cleanResults = new HtmlCleaner.Results(
                htmlContent, htmlContent.length(), htmlContent.length(), 0.0, Strategy.DISABLED, "Strategy: DISABLED"
        );

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(htmlPreprocessor.process(htmlContent, "https://example.com/recipe")).thenReturn(cleanResults);
        when(transformer.transform(htmlContent, "https://example.com/recipe"))
                .thenThrow(new RuntimeException("Transformer error"));

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        RecipeResponse recipeResponse = (RecipeResponse) response.getBody();
        assertThat(recipeResponse.getMetadata().getValidationPassed()).isFalse();
    }

    @Test
    @DisplayName("testTransform with raw HTML strategy")
    void testTransformRawHtmlStrategy() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "raw",
                null, false, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(htmlPreprocessor, never()).process(anyString(), anyString());
    }


    @Test
    @DisplayName("testTransform with disabled HTML cleaning")
    void testTransformDisabledCleaning() throws IOException, Exception {
        RecipeRequest request = new RecipeRequest(
                "https://example.com/recipe", null, null, "yaml", "disabled",
                null, false, null, null, null, null, null, null
        );
        String htmlContent = "<html><body>Recipe</body></html>";
        Recipe recipe = createTestRecipe("Test Recipe");
        Transformer.Response transformResponse = Transformer.Response.recipe(recipe);

        when(htmlExtractor.extractHtml("https://example.com/recipe", null)).thenReturn(htmlContent);
        when(transformer.transform(htmlContent, "https://example.com/recipe")).thenReturn(transformResponse);
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");

        ResponseEntity<?> response = controller.testTransform(request, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        verify(htmlPreprocessor, never()).process(anyString(), anyString());
    }
}
