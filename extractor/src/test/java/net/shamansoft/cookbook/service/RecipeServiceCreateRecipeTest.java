package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for RecipeService.createRecipe() method.
 * Tests the business logic that was extracted from CookbookController.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceCreateRecipeTest {

    private static final String USER_ID = "user-123";
    private static final String TITLE = "Recipe Title";
    private static final String URL = "http://example.com";
    private static final String SOURCE_HTML = "compressed-html-payload";
    private static final String EXTRACTED_HTML = "<html>content</html>";
    private static final String YAML = "recipe: true\ntitle: Test Recipe";
    private static final String HASH = "hash-123";
    private static final String ACCESS_TOKEN = "access-token-123";
    private static final String FOLDER_ID = "folder-id-123";
    private static final String FILE_NAME = "recipe.yaml";
    private static final String FILE_ID = "file-id-123";
    private static final String FILE_URL = "https://drive.google.com/file/d/file-id-123";

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
    private Transformer transformer;
    @Mock
    private RecipeValidationService validationService;

    @InjectMocks
    private RecipeService recipeService;

    private StorageInfo mockStorageInfo;

    @BeforeEach
    void setUp() {
        mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(ACCESS_TOKEN)
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .connectedAt(Instant.now())
                .folderId(FOLDER_ID)
                .build();
    }

    @Test
    @DisplayName("Should create recipe and upload to Drive when HTML transforms into valid recipe")
    void shouldCreateRecipeAndUploadToDrive() throws Exception {
        // Given
        Recipe testRecipe = createTestRecipe();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, null)).thenReturn(EXTRACTED_HTML);
        when(transformer.transform(eq(EXTRACTED_HTML), anyString())).thenReturn(Transformer.Response.recipe(testRecipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.driveFileId()).isEqualTo(FILE_ID);
        assertThat(response.driveFileUrl()).isEqualTo(FILE_URL);

        verify(storageService).getStorageInfo(USER_ID);
        verify(htmlExtractor).extractHtml(URL, SOURCE_HTML, null);
        verify(transformer).transform(eq(EXTRACTED_HTML), anyString());
        verify(recipeStoreService).storeValidRecipes(eq(HASH), eq(URL), any());
        verify(driveService).uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML);
    }

    @Test
    @DisplayName("Should skip Drive upload when content is not a recipe")
    void shouldSkipDriveWhenNotRecipe() throws IOException {
        // Given
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, null)).thenReturn(EXTRACTED_HTML);
        when(transformer.transform(eq(EXTRACTED_HTML), anyString())).thenReturn(Transformer.Response.notRecipe());

        // When
        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isFalse();
        assertThat(response.driveFileId()).isNull();
        assertThat(response.driveFileUrl()).isNull();

        verify(recipeStoreService).storeInvalidRecipe(HASH, URL);
        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should use cached result when recipe already stored")
    void shouldUseCachedResult() throws Exception {
        // Given
        Recipe testRecipe = createTestRecipe();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.valid(List.of(testRecipe))));
        when(validationService.toYaml(testRecipe)).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.driveFileUrl()).isEqualTo(FILE_URL);

        // Should NOT extract HTML or transform when cached
        verify(htmlExtractor, never()).extractHtml(any(), any(), any());
        verify(transformer, never()).transform(anyString(), anyString());
        verify(driveService).uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML);
    }

    @Test
    @DisplayName("Should respect compression parameter")
    void shouldRespectCompressionParameter() throws Exception {
        // Given
        Recipe testRecipe = createTestRecipe();
        String compression = "gzip";
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, compression)).thenReturn(EXTRACTED_HTML);
        when(transformer.transform(eq(EXTRACTED_HTML), anyString())).thenReturn(Transformer.Response.recipe(testRecipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, compression, TITLE);

        // Then
        assertThat(response.isRecipe()).isTrue();
        verify(htmlExtractor).extractHtml(URL, SOURCE_HTML, compression);
    }

    @Test
    @DisplayName("Should throw StorageNotConnectedException when storage not configured")
    void shouldThrowWhenStorageNotConfigured() throws IOException {
        // Given
        when(storageService.getStorageInfo(USER_ID))
                .thenThrow(new StorageNotConnectedException("No storage configured"));

        // When/Then
        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configured");

        verify(htmlExtractor, never()).extractHtml(any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate IOException from HtmlExtractor")
    void shouldPropagateHtmlExtractorException() throws IOException {
        // Given
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, null))
                .thenThrow(new IOException("Failed to extract HTML"));

        // When/Then
        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to extract HTML");

        verify(transformer, never()).transform(anyString(), anyString());
    }

    @Test
    @DisplayName("Should propagate RuntimeException from Transformer")
    void shouldPropagateTransformerException() throws IOException {
        // Given
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, null)).thenReturn(EXTRACTED_HTML);
        when(transformer.transform(eq(EXTRACTED_HTML), anyString())).thenThrow(new RuntimeException("AI transformation failed"));

        // When/Then
        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI transformation failed");

        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate RuntimeException from Drive upload")
    void shouldPropagateDriveUploadException() throws Exception {
        // Given
        Recipe testRecipe = createTestRecipe();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH)).thenReturn(Optional.empty());
        when(htmlExtractor.extractHtml(URL, SOURCE_HTML, null)).thenReturn(EXTRACTED_HTML);
        when(transformer.transform(eq(EXTRACTED_HTML), anyString())).thenReturn(Transformer.Response.recipe(testRecipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenThrow(new RuntimeException("Drive upload failed"));

        // When/Then
        assertThatThrownBy(() -> recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drive upload failed");
    }

    @Test
    @DisplayName("Should handle cached invalid recipe")
    void shouldHandleCachedInvalidRecipe() throws IOException {
        // Given
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(contentHashService.generateContentHash(URL)).thenReturn(HASH);
        when(recipeStoreService.findCachedRecipes(HASH))
                .thenReturn(Optional.of(RecipeStoreService.CachedRecipes.invalid()));

        // When
        RecipeResponse response = recipeService.createRecipe(USER_ID, URL, SOURCE_HTML, null, TITLE);

        // Then
        assertThat(response.isRecipe()).isFalse();
        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    private Recipe createTestRecipe() {
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        TITLE,
                        null,
                        "Test Author",
                        null,
                        LocalDate.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "Test description",
                List.of(new Ingredient("flour", "100g", null, null, null, null, null)),
                List.of("bowl"),
                List.of(new Instruction(null, "Mix ingredients", null, null, null)),
                null,
                "Test notes",
                null
        );
    }
}
