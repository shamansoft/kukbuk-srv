package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GoogleDriveStorageProviderTest {

    @Mock
    private GoogleDriveService googleDriveService;

    @InjectMocks
    private GoogleDriveStorageProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(provider, "folderName", "_save_a_recipe");
    }

    @Test
    void type_isGoogleDrive() {
        assertThat(provider.type()).isEqualTo(StorageType.GOOGLE_DRIVE);
    }

    @Test
    void getOrCreateFolder_delegatesAndUsesConfiguredName() {
        when(googleDriveService.getOrCreateFolder("token")).thenReturn("folder-123");

        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "ignored");

        assertThat(ref.id()).isEqualTo("folder-123");
        assertThat(ref.name()).isEqualTo("_save_a_recipe");
    }

    @Test
    void listRecipeFiles_delegates() {
        GoogleDrive.DriveFileListResult expected =
                new GoogleDrive.DriveFileListResult(java.util.List.of(), "next");
        when(googleDriveService.listRecipeFiles("token", "folder", 20, null)).thenReturn(expected);

        assertThat(provider.listRecipeFiles("token", "folder", 20, null)).isSameAs(expected);
    }

    @Test
    void uploadRecipeYaml_delegates() {
        DriveService.UploadResult expected = new DriveService.UploadResult("id1", "url1");
        when(googleDriveService.uploadRecipeYaml("token", "folder", "a.yaml", "yaml")).thenReturn(expected);

        assertThat(provider.uploadRecipeYaml("token", "folder", "a.yaml", "yaml")).isSameAs(expected);
    }
}
