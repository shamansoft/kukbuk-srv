package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.RecipeWriteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        ReflectionTestUtils.setField(provider, "appFolderName", "");
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
    void saveRecipeYaml_missingFileInAppRoot_joinsPathAndReturnsId() {
        when(dropboxClient.downloadForWrite("/a.yaml", "token"))
                .thenThrow(DropboxClient.FileMissingException.class);
        when(dropboxClient.create(eq("/a.yaml"), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/a.yaml", null));

        DriveService.UploadResult result = provider.saveRecipeYaml("token", "", "a.yaml", current -> {
            assertThat(current).isEmpty();
            return "yaml-content";
        });

        verify(dropboxClient).create("/a.yaml", "yaml-content".getBytes(StandardCharsets.UTF_8), "token");
        verify(dropboxClient, never()).update(any(), any(), any(), any());
        assertThat(result.fileId()).isEqualTo("id:abc");
        assertThat(result.fileUrl()).isEqualTo("https://www.dropbox.com/home");
    }

    @Test
    void saveRecipeYaml_existingRecipeInAppFolder_updatesByIdAndReturnsWebLink() {
        ReflectionTestUtils.setField(provider, "appFolderName", "MyKukBuk Recipes");
        when(dropboxClient.downloadForWrite("/Sub/a.yaml", "token"))
                .thenReturn(new DropboxClient.RecipeDownload("id:abc", "revision1", "user_notes: old"));
        when(dropboxClient.update(eq("id:abc"), eq("revision1"), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/Sub/a.yaml", null));

        DriveService.UploadResult result = provider.saveRecipeYaml("token", "/Sub", "a.yaml",
                current -> current.orElseThrow() + "\ntitle: Pasta");

        verify(dropboxClient).update("id:abc", "revision1",
                "user_notes: old\ntitle: Pasta".getBytes(StandardCharsets.UTF_8), "token");
        assertThat(result.fileUrl())
                .isEqualTo("https://www.dropbox.com/home/Apps/MyKukBuk%20Recipes/Sub/a.yaml");
    }

    @Test
    void fileOperations_nonDropboxId_reportedAsNotFound_noApiCall() {
        assertThatThrownBy(() -> provider.getFileMetadata("token", "1AbCdriveFileId"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> provider.downloadFile("token", "1AbCdriveFileId"))
                .isInstanceOf(ClientException.class);
        verify(dropboxClient, never()).getMetadata(any(), any());
        verify(dropboxClient, never()).downloadAsBytes(any(), any());
    }

    @Test
    void saveRecipeYaml_createConflict_reloadsConcurrentFileBeforeUpdating() {
        String path = "/MyKukBuk/a.yaml";
        when(dropboxClient.downloadForWrite(path, "token"))
                .thenThrow(DropboxClient.FileMissingException.class)
                .thenReturn(new DropboxClient.RecipeDownload("id:abc", "revision1", "user_notes: mine\n"));
        when(dropboxClient.create(eq(path), any(), eq("token")))
                .thenThrow(DropboxClient.WriteConflictException.class);
        when(dropboxClient.update(eq("id:abc"), eq("revision1"), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", path, null));

        DriveService.UploadResult result = provider.saveRecipeYaml("token", "/MyKukBuk", "a.yaml",
                current -> current.orElse("") + "title: Pasta");

        InOrder operations = inOrder(dropboxClient);
        operations.verify(dropboxClient).downloadForWrite(path, "token");
        operations.verify(dropboxClient).create(path, "title: Pasta".getBytes(StandardCharsets.UTF_8), "token");
        operations.verify(dropboxClient).downloadForWrite(path, "token");
        operations.verify(dropboxClient).update("id:abc", "revision1",
                "user_notes: mine\ntitle: Pasta".getBytes(StandardCharsets.UTF_8), "token");
        assertThat(result.fileId()).isEqualTo("id:abc");
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

    @Test
    void editRecipeYaml_updateConflict_reappliesEditToNewerContent() {
        when(dropboxClient.downloadForWrite("id:abc", "token"))
                .thenReturn(new DropboxClient.RecipeDownload("id:abc", "revision1", "title: Pancakes\n"),
                        new DropboxClient.RecipeDownload("id:abc", "revision2", "title: Pancakes\nuser_notes: mine\n"));
        when(dropboxClient.update(eq("id:abc"), eq("revision1"), any(), eq("token")))
                .thenThrow(DropboxClient.WriteConflictException.class);
        when(dropboxClient.update(eq("id:abc"), eq("revision2"), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "pancakes.yaml", "/pancakes.yaml", null));

        String updated = provider.editRecipeYaml("token", "id:abc", yaml -> yaml + "user_tags: [quick]\n");

        String firstAttempt = "title: Pancakes\nuser_tags: [quick]\n";
        String secondAttempt = "title: Pancakes\nuser_notes: mine\nuser_tags: [quick]\n";
        InOrder operations = inOrder(dropboxClient);
        operations.verify(dropboxClient).downloadForWrite("id:abc", "token");
        operations.verify(dropboxClient).update("id:abc", "revision1",
                firstAttempt.getBytes(StandardCharsets.UTF_8), "token");
        operations.verify(dropboxClient).downloadForWrite("id:abc", "token");
        operations.verify(dropboxClient).update("id:abc", "revision2",
                secondAttempt.getBytes(StandardCharsets.UTF_8), "token");
        assertThat(updated).isEqualTo(secondAttempt);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void failedRead_neverCreatesOrUpdatesRecipe(int status) {
        RestClientResponseException error =
                new RestClientResponseException("Dropbox failure", status, "", null, null, null);
        when(dropboxClient.downloadForWrite(any(), eq("token"))).thenThrow(error);

        assertThatThrownBy(() -> provider.saveRecipeYaml("token", "", "pancakes.yaml", ignored -> "replacement"))
                .isSameAs(error);
        assertThatThrownBy(() -> provider.editRecipeYaml("token", "id:abc", ignored -> "replacement"))
                .isSameAs(error);
        verify(dropboxClient, never()).create(any(), any(), any());
        verify(dropboxClient, never()).update(any(), any(), any(), any());
    }

    @Test
    void editRecipeYaml_repeatedConflictsGiveUpWithRetryableError() {
        when(dropboxClient.downloadForWrite("id:abc", "token"))
                .thenReturn(new DropboxClient.RecipeDownload("id:abc", "revision1", "title: Pancakes\n"));
        when(dropboxClient.update(eq("id:abc"), eq("revision1"), any(), eq("token")))
                .thenThrow(DropboxClient.WriteConflictException.class);

        assertThatThrownBy(() -> provider.editRecipeYaml("token", "id:abc", yaml -> yaml + "user_tags: [quick]\n"))
                .isInstanceOf(RecipeWriteUnavailableException.class);
        verify(dropboxClient, times(3)).downloadForWrite("id:abc", "token");
        verify(dropboxClient, times(3)).update(eq("id:abc"), eq("revision1"), any(), eq("token"));
    }

    @Test
    void editRecipeYaml_rejectsNonDropboxId() {
        assertThatThrownBy(() -> provider.editRecipeYaml("token", "drive-id", ignored -> "x"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("not found");
        verifyNoInteractions(dropboxClient);
    }
}
