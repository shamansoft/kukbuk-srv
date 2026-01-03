package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.InvalidRecipeFormatException;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecipeService - List and Get Operations")
class RecipeServiceListGetTest {

    @Mock
    private DriveService googleDriveService;

    @Mock
    private StorageService storageService;

    @Mock
    private RecipeParser recipeParser;

    @Mock
    private RecipeMapper recipeMapper;

    private RecipeService recipeService;

    private static final String USER_ID = "test-user";
    private static final String FILE_ID = "file-123";
    private static final String FOLDER_ID = "folder-456";
    private static final String ACCESS_TOKEN = "token-xyz";
    private static final String REFRESH_TOKEN = "refresh-xyz";
    private static final String YAML_CONTENT = "test yaml";
    private static final String RECIPE_TITLE = "Test Recipe";
    private static final String MODIFIED_TIME = "2024-01-15T10:30:00Z";

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(googleDriveService, storageService, recipeParser, recipeMapper);
    }

    @Test
    @DisplayName("Should list recipes with pagination from custom folder")
    void shouldListRecipesWithPaginationFromCustomFolder() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN, 
                Instant.now(), Instant.now(), FOLDER_ID);
        GoogleDrive.DriveFileInfo file = new GoogleDrive.DriveFileInfo("file-1", "recipe.yaml", MODIFIED_TIME);
        GoogleDrive.DriveFileListResult driveResult = new GoogleDrive.DriveFileListResult(List.of(file), "next-token");
        Recipe recipe = createTestRecipe();
        RecipeDto dto = createTestDto();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, "page-token")).thenReturn(driveResult);
        when(googleDriveService.getFileContent(ACCESS_TOKEN, "file-1")).thenReturn(YAML_CONTENT);
        when(recipeParser.parse(YAML_CONTENT)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, file)).thenReturn(dto);

        // When
        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 10, "page-token");

        // Then
        assertThat(result.recipes()).hasSize(1);
        assertThat(result.nextPageToken()).isEqualTo("next-token");
        verify(googleDriveService).listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, "page-token");
    }

    @Test
    @DisplayName("Should create default folder when not set")
    void shouldCreateDefaultFolderWhenNotSet() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), null);
        GoogleDrive.DriveFileListResult driveResult = new GoogleDrive.DriveFileListResult(Collections.emptyList(), null);

        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.getOrCreateFolder(ACCESS_TOKEN)).thenReturn(FOLDER_ID);
        when(googleDriveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, null)).thenReturn(driveResult);

        // When
        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 10, null);

        // Then
        assertThat(result.recipes()).isEmpty();
        verify(googleDriveService).getOrCreateFolder(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Should skip invalid recipes during listing")
    void shouldSkipInvalidRecipesDuringListing() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        GoogleDrive.DriveFileInfo file1 = new GoogleDrive.DriveFileInfo("file-1", "good.yaml", MODIFIED_TIME);
        GoogleDrive.DriveFileInfo file2 = new GoogleDrive.DriveFileInfo("file-2", "bad.yaml", MODIFIED_TIME);
        GoogleDrive.DriveFileListResult driveResult = new GoogleDrive.DriveFileListResult(List.of(file1, file2), null);
        Recipe recipe = createTestRecipe();
        RecipeDto dto = createTestDto();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, null)).thenReturn(driveResult);
        when(googleDriveService.getFileContent(ACCESS_TOKEN, "file-1")).thenReturn(YAML_CONTENT);
        when(googleDriveService.getFileContent(ACCESS_TOKEN, "file-2")).thenThrow(new InvalidRecipeFormatException("Bad", new Exception()));
        when(recipeParser.parse(YAML_CONTENT)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, file1)).thenReturn(dto);

        // When
        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 10, null);

        // Then
        assertThat(result.recipes()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw for non-Google Drive storage in list")
    void shouldThrowForNonGoogleDriveStorageInList() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.DROPBOX, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);

        // When & Then
        assertThatThrownBy(() -> recipeService.listRecipes(USER_ID, 10, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected Google Drive");
    }

    @Test
    @DisplayName("Should get recipe by file ID successfully")
    void shouldGetRecipeByFileIdSuccessfully() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        GoogleDrive.DriveFileMetadata metadata = new GoogleDrive.DriveFileMetadata(FILE_ID, "recipe.yaml", "text/yaml", MODIFIED_TIME);
        Recipe recipe = createTestRecipe();
        RecipeDto dto = createTestDto();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.getFileMetadata(ACCESS_TOKEN, FILE_ID)).thenReturn(metadata);
        when(googleDriveService.getFileContent(ACCESS_TOKEN, FILE_ID)).thenReturn(YAML_CONTENT);
        when(recipeParser.parse(YAML_CONTENT)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, metadata)).thenReturn(dto);

        // When
        RecipeDto result = recipeService.getRecipe(USER_ID, FILE_ID);

        // Then
        assertThat(result).isEqualTo(dto);
        verify(googleDriveService).getFileMetadata(ACCESS_TOKEN, FILE_ID);
        verify(recipeParser).parse(YAML_CONTENT);
    }

    @Test
    @DisplayName("Should throw RecipeNotFoundException on 404")
    void shouldThrowRecipeNotFoundExceptionOn404() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.getFileMetadata(ACCESS_TOKEN, FILE_ID)).thenThrow(new RuntimeException("404 not found"));

        // When & Then
        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(RecipeNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw RecipeNotFoundException when 'not found' in error")
    void shouldThrowRecipeNotFoundExceptionWhenErrorContainsNotFound() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.getFileMetadata(ACCESS_TOKEN, FILE_ID)).thenThrow(new RuntimeException("not found on server"));

        // When & Then
        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(RecipeNotFoundException.class);
    }

    @Test
    @DisplayName("Should propagate other exceptions")
    void shouldPropagateOtherExceptions() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.getFileMetadata(ACCESS_TOKEN, FILE_ID)).thenThrow(new RuntimeException("Server error"));

        // When & Then
        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Server error");
    }

    @Test
    @DisplayName("Should throw for non-Google Drive storage in get")
    void shouldThrowForNonGoogleDriveStorageInGet() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.DROPBOX, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);

        // When & Then
        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Should handle empty recipe list")
    void shouldHandleEmptyRecipeList() {
        // Given
        StorageInfo storage = new StorageInfo(StorageType.GOOGLE_DRIVE, true, ACCESS_TOKEN, REFRESH_TOKEN,
                Instant.now(), Instant.now(), FOLDER_ID);
        GoogleDrive.DriveFileListResult driveResult = new GoogleDrive.DriveFileListResult(Collections.emptyList(), null);

        when(storageService.getStorageInfo(USER_ID)).thenReturn(storage);
        when(googleDriveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, null)).thenReturn(driveResult);

        // When
        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 10, null);

        // Then
        assertThat(result.recipes()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    // Helper methods
    private Recipe createTestRecipe() {
        RecipeMetadata metadata = new RecipeMetadata(RECIPE_TITLE, "https://example.com", "Author", "en", LocalDate.now(),
                Collections.emptyList(), Collections.emptyList(), 4, "15m", "30m", "45m", "easy", null);
        return new Recipe("1.0.0", "1.0.0", metadata, "Description",
                List.of(mock(net.shamansoft.recipe.model.Ingredient.class)),
                Collections.emptyList(),
                List.of(mock(net.shamansoft.recipe.model.Instruction.class)), null, "Notes", null);
    }

    private RecipeDto createTestDto() {
        return RecipeDto.builder()
                .id(FILE_ID).lastModified(MODIFIED_TIME).title(RECIPE_TITLE)
                .description("Description").author("Author").source("https://example.com")
                .servings(4).prepTime("15m").cookTime("30m").totalTime("45m").difficulty("easy")
                .ingredients(Collections.emptyList()).equipment(Collections.emptyList())
                .instructions(Collections.emptyList()).nutrition(null).notes("Notes")
                .coverImageUrl(null).allImageUrls(Collections.emptyList()).build();
    }
}
