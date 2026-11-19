package net.shamansoft.cookbook.repository;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.client.ClientException;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.exception.RecipeWriteUnavailableException;
import net.shamansoft.cookbook.service.GoogleDriveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class DriveRecipeWriteLockIntegrationTest {

    @Container
    static final FirestoreEmulatorContainer emulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @Test
    void patchAndResaveAcrossInstancesUseTheLatestFileContents() throws Exception {
        String fileId = UUID.randomUUID().toString();
        var firstUploadStarted = new CountDownLatch(1);
        var finishFirstUpload = new CountDownLatch(1);
        var contents = new AtomicReference<>("recipe\n");
        var drive = mock(GoogleDrive.class);
        when(drive.getFile("recipe.yaml", "folder", "token"))
                .thenReturn(Optional.of(new GoogleDrive.Item(fileId, "recipe.yaml")));
        when(drive.downloadFileAsString("token", fileId)).thenAnswer(invocation -> contents.get());
        doAnswer(invocation -> {
            String yaml = invocation.getArgument(1);
            if (yaml.equals("recipe\nuser_tags: [quick]\n")) {
                firstUploadStarted.countDown();
                if (!finishFirstUpload.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release the first upload");
                }
            }
            contents.set(yaml);
            return null;
        }).when(drive).updateFileContent(anyString(), anyString(), anyString());

        try (var firstFirestore = firestore(); var secondFirestore = firestore();
             var executor = Executors.newSingleThreadExecutor()) {
            var first = new GoogleDriveService("folder", drive, null, null, lockRepository(firstFirestore));
            var second = new GoogleDriveService("folder", drive, null, null, lockRepository(secondFirestore));
            var tags = executor.submit(() -> first.editRecipeYaml("token", fileId,
                    yaml -> yaml + "user_tags: [quick]\n"));
            try {
                assertThat(firstUploadStarted.await(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.editRecipeYaml("token", fileId, yaml -> yaml + "user_notes: More salt\n"))
                        .isInstanceOf(RecipeWriteUnavailableException.class);
                assertThatThrownBy(() -> second.saveRecipeYaml("token", "folder", "recipe.yaml", yaml -> "new"))
                        .isInstanceOf(RecipeWriteUnavailableException.class);
            } finally {
                finishFirstUpload.countDown();
            }
            tags.get(5, TimeUnit.SECONDS);

            second.editRecipeYaml("token", fileId, yaml -> yaml + "user_notes: More salt\n");
            assertThat(contents.get()).isEqualTo("recipe\nuser_tags: [quick]\nuser_notes: More salt\n");
            second.saveRecipeYaml("token", "folder", "recipe.yaml", yaml -> yaml.orElseThrow().replace("recipe\n", "resaved\n"));
            assertThat(contents.get()).isEqualTo("resaved\nuser_tags: [quick]\nuser_notes: More salt\n");
        }
    }

    @Test
    void ambiguousUploadKeepsPermitWhileDefinitiveRejectionReleasesIt() throws Exception {
        try (var firestore = firestore()) {
            var locks = lockRepository(firestore);
            String rejectedId = UUID.randomUUID().toString();
            var rejected = new ClientException("Upload rejected", new HttpClientErrorException(HttpStatus.FORBIDDEN));
            try (var permit = locks.forFile(rejectedId)) {
                assertThatThrownBy(() -> permit.write(() -> { throw rejected; })).isSameAs(rejected);
            }
            try (var permit = locks.forFile(rejectedId)) {
                assertThat(permit.write(() -> "acknowledged")).isEqualTo("acknowledged");
            }

            String timedOutId = UUID.randomUUID().toString();
            try (var permit = locks.forFile(timedOutId)) {
                assertThatThrownBy(() -> permit.write(() -> { throw new ClientException("Network timeout"); }))
                        .isInstanceOf(RecipeWriteUnavailableException.class);
            }
            assertThatThrownBy(() -> locks.forFile(timedOutId)).isInstanceOf(RecipeWriteUnavailableException.class);
        }
    }

    @Test
    void expiredReadOwnerCannotWriteOrReleaseItsSuccessorsPermit() throws Exception {
        try (var firestore = firestore()) {
            var locks = lockRepository(firestore);
            String fileId = UUID.randomUUID().toString();
            String lockId = UUID.nameUUIDFromBytes(("file:" + fileId).getBytes(StandardCharsets.UTF_8)).toString();
            try (var oldPermit = locks.forFile(fileId)) {
                firestore.collection(DriveRecipeWriteLock.COLLECTION).document(lockId)
                        .update(Map.of("readUntil", Timestamp.ofTimeSecondsAndNanos(0, 0))).get(5, TimeUnit.SECONDS);
                try (var newPermit = locks.forFile(fileId)) {
                    assertThatThrownBy(() -> oldPermit.write(() -> { throw new AssertionError("stale writer ran"); }))
                            .isInstanceOf(RecipeWriteUnavailableException.class);
                    oldPermit.close();
                    assertThat(newPermit.write(() -> "acknowledged")).isEqualTo("acknowledged");
                }
            }
        }
    }

    private static Firestore firestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("drive-write-lock-test")
                .setHost(emulator.getEmulatorEndpoint())
                .setCredentials(GoogleCredentials.newBuilder().build())
                .build()
                .getService();
    }

    @SuppressWarnings("unchecked")
    private static DriveRecipeWriteLock lockRepository(Firestore firestore) {
        ObjectProvider<Firestore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(firestore);
        return new DriveRecipeWriteLock(provider);
    }
}
