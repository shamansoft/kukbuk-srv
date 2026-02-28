package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for RecipeService.listRecipes() and RecipeService.getRecipe() methods.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceListGetTest {

    private static final String USER_ID = "user-123";
    private static final String ACCESS_TOKEN = "access-token-abc";
    private static final String FOLDER_ID = "folder-id-456";
    private static final String FILE_ID = "file-id-789";
    private static final String YAML = "is_recipe: true\ntitle: Test\n";

    @Mock private ContentHashService contentHashService;
    @Mock private DriveService driveService;
    @Mock private StorageService storageService;
    @Mock private RecipeStoreService recipeStoreService;
    @Mock private RecipeParser recipeParser;
    @Mock private RecipeMapper recipeMapper;
    @Mock private HtmlExtractor htmlExtractor;
    @Mock private Transformer transformer;
    @Mock private RecipeValidationService validationService;

    @InjectMocks
    private RecipeService recipeService;

    private StorageInfo connectedStorage;

    @BeforeEach
    void setUp() {
        connectedStorage = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(ACCESS_TOKEN)
                .expiresAt(Instant.now().plusSeconds(3600))
                .connectedAt(Instant.now())
                .folderId(FOLDER_ID)
                .build();
    }

    // ---- listRecipes --------------------------------------------------------

    @Test
    @DisplayName("listRecipes: happy path returns parsed recipes and nextPageToken")
    void listRecipes_happyPath() throws Exception {
        GoogleDrive.DriveFileInfo fileInfo =
                new GoogleDrive.DriveFileInfo(FILE_ID, "pasta.yaml", "2024-01-15T10:00:00Z");
        GoogleDrive.DriveFileListResult driveResult =
                new GoogleDrive.DriveFileListResult(List.of(fileInfo), "next-page");

        Recipe recipe = createTestRecipe("Pasta");
        RecipeDto dto = RecipeDto.builder().id(FILE_ID).title("Pasta").build();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, null)).thenReturn(driveResult);
        when(driveService.getFileContent(ACCESS_TOKEN, FILE_ID)).thenReturn(YAML);
        when(recipeParser.parse(YAML)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, fileInfo)).thenReturn(dto);

        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 10, null);

        assertThat(result.recipes()).hasSize(1);
        assertThat(result.recipes().get(0).getTitle()).isEqualTo("Pasta");
        assertThat(result.nextPageToken()).isEqualTo("next-page");
        verify(driveService).listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 10, null);
    }

    @Test
    @DisplayName("listRecipes: forwards pageToken to Drive")
    void listRecipes_withPageToken() throws Exception {
        GoogleDrive.DriveFileListResult driveResult =
                new GoogleDrive.DriveFileListResult(List.of(), null);
        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 20, "token-abc"))
                .thenReturn(driveResult);

        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 20, "token-abc");

        assertThat(result.recipes()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
        verify(driveService).listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 20, "token-abc");
    }

    @Test
    @DisplayName("listRecipes: skips files that fail to parse instead of throwing")
    void listRecipes_skipsUnparsableFiles() throws Exception {
        GoogleDrive.DriveFileInfo goodFile =
                new GoogleDrive.DriveFileInfo("file-1", "good.yaml", "2024-01-01T00:00:00Z");
        GoogleDrive.DriveFileInfo badFile =
                new GoogleDrive.DriveFileInfo("file-2", "bad.yaml", "2024-01-02T00:00:00Z");
        GoogleDrive.DriveFileListResult driveResult =
                new GoogleDrive.DriveFileListResult(List.of(goodFile, badFile), null);

        Recipe recipe = createTestRecipe("Good Recipe");
        RecipeDto dto = RecipeDto.builder().id("file-1").title("Good Recipe").build();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.listRecipeFiles(ACCESS_TOKEN, FOLDER_ID, 20, null)).thenReturn(driveResult);
        when(driveService.getFileContent(ACCESS_TOKEN, "file-1")).thenReturn(YAML);
        when(driveService.getFileContent(ACCESS_TOKEN, "file-2")).thenThrow(new RuntimeException("Drive error"));
        when(recipeParser.parse(YAML)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, goodFile)).thenReturn(dto);

        RecipeService.RecipeListResult result = recipeService.listRecipes(USER_ID, 20, null);

        assertThat(result.recipes()).hasSize(1);
        assertThat(result.recipes().get(0).getTitle()).isEqualTo("Good Recipe");
    }

    @Test
    @DisplayName("listRecipes: throws StorageNotConnectedException when folderId is null")
    void listRecipes_throwsWhenNoFolder() {
        StorageInfo noFolder = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE).connected(true)
                .accessToken(ACCESS_TOKEN).folderId(null).build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(noFolder);

        assertThatThrownBy(() -> recipeService.listRecipes(USER_ID, 20, null))
                .isInstanceOf(StorageNotConnectedException.class);
        verify(driveService, never()).listRecipeFiles(any(), any(), any(int.class), any());
    }

    @Test
    @DisplayName("listRecipes: throws IllegalStateException for non-Drive storage")
    void listRecipes_throwsForWrongStorageType() {
        StorageInfo dropbox = StorageInfo.builder()
                .type(StorageType.DROPBOX).connected(true)
                .accessToken(ACCESS_TOKEN).folderId(FOLDER_ID).build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(dropbox);

        assertThatThrownBy(() -> recipeService.listRecipes(USER_ID, 20, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DROPBOX");
    }

    // ---- getRecipe ----------------------------------------------------------

    @Test
    @DisplayName("getRecipe: happy path returns parsed recipe DTO")
    void getRecipe_happyPath() throws Exception {
        GoogleDrive.DriveFileMetadata metadata =
                new GoogleDrive.DriveFileMetadata(FILE_ID, "pasta.yaml", "application/x-yaml", "2024-01-15T10:00:00Z");
        Recipe recipe = createTestRecipe("Pasta Carbonara");
        RecipeDto dto = RecipeDto.builder().id(FILE_ID).title("Pasta Carbonara").build();

        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.getFileMetadata(ACCESS_TOKEN, FILE_ID)).thenReturn(metadata);
        when(driveService.getFileContent(ACCESS_TOKEN, FILE_ID)).thenReturn(YAML);
        when(recipeParser.parse(YAML)).thenReturn(recipe);
        when(recipeMapper.toDto(recipe, metadata)).thenReturn(dto);

        RecipeDto result = recipeService.getRecipe(USER_ID, FILE_ID);

        assertThat(result.getId()).isEqualTo(FILE_ID);
        assertThat(result.getTitle()).isEqualTo("Pasta Carbonara");
        verify(driveService).getFileMetadata(ACCESS_TOKEN, FILE_ID);
        verify(driveService).getFileContent(ACCESS_TOKEN, FILE_ID);
    }

    @Test
    @DisplayName("getRecipe: throws RecipeNotFoundException when Drive returns 404 in message")
    void getRecipe_throwsNotFoundWhenDrive404() {
        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.getFileMetadata(ACCESS_TOKEN, FILE_ID))
                .thenThrow(new RuntimeException("404 not found"));

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(RecipeNotFoundException.class)
                .hasMessageContaining(FILE_ID);
    }

    @Test
    @DisplayName("getRecipe: rethrows non-404 exceptions as-is")
    void getRecipe_rethrowsOtherExceptions() {
        when(storageService.getStorageInfo(USER_ID)).thenReturn(connectedStorage);
        when(driveService.getFileMetadata(ACCESS_TOKEN, FILE_ID))
                .thenThrow(new RuntimeException("Connection timeout"));

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection timeout");
    }

    @Test
    @DisplayName("getRecipe: throws StorageNotConnectedException when storage missing")
    void getRecipe_throwsWhenStorageNotConnected() {
        when(storageService.getStorageInfo(USER_ID))
                .thenThrow(new StorageNotConnectedException("No storage"));

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(StorageNotConnectedException.class);
        verify(driveService, never()).getFileMetadata(anyString(), anyString());
    }

    @Test
    @DisplayName("getRecipe: throws IllegalStateException for non-Drive storage")
    void getRecipe_throwsForWrongStorageType() {
        StorageInfo dropbox = StorageInfo.builder()
                .type(StorageType.DROPBOX).connected(true)
                .accessToken(ACCESS_TOKEN).folderId(FOLDER_ID).build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(dropbox);

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private Recipe createTestRecipe(String title) {
        return new Recipe(
                true, "1.0.0", "1.0.0",
                new RecipeMetadata(title, "https://example.com", null, null, LocalDate.now(),
                        null, null, 4, null, null, null, null, null),
                "Delicious recipe",
                List.of(new Ingredient("flour", "1", "cup", null, false, null, "main")),
                null,
                List.of(new Instruction(1, "Mix", null, null, null)),
                null, null, null);
    }
}
