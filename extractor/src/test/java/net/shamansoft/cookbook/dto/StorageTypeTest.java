package net.shamansoft.cookbook.dto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageTypeTest {

    @Test
    void fromFirestoreValue_googleDrive_returnsGoogleDrive() {
        assertThat(StorageType.fromFirestoreValue("googleDrive")).isEqualTo(StorageType.GOOGLE_DRIVE);
    }

    @Test
    void fromFirestoreValue_dropbox_returnsDropbox() {
        assertThat(StorageType.fromFirestoreValue("dropbox")).isEqualTo(StorageType.DROPBOX);
    }

    @Test
    void fromFirestoreValue_oneDrive_returnsOneDrive() {
        assertThat(StorageType.fromFirestoreValue("oneDrive")).isEqualTo(StorageType.ONE_DRIVE);
    }

    @Test
    void fromFirestoreValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> StorageType.fromFirestoreValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromFirestoreValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> StorageType.fromFirestoreValue("unknown-type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-type");
    }

    @Test
    void getFirestoreValue_returnsCorrectString() {
        assertThat(StorageType.GOOGLE_DRIVE.getFirestoreValue()).isEqualTo("googleDrive");
        assertThat(StorageType.DROPBOX.getFirestoreValue()).isEqualTo("dropbox");
        assertThat(StorageType.ONE_DRIVE.getFirestoreValue()).isEqualTo("oneDrive");
    }
}
