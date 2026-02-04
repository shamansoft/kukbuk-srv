package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import net.shamansoft.cookbook.client.GoogleAuthClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class StorageServiceIntegrationTest {

    @Container
    static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );
    private static final String FOLDER_NAME = "kukbuk";
    private static final String TEST_USER_ID = "integration-test-user";
    private static final String AUTH_CODE = "test-auth-code";
    private static final String REDIRECT_URI = "https://example.com/callback";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String ENCRYPTED_ACCESS = "encrypted-access-token";
    private static final String ENCRYPTED_REFRESH = "encrypted-refresh-token";
    private static final String FOLDER_ID = "test-folder-123";
    private StorageService storageService;
    private Firestore firestore;
    private TokenEncryptionService tokenEncryptionService;
    private GoogleAuthClient googleAuthClient;
    private GoogleDrive googleDrive;

    @BeforeEach
    void setUp() throws Exception {
        String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();
        firestore = FirestoreOptions.newBuilder()
                .setProjectId("test-project")
                .setHost(emulatorEndpoint)
                .setCredentials(com.google.auth.oauth2.GoogleCredentials.newBuilder().build())
                .build()
                .getService();

        // Mock token encryption service
        tokenEncryptionService = mock(TokenEncryptionService.class);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS)).thenReturn(ACCESS_TOKEN);
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH)).thenReturn(REFRESH_TOKEN);

        // Mock GoogleAuthClient for OAuth token exchange
        googleAuthClient = mock(GoogleAuthClient.class);
        GoogleAuthClient.TokenResponse tokenResponse = new GoogleAuthClient.TokenResponse(
                ACCESS_TOKEN, REFRESH_TOKEN, 3600L
        );
        when(googleAuthClient.exchangeAuthorizationCode(eq(AUTH_CODE), eq(REDIRECT_URI)))
                .thenReturn(tokenResponse);

        // Mock GoogleDrive for folder operations
        googleDrive = mock(GoogleDrive.class);
        GoogleDrive.Item folderItem = new GoogleDrive.Item(FOLDER_ID, FOLDER_NAME);
        when(googleDrive.getFolder(eq(FOLDER_NAME), eq(ACCESS_TOKEN)))
                .thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq(FOLDER_NAME), eq(ACCESS_TOKEN)))
                .thenReturn(folderItem);

        storageService = new StorageService(firestore, tokenEncryptionService, googleAuthClient, googleDrive);

        // Set required @Value fields using reflection
        ReflectionTestUtils.setField(storageService, "defaultFolderName", FOLDER_NAME);

        // Create user profile
        firestore.collection("users")
                .document(TEST_USER_ID)
                .set(Map.of("email", "test@example.com"))
                .get();
    }

    @Test
    @DisplayName("Should connect Google Drive and store encrypted tokens")
    void shouldConnectGoogleDriveAndStoreEncryptedTokens() throws Exception {
        // When - Connect Google Drive
        storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_NAME);

        // Then - Verify stored data
        DocumentSnapshot doc = firestore.collection("users")
                .document(TEST_USER_ID)
                .get()
                .get();

        assertThat(doc.exists()).isTrue();

        Map<String, Object> storage = (Map<String, Object>) doc.get("storage");
        assertThat(storage).isNotNull();
        assertThat(storage.get("type")).isEqualTo("googleDrive");
        assertThat(storage.get("connected")).isEqualTo(true);
        assertThat(storage.get("accessToken")).isEqualTo(ENCRYPTED_ACCESS);
        assertThat(storage.get("refreshToken")).isEqualTo(ENCRYPTED_REFRESH);
        assertThat(storage.get("folderId")).isEqualTo(FOLDER_ID);
        assertThat(storage.get("folderName")).isEqualTo(FOLDER_NAME);
        assertThat(storage.get("expiresAt")).isInstanceOf(Timestamp.class);
        assertThat(storage.get("connectedAt")).isInstanceOf(Timestamp.class);
    }

    @Test
    @DisplayName("Should retrieve and decrypt storage info")
    void shouldRetrieveAndDecryptStorageInfo() throws Exception {
        // Given - Connect Google Drive
        storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_NAME);

        // When - Get storage info
        StorageInfo info = storageService.getStorageInfo(TEST_USER_ID);

        // Then - Verify decrypted data
        assertThat(info).isNotNull();
        assertThat(info.type().getFirestoreValue()).isEqualTo("googleDrive");
        assertThat(info.connected()).isTrue();
        assertThat(info.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(info.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(info.folderId()).isEqualTo(FOLDER_ID);
        assertThat(info.folderName()).isEqualTo(FOLDER_NAME);
        assertThat(info.expiresAt()).isNotNull();
        assertThat(info.connectedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update default folder")
    void shouldUpdateDefaultFolder() throws Exception {
        // Given - Connect Google Drive
        storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_NAME);

        // When - Update default folder
        String newFolderId = "new-folder-456";
        storageService.updateDefaultFolder(TEST_USER_ID, newFolderId);

        // Then - Verify updated folder
        DocumentSnapshot doc = firestore.collection("users")
                .document(TEST_USER_ID)
                .get()
                .get();

        Map<String, Object> storage = (Map<String, Object>) doc.get("storage");
        assertThat(storage.get("folderId")).isEqualTo(newFolderId);
    }

    @Test
    @DisplayName("Should disconnect storage and remove data")
    void shouldDisconnectStorageAndRemoveData() throws Exception {
        // Given - Connect Google Drive
        storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_NAME);

        // When - Disconnect storage
        storageService.disconnectStorage(TEST_USER_ID);

        // Then - Verify storage removed
        DocumentSnapshot doc = firestore.collection("users")
                .document(TEST_USER_ID)
                .get()
                .get();

        assertThat(doc.get("storage")).isNull();
    }

    @Test
    @DisplayName("Should check if storage is connected")
    void shouldCheckIfStorageIsConnected() throws Exception {
        // Given - No storage connected initially
        assertThat(storageService.isStorageConnected(TEST_USER_ID)).isFalse();

        // When - Connect Google Drive
        storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_NAME);

        // Then - Storage is connected
        assertThat(storageService.isStorageConnected(TEST_USER_ID)).isTrue();

        // When - Disconnect storage
        storageService.disconnectStorage(TEST_USER_ID);

        // Then - Storage is not connected
        assertThat(storageService.isStorageConnected(TEST_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when getting storage info for non-existent user")
    void shouldThrowExceptionWhenGettingStorageInfoForNonExistentUser() {
        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo("non-existent-user"))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No user profile found");
    }

    @Test
    @DisplayName("Should throw exception when storage not connected")
    void shouldThrowExceptionWhenStorageNotConnected() {
        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(TEST_USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configuration found for user");
    }

    @Test
    @DisplayName("Should handle null refresh token")
    void shouldHandleNullRefreshToken() throws Exception {
        // Mock GoogleAuthClient to throw IllegalStateException when refresh token is missing
        when(googleAuthClient.exchangeAuthorizationCode(eq(AUTH_CODE), eq(REDIRECT_URI)))
                .thenThrow(new IllegalStateException("No refresh token received. Ensure OAuth consent screen requests offline access."));

        // When & Then - Connect without refresh token should fail
        // The service expects a refresh token and will throw IllegalStateException if missing
        assertThatThrownBy(() -> storageService.connectGoogleDrive(TEST_USER_ID, AUTH_CODE, REDIRECT_URI, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No refresh token received");
    }
}
