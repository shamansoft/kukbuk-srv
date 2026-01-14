package net.shamansoft.cookbook.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
class FirestoreUserProfileRepositoryIntegrationTest {

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );

    private FirestoreUserProfileRepository repository;
    private Firestore firestore;

    @BeforeEach
    void setUp() {
        String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();
        firestore = FirestoreOptions.newBuilder()
                .setProjectId("test-project")
                .setHost(emulatorEndpoint)
                .setCredentials(com.google.auth.oauth2.GoogleCredentials.newBuilder().build())
                .build()
                .getService();

        repository = new FirestoreUserProfileRepository(firestore, new Transformer());
    }

    @Test
    @DisplayName("Should save and retrieve user profile successfully")
    void shouldSaveAndRetrieveUserProfileSuccessfully() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId("test-user-integration")
                .email("test@example.com")
                .displayName("Test User")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        // When - Save
        CompletableFuture<UserProfile> saveResult = repository.save(profile);
        assertThatCode(() -> saveResult.join()).doesNotThrowAnyException();

        // When - Retrieve
        CompletableFuture<Optional<UserProfile>> retrieveResult = repository.findByUserId("test-user-integration");
        Optional<UserProfile> retrievedProfile = retrieveResult.join();

        // Then
        assertThat(retrievedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.userId()).isEqualTo("test-user-integration");
                    assertThat(retrieved.email()).isEqualTo("test@example.com");
                    assertThat(retrieved.displayName()).isEqualTo("Test User");
                    assertThat(retrieved.createdAt()).isNotNull();
                    assertThat(retrieved.updatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should return empty for non-existent user profile")
    void shouldReturnEmptyForNonExistentUserProfile() {
        // When
        CompletableFuture<Optional<UserProfile>> result = repository.findByUserId("non-existent-user");
        Optional<UserProfile> profile = result.join();

        // Then
        assertThat(profile).isEmpty();
    }

    @Test
    @DisplayName("Should check existence correctly")
    void shouldCheckExistenceCorrectly() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId("existence-test-user")
                .email("existence@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        // When - Save first
        repository.save(profile).join();

        // When - Check existence
        CompletableFuture<Boolean> existsResult = repository.existsByUserId("existence-test-user");
        CompletableFuture<Boolean> notExistsResult = repository.existsByUserId("non-existent-user");

        // Then
        assertThat(existsResult.join()).isTrue();
        assertThat(notExistsResult.join()).isFalse();
    }

    @Test
    @DisplayName("Should update OAuth tokens successfully")
    void shouldUpdateOAuthTokensSuccessfully() {
        // Given - Create initial profile
        UserProfile profile = UserProfile.builder()
                .userId("oauth-test-user")
                .email("oauth@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        repository.save(profile).join();

        // When - Update with OAuth tokens
        Map<String, Object> updates = new HashMap<>();
        updates.put("googleOAuthToken", "encrypted-token-123");
        updates.put("googleRefreshToken", "encrypted-refresh-456");
        updates.put("tokenExpiresAt", Timestamp.now());
        updates.put("updatedAt", Timestamp.now());

        CompletableFuture<Void> updateResult = repository.update("oauth-test-user", updates);
        assertThatCode(() -> updateResult.join()).doesNotThrowAnyException();

        // Then - Verify tokens are stored
        Optional<UserProfile> updatedProfile = repository.findByUserId("oauth-test-user").join();
        assertThat(updatedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.googleOAuthToken()).isEqualTo("encrypted-token-123");
                    assertThat(retrieved.googleRefreshToken()).isEqualTo("encrypted-refresh-456");
                    assertThat(retrieved.tokenExpiresAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should handle user profile with storage entity")
    void shouldHandleUserProfileWithStorageEntity() {
        // Given
        StorageEntity storage = StorageEntity.builder()
                .type("googleDrive")  // Use camelCase format matching Firestore enum value
                .connected(true)
                .accessToken("encrypted-access-token")
                .refreshToken("encrypted-refresh-token")
                .expiresAt(Timestamp.now())
                .connectedAt(Timestamp.now())
                .folderId("test-folder-id")
                .folderName("My Recipe Folder")
                .build();

        UserProfile profile = UserProfile.builder()
                .userId("storage-test-user")
                .email("storage@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .storage(storage)
                .build();

        // When - Save
        repository.save(profile).join();

        // When - Retrieve
        Optional<UserProfile> retrievedProfile = repository.findByUserId("storage-test-user").join();

        // Then
        assertThat(retrievedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.storage()).isNotNull();
                    assertThat(retrieved.storage().type()).isEqualTo("googleDrive");
                    assertThat(retrieved.storage().connected()).isTrue();
                    assertThat(retrieved.storage().folderId()).isEqualTo("test-folder-id");
                    assertThat(retrieved.storage().folderName()).isEqualTo("My Recipe Folder");
                });
    }

    @Test
    @DisplayName("Should handle null storage entity")
    void shouldHandleNullStorageEntity() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId("no-storage-test-user")
                .email("nostorage@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .storage(null)
                .build();

        // When - Save
        repository.save(profile).join();

        // When - Retrieve
        Optional<UserProfile> retrievedProfile = repository.findByUserId("no-storage-test-user").join();

        // Then
        assertThat(retrievedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.storage()).isNull();
                    assertThat(retrieved.userId()).isEqualTo("no-storage-test-user");
                });
    }

    @Test
    @DisplayName("Should handle partial updates without overwriting")
    void shouldHandlePartialUpdatesWithoutOverwriting() {
        // Given - Create profile with email
        UserProfile profile = UserProfile.builder()
                .userId("partial-update-user")
                .email("original@example.com")
                .displayName("Original Name")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        repository.save(profile).join();

        // When - Update only displayName
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", "Updated Name");
        updates.put("updatedAt", Timestamp.now());

        repository.update("partial-update-user", updates).join();

        // Then - Email should remain unchanged
        Optional<UserProfile> updatedProfile = repository.findByUserId("partial-update-user").join();
        assertThat(updatedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.email()).isEqualTo("original@example.com");
                    assertThat(retrieved.displayName()).isEqualTo("Updated Name");
                });
    }

    @Test
    @DisplayName("Should handle concurrent access correctly")
    void shouldHandleConcurrentAccessCorrectly() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId("concurrent-test-user")
                .email("concurrent@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        // Save initially
        repository.save(profile).join();

        // When - Multiple concurrent reads
        CompletableFuture<Optional<UserProfile>>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = repository.findByUserId("concurrent-test-user");
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).join();

        // Then - All should succeed
        for (CompletableFuture<Optional<UserProfile>> future : futures) {
            Optional<UserProfile> result = future.join();
            assertThat(result)
                    .isPresent()
                    .get()
                    .extracting(UserProfile::userId)
                    .isEqualTo("concurrent-test-user");
        }
    }

    @Test
    @DisplayName("Should meet performance requirements for retrieval")
    void shouldMeetPerformanceRequirementsForRetrieval() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId("performance-test-user")
                .email("performance@example.com")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        repository.save(profile).join();

        // When - Measure retrieval time
        long startTime = System.currentTimeMillis();
        CompletableFuture<Optional<UserProfile>> result = repository.findByUserId("performance-test-user");
        Optional<UserProfile> retrievedProfile = result.join();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(retrievedProfile).isPresent();
        assertThat(duration)
                .as("Retrieval took %dms, which exceeds the 100ms requirement", duration)
                .isLessThan(100L);
    }

    @Test
    @DisplayName("Should save profile with all OAuth fields")
    void shouldSaveProfileWithAllOAuthFields() {
        // Given
        Timestamp now = Timestamp.now();
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(now.getSeconds() + 3600, 0);

        UserProfile profile = UserProfile.builder()
                .userId("full-oauth-user")
                .email("fullOAuth@example.com")
                .displayName("Full OAuth User")
                .createdAt(now)
                .updatedAt(now)
                .googleOAuthToken("encrypted-access-full")
                .googleRefreshToken("encrypted-refresh-full")
                .tokenExpiresAt(expiresAt)
                .build();

        // When
        repository.save(profile).join();

        // Then
        Optional<UserProfile> retrievedProfile = repository.findByUserId("full-oauth-user").join();
        assertThat(retrievedProfile)
                .isPresent()
                .get()
                .satisfies(retrieved -> {
                    assertThat(retrieved.userId()).isEqualTo("full-oauth-user");
                    assertThat(retrieved.email()).isEqualTo("fullOAuth@example.com");
                    assertThat(retrieved.displayName()).isEqualTo("Full OAuth User");
                    assertThat(retrieved.googleOAuthToken()).isEqualTo("encrypted-access-full");
                    assertThat(retrieved.googleRefreshToken()).isEqualTo("encrypted-refresh-full");
                    assertThat(retrieved.tokenExpiresAt()).isNotNull();
                    assertThat(retrieved.createdAt()).isNotNull();
                    assertThat(retrieved.updatedAt()).isNotNull();
                });
    }
}
