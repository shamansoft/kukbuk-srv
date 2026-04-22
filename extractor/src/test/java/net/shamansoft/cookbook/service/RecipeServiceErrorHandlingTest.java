package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.dto.Compression;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import net.shamansoft.recipe.parser.RecipeParseException;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RecipeService error handling scenarios.
 * Covers HTML extraction failures, Drive upload failures, decompression failures, and parsing errors.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceErrorHandlingTest {

    private static final String USER_ID = "user-123";
    private static final String TITLE = "Test Recipe";
    private static final String URL = "http://example.com";
    private static final String SOURCE_HTML = "compressed-data";
    private static final String HASH = "content-hash-123";
    private static final String FOLDER_ID = "folder-123";
    private static final String ACCESS_TOKEN = "token-123";

    @Mock
    private ContentHashService contentHashService;

    @Mock
    private DriveService driveService;

    @Mock
    private StorageService storageService;

    @Mock
    private RecipeStoreService recipeStoreService;

    @Mock
    private RecipeParser recipeParser;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private HtmlExtractor htmlExtractor;

    @Mock
    private Compressor compressor;

    @Mock
    private Transformer transformer;

    @Mock
    private RecipeValidationService validationService;

    @Mock
    private GeminiRestTransformer geminiRestTransformer;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        lenient().when(contentHashService.generateContentHash(anyString())).thenReturn(HASH);
        lenient().when(recipeStoreService.findCachedRecipes(anyString())).thenReturn(Optional.empty());
        lenient().when(storageService.getStorageInfo(anyString())).thenAnswer(invocation -> {
            StorageInfo storage = new StorageInfo(
                    StorageType.GOOGLE_DRIVE,
                    true,
                    ACCESS_TOKEN,
                    null,
                    null,
                    Instant.now(),
                    FOLDER_ID,
                    null
            );
            return storage;
        });

        recipeService = new RecipeService(
                contentHashService, driveService, storageService, recipeStoreService,
                recipeParser, recipeMapper, htmlExtractor, compressor, transformer,
                validationService, geminiRestTransformer
        );
    }

    // ---- HTML Extraction Error Cases ----------------------------------------

    @Test
    @DisplayName("createRecipe: handles HTML extraction failure")
    void createRecipe_handlesHtmlExtractionFailure() throws IOException {
        setupStorageInfo();
        setupContentHash();
        setupCacheMiss();

        lenient().when(compressor.decompress(SOURCE_HTML)).thenReturn(SOURCE_HTML);
        lenient().when(htmlExtractor.extractHtml(URL, SOURCE_HTML))
                .thenThrow(new IOException("Failed to extract HTML"));

        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.NONE, TITLE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to extract HTML");
    }

    @Test
    @DisplayName("createRecipe: handles decompression failure with fallback to URL fetch")
    void createRecipe_decompressFallsBackToUrlFetch() throws IOException {
        setupStorageInfo();
        setupContentHash();
        setupCacheMiss();

        when(compressor.decompress(SOURCE_HTML))
                .thenThrow(new IOException("Decompression failed"));
        when(htmlExtractor.extractHtml(URL, null)).thenReturn("<html>content</html>");
        when(transformer.transform("<html>content</html>", URL))
                .thenReturn(Transformer.Response.notRecipe());

        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.BASE64_GZIP, TITLE);

        assertThat(response.isRecipe()).isFalse();
    }

    // ---- Drive Upload Failure Cases -----------------------------------------

    @Test
    @DisplayName("createRecipe: handles Drive upload failure")
    void createRecipe_handlesDriveUploadFailure() throws IOException, RecipeSerializeException {
        lenient().when(compressor.decompress(SOURCE_HTML)).thenReturn(SOURCE_HTML);
        lenient().when(htmlExtractor.extractHtml(URL, SOURCE_HTML)).thenReturn("<html>content</html>");

        Recipe recipe = createTestRecipe(TITLE);
        lenient().when(transformer.transform("<html>content</html>", URL))
                .thenReturn(Transformer.Response.recipe(recipe));
        lenient().when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");
        lenient().when(driveService.generateFileName(TITLE)).thenReturn("recipe.yaml");
        lenient().when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, "recipe.yaml", "recipe: yaml"))
                .thenThrow(new ClientException("Drive API error: 403 Forbidden"));

        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.NONE, TITLE))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Drive API error");
    }

    @Test
    @DisplayName("createRecipe: handles YAML serialization failure")
    void createRecipe_handlesYamlSerializationFailure() throws IOException, RecipeSerializeException {
        lenient().when(compressor.decompress(SOURCE_HTML)).thenReturn(SOURCE_HTML);
        lenient().when(htmlExtractor.extractHtml(URL, SOURCE_HTML)).thenReturn("<html>content</html>");

        Recipe recipe = createTestRecipe(TITLE);
        lenient().when(transformer.transform("<html>content</html>", URL))
                .thenReturn(Transformer.Response.recipe(recipe));
        lenient().when(validationService.toYaml(recipe))
                .thenThrow(new RecipeSerializeException("Failed to serialize"));

        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.NONE, TITLE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to convert recipe to YAML");
    }

    // ---- Recipe Parsing Error Cases -----------------------------------------

    @Test
    @DisplayName("getRecipe: handles missing recipe (404)")
    void getRecipe_handlesMissingRecipe() {
        setupStorageInfo();
        when(driveService.getFileMetadata(ACCESS_TOKEN, "missing-id"))
                .thenThrow(new ClientException("404: File not found"));

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, "missing-id"))
                .isInstanceOf(RecipeNotFoundException.class)
                .hasMessageContaining("Recipe not found");
    }


    @Test
    @DisplayName("listRecipes: handles Drive API error gracefully")
    void listRecipes_handlesDriveApiError() {
        setupStorageInfo();
        when(driveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 20, null))
                .thenThrow(new ClientException("Drive API rate limited"));

        assertThatThrownBy(() -> recipeService.listRecipes(USER_ID, 20, null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("rate limited");
    }

    // ---- Cache and Storage Tests -------------------------------------------

    @Test
    @DisplayName("createRecipe: uses cached valid recipe when cache hit")
    void createRecipe_usesCachedRecipe() throws IOException, RecipeSerializeException {
        setupStorageInfo();
        setupContentHash();

        Recipe cachedRecipe = createTestRecipe("Cached Recipe");
        when(recipeStoreService.findCachedRecipes(HASH))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.valid(List.of(cachedRecipe))));
        when(driveService.generateFileName("Cached Recipe")).thenReturn("recipe.yaml");
        when(validationService.toYaml(cachedRecipe)).thenReturn("cached: yaml");
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, "recipe.yaml", "cached: yaml"))
                .thenReturn(new DriveService.UploadResult("file-id", "https://drive/file"));

        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.NONE, TITLE);

        assertThat(response.isRecipe()).isTrue();
        assertThat(response.driveFileId()).isEqualTo("file-id");
    }

    @Test
    @DisplayName("createRecipe: handles cached invalid recipe")
    void createRecipe_handlesCachedInvalidRecipe() throws IOException {
        setupStorageInfo();
        setupContentHash();

        when(recipeStoreService.findCachedRecipes(HASH))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.invalid()));

        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, Compression.NONE, TITLE);

        assertThat(response.isRecipe()).isFalse();
    }

    // ---- Description-based Recipe Creation ---------------------------------

    @Test
    @DisplayName("createRecipeFromDescription: handles Drive upload failure")
    void createRecipeFromDescription_handlesDriveUploadFailure() throws IOException, RecipeSerializeException {
        setupStorageInfo();

        Recipe recipe = createTestRecipe("Description Recipe");
        when(geminiRestTransformer.transformDescription("test description"))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(recipe)).thenReturn("recipe: yaml");
        when(driveService.generateFileName("Description Recipe")).thenReturn("recipe.yaml");
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, "recipe.yaml", "recipe: yaml"))
                .thenThrow(new ClientException("Storage quota exceeded"));

        assertThatThrownBy(() -> recipeService.createRecipeFromDescription(USER_ID, "test description", null, null, Compression.NONE))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Storage quota");
    }

    @Test
    @DisplayName("createRecipeFromDescription: handles YAML serialization failure")
    void createRecipeFromDescription_handlesYamlSerializationFailure() throws IOException, RecipeSerializeException {
        setupStorageInfo();

        Recipe recipe = createTestRecipe("Description Recipe");
        when(geminiRestTransformer.transformDescription("test description"))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(recipe))
                .thenThrow(new RecipeSerializeException("Serialization failed"));

        assertThatThrownBy(() -> recipeService.createRecipeFromDescription(USER_ID, "test description", null, null, Compression.NONE))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- Helper Methods ------------------------------------------------

    private void setupStorageInfo() {
        StorageInfo storage = new StorageInfo(
                StorageType.GOOGLE_DRIVE,
                true,
                ACCESS_TOKEN,
                null,
                null,
                Instant.now(),
                FOLDER_ID,
                null
        );
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
    }

    private void setupContentHash() {
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
    }

    private void setupCacheMiss() {
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
    }

    private Recipe createTestRecipe(String title) {
        return new Recipe(
                true, "1.0.0", "1.0.0",
                new RecipeMetadata(title, URL, null, null, LocalDate.now(),
                        null, null, 4, null, null, null, null, null),
                "Description",
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main")),
                null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null
        );
    }
}
