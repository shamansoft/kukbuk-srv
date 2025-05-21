package net.shamansoft.cookbook.service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.http.InputStreamContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GoogleDriveSDKServiceTest {

    @Mock
    private Drive drive;

    @Mock
    private Drive.Files files;

    @Mock
    private Drive.Files.List list;

    @Mock
    private Drive.Files.Create create;

    @InjectMocks
    private GoogleDriveSDKService googleDriveSDKService;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        // Set the folder name via reflection
        ReflectionTestUtils.setField(googleDriveSDKService, "folderName", "test-folder");

        // Set up the mock chain for Drive API
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);  // Changed from list(anyString())
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.setOauthToken(anyString())).thenReturn(list);
    }

    @Test
    void testGetOrCreateFolder_whenFolderExists() throws IOException {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        File folder = new File();
        folder.setId(folderId);

        FileList fileList = new FileList();
        fileList.setFiles(Collections.singletonList(folder));

        when(list.execute()).thenReturn(fileList);

        // Act
        String result = googleDriveSDKService.getOrCreateFolder(authToken);

        // Assert
        assertThat(result).isEqualTo(folderId);
        verify(drive, times(1)).files();
        verify(files, times(1)).list();  // Changed from list("drive")
        verify(list, times(1)).setQ("mimeType='application/vnd.google-apps.folder' and name='test-folder' and trashed=false");
        verify(list, times(1)).execute();
    }

    @Test
    void testGetOrCreateFolder_whenFolderDoesNotExist() throws IOException {
        // Arrange
        String authToken = "test-token";
        String newFolderId = "new-folder-456";

        FileList emptyList = new FileList();
        emptyList.setFiles(Collections.emptyList());

        File createdFolder = new File();
        createdFolder.setId(newFolderId);

        when(list.execute()).thenReturn(emptyList);
        when(files.create(any(File.class))).thenReturn(create);
        when(create.setFields(anyString())).thenReturn(create);
        when(create.execute()).thenReturn(createdFolder);

        // Act
        String result = googleDriveSDKService.getOrCreateFolder(authToken);

        // Assert
        assertThat(result).isEqualTo(newFolderId);
        verify(drive, times(1)).files();
        verify(files, times(1)).list();
        verify(list, times(1)).setQ("mimeType='application/vnd.google-apps.folder' and name='test-folder' and trashed=false");
        verify(list, times(1)).execute();
        verify(files, times(1)).create(any(File.class));
    }

    @Test
    void testUploadRecipeYaml_whenFileExists() throws IOException {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        String fileName = "recipe.yaml";
        String content = "test-content";
        String fileId = "file-789";

        FileList fileList = new FileList();
        File existingFile = new File();
        existingFile.setId(fileId);
        fileList.setFiles(Collections.singletonList(existingFile));

        when(list.setQ("name='recipe.yaml' and 'folder-123' in parents and trashed=false")).thenReturn(list);
        when(list.execute()).thenReturn(fileList);

        // Act
        DriveService.UploadResult result = googleDriveSDKService.uploadRecipeYaml(authToken, folderId, fileName, content);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(fileId);
        verify(drive, times(1)).files();
        verify(files, times(1)).list("drive");
        verify(list, times(1)).setQ("name='recipe.yaml' and 'folder-123' in parents and trashed=false");
        verify(files, never()).create(any(File.class), any(InputStreamContent.class));
    }

    @Test
    void testUploadRecipeYaml_whenFileDoesNotExist() throws IOException {
        // Arrange
        String authToken = "test-token";
        String folderId = "folder-123";
        String fileName = "recipe.yaml";
        String content = "test-content";

        FileList emptyList = new FileList();
        emptyList.setFiles(Collections.emptyList());

        when(list.setQ("name='recipe.yaml' and 'folder-123' in parents and trashed=false")).thenReturn(list);
        when(list.execute()).thenReturn(emptyList);

        // Act
        DriveService.UploadResult result = googleDriveSDKService.uploadRecipeYaml(authToken, folderId, fileName, content);

        // Assert
        assertThat(result).isNotNull();
        verify(drive, times(1)).files();
        verify(files, times(1)).list("drive");
        verify(list, times(1)).setQ("name='recipe.yaml' and 'folder-123' in parents and trashed=false");
        verify(files, times(1)).create(any(File.class), any(InputStreamContent.class));
    }
}
