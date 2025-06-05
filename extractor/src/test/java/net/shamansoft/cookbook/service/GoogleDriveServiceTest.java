package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    @Mock
    private GoogleDrive googleDrive;
    @Mock
    private Transliterator transliterator;

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
}
