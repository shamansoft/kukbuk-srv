package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    @Mock
    private GoogleDrive googleDrive;
    @Mock
    private Transliterator transliterator;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private GoogleDriveService googleDriveService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set the folder name via reflection
        ReflectionTestUtils.setField(googleDriveService, "folderName", "test-folder");
    }


    @Test
    void generatesFileNameWithTransliteratedTitle() {
        when(transliterator.toAsciiKebab("Хачапури по Мегрельски")).thenReturn("khachapuri-po-megrelski");
        String result = googleDriveService.generateFileName("Хачапури по Мегрельски");
        assertThat(result).isEqualTo("khachapuri-po-megrelski.yaml");
    }

    @Test
    void generatesFileNameWithDefaultNameWhenTitleIsEmpty() {
        when(transliterator.toAsciiKebab("")).thenReturn("");
        String result = googleDriveService.generateFileName("");
        assertThat(result).startsWith("recipe-").endsWith(".yaml");
    }

    @Test
    void generatesFileNameWithDefaultNameWhenTitleIsNull() {
        when(transliterator.toAsciiKebab(null)).thenReturn("");
        String result = googleDriveService.generateFileName(null);
        assertThat(result).startsWith("recipe-").endsWith(".yaml");
    }

    @Test
    void generatesFileNameWithOriginalTitleWhenTransliterationFails() {
        when(transliterator.toAsciiKebab("Invalid@Title")).thenReturn("");
        String result = googleDriveService.generateFileName("Invalid@Title");
        assertThat(result).startsWith("recipe-").endsWith(".yaml");
    }

    @Test
    void generatesFileNameWithDefaultNameWhenTransliterationOfValidTitleIsEmpty() {
        when(transliterator.toAsciiKebab(" ")).thenReturn("");
        String result = googleDriveService.generateFileName(" ");
        assertThat(result).startsWith("recipe-").endsWith(".yaml");
    }

    @Test
    void testGetOrCreateFolder_whenFolderExists() {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        when(googleDrive.getFolder(eq("test-folder"), eq(authToken)))
                .thenReturn(java.util.Optional.of(new GoogleDrive.Item(folderId, "test-folder")));

        // Act
        String result = googleDriveService.getOrCreateFolder(authToken);

        // Assert
        assertThat(result).isEqualTo(folderId);
        verify(googleDrive, times(1)).getFolder(eq("test-folder"), eq(authToken));
        verifyNoMoreInteractions(googleDrive);
    }

    @Test
    void testGetOrCreateFolder_whenFolderDoesNotExist() {
        // Arrange
        String authToken = "test-token";
        String newFolderId = "new-folder-456";
        when(googleDrive.getFolder(eq("test-folder"), eq(authToken))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq("test-folder"), eq(authToken)))
                .thenReturn(new GoogleDrive.Item(newFolderId, "test-folder"));

        // Act
        String result = googleDriveService.getOrCreateFolder(authToken);

        // Assert
        assertThat(result).isEqualTo(newFolderId);
        verify(googleDrive, times(1)).getFolder(eq("test-folder"), eq(authToken));
        verify(googleDrive, times(1)).createFolder(eq("test-folder"), eq(authToken));
    }

    @Test
    void testUploadRecipeYaml_whenFileExists() {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        String fileName = "recipe.yaml";
        String content = "test-content";
        String fileId = "file-789";

        when(googleDrive.getFile(eq(fileName), eq(folderId), eq(authToken)))
                .thenReturn(java.util.Optional.of(new GoogleDrive.Item(fileId, fileName)));
        when(googleDrive.updateFile(any(GoogleDrive.Item.class), eq(content), eq(authToken)))
                .thenReturn(new GoogleDrive.Item(fileId, fileName));

        // Act
        DriveService.UploadResult result = googleDriveService.uploadRecipeYaml(authToken, folderId, fileName, content);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(fileId);
        verify(googleDrive, times(1)).getFile(eq(fileName), eq(folderId), eq(authToken));
        verify(googleDrive, times(1)).updateFile(any(GoogleDrive.Item.class), eq(content), eq(authToken));
    }

    @Test
    void testUploadRecipeYaml_whenFileExistsWithDifferentId() {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        String fileName = "recipe.yaml";
        String content = "test-content";
        String existingFileId = "file-456";
        String updatedFileId = "file-789";

        when(googleDrive.getFile(eq(fileName), eq(folderId), eq(authToken)))
                .thenReturn(java.util.Optional.of(new GoogleDrive.Item(existingFileId, fileName)));
        when(googleDrive.updateFile(any(GoogleDrive.Item.class), eq(content), eq(authToken)))
                .thenReturn(new GoogleDrive.Item(updatedFileId, fileName));

        // Act
        DriveService.UploadResult result = googleDriveService.uploadRecipeYaml(authToken, folderId, fileName, content);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(updatedFileId);
        verify(googleDrive, times(1)).getFile(eq(fileName), eq(folderId), eq(authToken));
        verify(googleDrive, times(1)).updateFile(any(GoogleDrive.Item.class), eq(content), eq(authToken));
    }

    @Test
    void testUploadRecipeYaml_whenFileDoesNotExist() {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        String fileName = "recipe.yaml";
        String content = "test-content";
        String newFileId = "new-file-789";

        when(googleDrive.getFile(eq(fileName), eq(folderId), eq(authToken))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFile(eq(fileName), eq(folderId), eq(content), eq(authToken)))
                .thenReturn(new GoogleDrive.Item(newFileId, fileName));

        // Act
        DriveService.UploadResult result = googleDriveService.uploadRecipeYaml(authToken, folderId, fileName, content);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(newFileId);
        verify(googleDrive, times(1)).getFile(eq(fileName), eq(folderId), eq(authToken));
        verify(googleDrive, times(1)).createFile(eq(fileName), eq(folderId), eq(content), eq(authToken));
    }

    @Test
    void saveRecipeForUser_withoutFolderId_throwsException() throws Exception {
        // Arrange
        String userId = "user-123";
        String content = "recipe: test";
        String title = "Test Recipe";
        String accessToken = "access-token";

        StorageInfo storage = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(accessToken)
                .folderId(null)  // No folder configured
                .build();

        when(storageService.getStorageInfo(userId)).thenReturn(storage);

        // Act & Assert
        assertThatThrownBy(() -> googleDriveService.saveRecipeForUser(userId, content, title))
                .isInstanceOf(net.shamansoft.cookbook.exception.StorageNotConnectedException.class)
                .hasMessageContaining("No folder configured");

        verify(storageService).getStorageInfo(userId);
        verify(googleDrive, never()).getFile(any(), any(), any());
        verify(googleDrive, never()).createFile(any(), any(), any(), any());
    }

    @Test
    void saveRecipeForUser_withCustomFolder_usesCustomFolder() throws Exception {
        // Arrange
        String userId = "user-123";
        String content = "recipe: test";
        String title = "Test Recipe";
        String accessToken = "access-token";
        String customFolderId = "custom-folder-999";
        String fileName = "test-recipe.yaml";
        String fileId = "file-789";

        StorageInfo storage = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken(accessToken)
                .folderId(customFolderId)
                .build();

        when(storageService.getStorageInfo(userId)).thenReturn(storage);
        when(transliterator.toAsciiKebab(title)).thenReturn("test-recipe");
        when(googleDrive.getFile(eq(fileName), eq(customFolderId), eq(accessToken)))
                .thenReturn(java.util.Optional.empty());
        when(googleDrive.createFile(eq(fileName), eq(customFolderId), eq(content), eq(accessToken)))
                .thenReturn(new GoogleDrive.Item(fileId, fileName));

        // Act
        DriveService.UploadResult result = googleDriveService.saveRecipeForUser(userId, content, title);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(fileId);
        assertThat(result.fileUrl()).isEqualTo("https://drive.google.com/file/d/" + fileId + "/view");
        verify(storageService).getStorageInfo(userId);
        verify(googleDrive, never()).getFolder(any(), any());
    }

    @Test
    void saveRecipeForUser_whenStorageNotConnected_throwsException() throws Exception {
        // Arrange
        String userId = "user-123";
        String content = "recipe: test";
        String title = "Test Recipe";

        when(storageService.getStorageInfo(userId))
                .thenThrow(new StorageNotConnectedException("No storage connected"));

        // Act & Assert
        assertThatThrownBy(() -> googleDriveService.saveRecipeForUser(userId, content, title))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage connected");
    }

    @Test
    void saveRecipeForUser_whenWrongStorageType_throwsException() throws Exception {
        // Arrange
        String userId = "user-123";
        String content = "recipe: test";
        String title = "Test Recipe";

        StorageInfo storage = StorageInfo.builder()
                .type(StorageType.DROPBOX)
                .connected(true)
                .accessToken("token")
                .build();

        when(storageService.getStorageInfo(userId)).thenReturn(storage);

        // Act & Assert
        assertThatThrownBy(() -> googleDriveService.saveRecipeForUser(userId, content, title))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected Google Drive storage");
    }

    // ---- read methods -------------------------------------------------------

    @Test
    void listRecipeFiles_delegatesToDrive() {
        String authToken = "token";
        String folderId = "folder-123";
        GoogleDrive.DriveFileInfo fileInfo =
                new GoogleDrive.DriveFileInfo("file-1", "pasta.yaml", "2024-01-15T10:00:00Z");
        GoogleDrive.DriveFileListResult expected =
                new GoogleDrive.DriveFileListResult(java.util.List.of(fileInfo), "next-token");

        when(googleDrive.listFiles(authToken, folderId, 20, null)).thenReturn(expected);

        GoogleDrive.DriveFileListResult result =
                googleDriveService.listRecipeFiles(authToken, folderId, 20, null);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).name()).isEqualTo("pasta.yaml");
        assertThat(result.nextPageToken()).isEqualTo("next-token");
        verify(googleDrive).listFiles(authToken, folderId, 20, null);
    }

    @Test
    void listRecipeFiles_withPageToken_forwardsTokenToDrive() {
        String authToken = "token";
        String folderId = "folder-123";
        GoogleDrive.DriveFileListResult expected =
                new GoogleDrive.DriveFileListResult(java.util.List.of(), null);

        when(googleDrive.listFiles(authToken, folderId, 10, "page-tok")).thenReturn(expected);

        GoogleDrive.DriveFileListResult result =
                googleDriveService.listRecipeFiles(authToken, folderId, 10, "page-tok");

        assertThat(result.files()).isEmpty();
        verify(googleDrive).listFiles(authToken, folderId, 10, "page-tok");
    }

    @Test
    void getFileContent_delegatesToDrive() {
        String authToken = "token";
        String fileId = "file-abc";
        when(googleDrive.downloadFileAsString(authToken, fileId))
                .thenReturn("is_recipe: true\ntitle: Test");

        String content = googleDriveService.getFileContent(authToken, fileId);

        assertThat(content).contains("is_recipe: true");
        verify(googleDrive).downloadFileAsString(authToken, fileId);
    }

    @Test
    void downloadFile_delegatesToDrive() {
        String authToken = "token";
        String fileId = "file-abc";
        byte[] expected = new byte[]{1, 2, 3};
        when(googleDrive.downloadFileAsBytes(authToken, fileId)).thenReturn(expected);

        byte[] result = googleDriveService.downloadFile(authToken, fileId);

        assertThat(result).isEqualTo(expected);
        verify(googleDrive).downloadFileAsBytes(authToken, fileId);
    }

    @Test
    void getFileMimeType_delegatesToDriveMetadata() {
        String authToken = "token";
        String fileId = "file-abc";
        GoogleDrive.DriveFileMetadata metadata =
                new GoogleDrive.DriveFileMetadata(fileId, "recipe.yaml", "application/x-yaml", "2024-01-15");
        when(googleDrive.getFileMetadata(authToken, fileId)).thenReturn(metadata);

        String mimeType = googleDriveService.getFileMimeType(authToken, fileId);

        assertThat(mimeType).isEqualTo("application/x-yaml");
        verify(googleDrive).getFileMetadata(authToken, fileId);
    }

    @Test
    void getFileMetadata_delegatesToDrive() {
        String authToken = "token";
        String fileId = "file-abc";
        GoogleDrive.DriveFileMetadata expected =
                new GoogleDrive.DriveFileMetadata(fileId, "recipe.yaml", "application/x-yaml", "2024-01-15");
        when(googleDrive.getFileMetadata(authToken, fileId)).thenReturn(expected);

        GoogleDrive.DriveFileMetadata result = googleDriveService.getFileMetadata(authToken, fileId);

        assertThat(result.id()).isEqualTo(fileId);
        assertThat(result.name()).isEqualTo("recipe.yaml");
        assertThat(result.mimeType()).isEqualTo("application/x-yaml");
        verify(googleDrive).getFileMetadata(authToken, fileId);
    }
}
