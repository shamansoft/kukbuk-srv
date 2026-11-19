package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
    void saveRecipeYaml_delegates() {
        DriveService.UploadResult expected = new DriveService.UploadResult("id1", "url1");
        Function<Optional<String>, String> edit = current -> "yaml";
        when(googleDriveService.saveRecipeYaml("token", "folder", "a.yaml", edit)).thenReturn(expected);

        assertThat(provider.saveRecipeYaml("token", "folder", "a.yaml", edit)).isSameAs(expected);
    }

    @Test
    void editRecipeYaml_delegates() {
        UnaryOperator<String> edit = current -> "new";
        when(googleDriveService.editRecipeYaml("token", "file-1", edit)).thenReturn("new");

        assertThat(provider.editRecipeYaml("token", "file-1", edit)).isEqualTo("new");
        verify(googleDriveService).editRecipeYaml("token", "file-1", edit);
    }
}
