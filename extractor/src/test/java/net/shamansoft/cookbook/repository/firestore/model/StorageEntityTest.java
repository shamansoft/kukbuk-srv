package net.shamansoft.cookbook.repository.firestore.model;

import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageEntityTest {

    @Test
    void toMap_persistsDropboxType() {
        StorageEntity entity = StorageEntity.builder()
                .type(StorageType.DROPBOX.getFirestoreValue())
                .connected(true)
                .accessToken("enc-access")
                .folderId("")
                .folderName("MyKukBuk")
                .build();

        Map<String, Object> map = entity.toMap();

        assertThat(map.get("type")).isEqualTo("dropbox");
        assertThat(map.get("connected")).isEqualTo(true);
        assertThat(map.get("folderId")).isEqualTo("");
    }

    @Test
    void toMap_persistsGoogleDriveType() {
        StorageEntity entity = StorageEntity.builder()
                .type(StorageType.GOOGLE_DRIVE.getFirestoreValue())
                .connected(true)
                .accessToken("enc-access")
                .build();

        assertThat(entity.toMap().get("type")).isEqualTo("googleDrive");
    }
}
