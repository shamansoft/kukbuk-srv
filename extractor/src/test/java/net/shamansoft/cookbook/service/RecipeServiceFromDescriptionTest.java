package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.Compression;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceFromDescriptionTest {

    private static final String USER_ID = "user-123";
    private static final String DESCRIPTION = "Mix flour, eggs and milk. Fry on medium heat until golden.";
    private static final String TITLE = "Crepes";
    private static final String URL = "https://example.com/my-recipe";
    private static final String YAML = "recipe: true\ntitle: Crepes";
    private static final String ACCESS_TOKEN = "access-token-123";
    private static final String FOLDER_ID = "folder-id-123";
    private static final String FILE_NAME = "crepes.yaml";
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
    private Compressor compressor;
    @Mock
    private Transformer transformer;
    @Mock
    private RecipeValidationService validationService;
    @Mock
    private GeminiRestTransformer geminiRestTransformer;

    private RecipeService recipeService;

    private StorageInfo mockStorageInfo;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(contentHashService, driveService, storageService,
                recipeStoreService, recipeParser, recipeMapper, htmlExtractor,
                compressor, transformer, validationService, geminiRestTransformer);
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
    @DisplayName("Should create recipe from description and upload to Drive")
    void shouldCreateRecipeFromDescriptionAndUploadToDrive() throws Exception {
        // Given
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, URL, Compression.NONE);

        // Then
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.driveFileId()).isEqualTo(FILE_ID);
        assertThat(response.driveFileUrl()).isEqualTo(FILE_URL);
        assertThat(response.recipes()).hasSize(1);

        verify(geminiRestTransformer).transformDescription(DESCRIPTION);
        verify(driveService).uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML);
    }

    @Test
    @DisplayName("Should use recipe title from AI when caller title is null")
    void shouldUseRecipeTitleFromAiWhenCallerTitleIsNull() throws Exception {
        // Given
        Recipe recipe = createTestRecipe("AI Generated Title");
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName("AI Generated Title")).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, null, null, Compression.NONE);

        // Then
        assertThat(response.title()).isEqualTo("AI Generated Title");
        assertThat(response.url()).isNull();
    }

    @Test
    @DisplayName("Should handle null URL gracefully")
    void shouldHandleNullUrl() throws Exception {
        // Given
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(ACCESS_TOKEN, FOLDER_ID, FILE_NAME, YAML))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        RecipeResponse response = recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, null, Compression.NONE);

        // Then
        assertThat(response.isRecipe()).isTrue();
        assertThat(response.url()).isNull();
    }

    @Test
    @DisplayName("Should throw StorageNotConnectedException when folder not configured")
    void shouldThrowWhenFolderNotConfigured() {
        // Given
        StorageInfo noFolder = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(false)
                .accessToken(ACCESS_TOKEN)
                .folderId(null)
                .build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(noFolder);

        // When/Then
        assertThatThrownBy(() ->
                recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, URL, Compression.NONE))
                .isInstanceOf(StorageNotConnectedException.class);

        verify(geminiRestTransformer, never()).transformDescription(any());
    }

    @Test
    @DisplayName("Should not use HTML extractor or cache for description-based recipes")
    void shouldNotUseCacheOrHtmlExtractor() throws Exception {
        // Given
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        // When
        recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, URL, Compression.NONE);

        // Then
        verify(htmlExtractor, never()).extractHtml(any(), any());
        verify(recipeStoreService, never()).findCachedRecipes(any());
        verify(recipeStoreService, never()).storeValidRecipes(any(), any(), any());
        verify(contentHashService, never()).generateContentHash(any());
        verify(transformer, never()).transform(any(), any());
    }

    @Test
    @DisplayName("Should propagate RuntimeException from Gemini transformer")
    void shouldPropagateTransformerException() {
        // Given
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenThrow(new RuntimeException("Gemini API error"));

        // When/Then
        assertThatThrownBy(() ->
                recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, URL, Compression.NONE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API error");

        verify(driveService, never()).uploadRecipeYaml(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate RuntimeException from Drive upload")
    void shouldPropagateDriveException() throws Exception {
        // Given
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(eq(ACCESS_TOKEN), eq(FOLDER_ID), eq(FILE_NAME), eq(YAML)))
                .thenThrow(new RuntimeException("Drive upload failed"));

        // When/Then
        assertThatThrownBy(() ->
                recipeService.createRecipeFromDescription(USER_ID, DESCRIPTION, TITLE, URL, Compression.NONE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drive upload failed");
    }

    @Test
    @DisplayName("Should decompress description when compression is BASE64_GZIP")
    void shouldDecompressDescriptionWhenBase64GzipCompression() throws Exception {
        String compressedDescription = "base64encodedContent==";
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(compressor.decompress(compressedDescription)).thenReturn(DESCRIPTION);
        when(geminiRestTransformer.transformDescription(DESCRIPTION))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        recipeService.createRecipeFromDescription(USER_ID, compressedDescription, TITLE, URL, Compression.BASE64_GZIP);

        verify(compressor).decompress(compressedDescription);
        verify(geminiRestTransformer).transformDescription(DESCRIPTION);
    }

    @Test
    @DisplayName("Should treat null compression as NONE (no decompression) for description")
    void shouldTreatNullCompressionAsNoneForDescription() throws Exception {
        String rawDescription = "Mix flour and eggs";
        Recipe recipe = createTestRecipe(TITLE);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(mockStorageInfo);
        when(geminiRestTransformer.transformDescription(rawDescription))
                .thenReturn(Transformer.Response.recipe(recipe));
        when(validationService.toYaml(any(Recipe.class))).thenReturn(YAML);
        when(driveService.generateFileName(TITLE)).thenReturn(FILE_NAME);
        when(driveService.uploadRecipeYaml(any(), any(), any(), any()))
                .thenReturn(new DriveService.UploadResult(FILE_ID, FILE_URL));

        recipeService.createRecipeFromDescription(USER_ID, rawDescription, TITLE, URL, null);

        verify(compressor, never()).decompress(any());
        verify(geminiRestTransformer).transformDescription(rawDescription);
    }

    private Recipe createTestRecipe(String title) {
        return new Recipe(
                true,
                "1.0.0",
                "1.0.0",
                new RecipeMetadata(
                        title, null, null, null,
                        LocalDate.now(), null, null, 2,
                        null, null, null, null, null),
                null,
                List.of(new Ingredient("flour", "200", "g", null, null, null, null)),
                null,
                List.of(new Instruction(1, "Mix ingredients", null, null, null)),
                null, null, null
        );
    }
}
