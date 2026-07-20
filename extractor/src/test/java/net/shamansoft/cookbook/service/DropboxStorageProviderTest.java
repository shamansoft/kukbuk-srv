package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DropboxStorageProviderTest {

    @Mock
    private DropboxClient dropboxClient;
    @Mock
    private Transliterator transliterator;

    @InjectMocks
    private DropboxStorageProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(provider, "folderName", "");
    }

    @Test
    void type_isDropbox() {
        assertThat(provider.type()).isEqualTo(StorageType.DROPBOX);
    }

    @Test
    void generateFileName_kebabsAndAddsYaml() {
        when(transliterator.toAsciiKebab("Хачапури")).thenReturn("khachapuri");
        assertThat(provider.generateFileName("Хачапури")).isEqualTo("khachapuri.yaml");
    }

    @Test
    void getOrCreateFolder_blank_returnsAppRoot_noApiCall() {
        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "");
        assertThat(ref.id()).isEqualTo("");
        verify(dropboxClient, never()).createFolder(any(), any());
    }

    @Test
    void getOrCreateFolder_named_createsSubfolder() {
        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "MyKukBuk");
        assertThat(ref.id()).isEqualTo("/MyKukBuk");
        assertThat(ref.name()).isEqualTo("MyKukBuk");
        verify(dropboxClient).createFolder("/MyKukBuk", "token");
    }

    @Test
    void uploadRecipeYaml_appRoot_joinsPathAndReturnsId() {
        when(dropboxClient.upload(any(), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/a.yaml", null));

        DriveService.UploadResult result = provider.uploadRecipeYaml("token", "", "a.yaml", "yaml-content");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(dropboxClient).upload(path.capture(), any(), eq("token"));
        assertThat(path.getValue()).isEqualTo("/a.yaml");
        assertThat(result.fileId()).isEqualTo("id:abc");
        assertThat(result.fileUrl()).isEqualTo("/a.yaml");
    }

    @Test
    void uploadRecipeYaml_subfolder_joinsPath() {
        when(dropboxClient.upload(any(), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/MyKukBuk/a.yaml", null));

        provider.uploadRecipeYaml("token", "/MyKukBuk", "a.yaml", "yaml");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(dropboxClient).upload(path.capture(), any(), eq("token"));
        assertThat(path.getValue()).isEqualTo("/MyKukBuk/a.yaml");
    }

    @Test
    void listRecipeFiles_firstPage_filtersYamlAndMapsCursor() {
        DropboxClient.FileEntry yaml = new DropboxClient.FileEntry("id:1", "a.yaml", "/a.yaml", "2024-01-15T10:00:00Z");
        DropboxClient.FileEntry other = new DropboxClient.FileEntry("id:2", "note.txt", "/note.txt", "2024-01-16T10:00:00Z");
        when(dropboxClient.listFolder("", 20, "token"))
                .thenReturn(new DropboxClient.ListResult(List.of(yaml, other), "CUR", true));

        GoogleDrive.DriveFileListResult result = provider.listRecipeFiles("token", "", 20, null);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).id()).isEqualTo("id:1");
        assertThat(result.files().get(0).modifiedTime()).isEqualTo("2024-01-15T10:00:00Z");
        assertThat(result.nextPageToken()).isEqualTo("CUR");
    }

    @Test
    void listRecipeFiles_withPageToken_usesContinue_noMore() {
        when(dropboxClient.listFolderContinue("CUR", "token"))
                .thenReturn(new DropboxClient.ListResult(List.of(), null, false));

        GoogleDrive.DriveFileListResult result = provider.listRecipeFiles("token", "", 20, "CUR");

        assertThat(result.files()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    void getFileContent_delegates() {
        when(dropboxClient.downloadAsString("id:abc", "token")).thenReturn("yaml");
        assertThat(provider.getFileContent("token", "id:abc")).isEqualTo("yaml");
    }

    @Test
    void downloadFile_delegates() {
        byte[] bytes = {1, 2, 3};
        when(dropboxClient.downloadAsBytes("id:abc", "token")).thenReturn(bytes);
        assertThat(provider.downloadFile("token", "id:abc")).isEqualTo(bytes);
    }

    @Test
    void getFileMimeType_inferredFromName() {
        when(dropboxClient.getMetadata("id:img", "token"))
                .thenReturn(new DropboxClient.FileEntry("id:img", "photo.jpg", "/photo.jpg", null));
        assertThat(provider.getFileMimeType("token", "id:img")).isEqualTo("image/jpeg");
    }

    @Test
    void getFileMetadata_mapsWithInferredMime() {
        when(dropboxClient.getMetadata("id:1", "token"))
                .thenReturn(new DropboxClient.FileEntry("id:1", "a.yaml", "/a.yaml", "2024-01-15T10:00:00Z"));

        GoogleDrive.DriveFileMetadata meta = provider.getFileMetadata("token", "id:1");

        assertThat(meta.id()).isEqualTo("id:1");
        assertThat(meta.name()).isEqualTo("a.yaml");
        assertThat(meta.mimeType()).isEqualTo("application/x-yaml");
        assertThat(meta.modifiedTime()).isEqualTo("2024-01-15T10:00:00Z");
    }
}
