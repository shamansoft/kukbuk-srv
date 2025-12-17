package net.shamansoft.cookbook.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageServiceTest {

    @Mock
    private Firestore firestore;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private CollectionReference usersCollection;

    @Mock
    private DocumentReference userDocument;

    @Mock
    private ApiFuture<DocumentSnapshot> documentFuture;

    @Mock
    private ApiFuture<WriteResult> writeFuture;

    @Mock
    private DocumentSnapshot documentSnapshot;

    private StorageService storageService;

    private static final String USER_ID = "test-user-123";
    private static final String ACCESS_TOKEN = "access-token-123";
    private static final String REFRESH_TOKEN = "refresh-token-456";
    private static final String ENCRYPTED_ACCESS = "encrypted-access";
    private static final String ENCRYPTED_REFRESH = "encrypted-refresh";
    private static final long EXPIRES_IN = 3600L;
    private static final String FOLDER_ID = "folder-123";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storageService = new StorageService(firestore, tokenEncryptionService);

        // Default Firestore mock setup
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocument);
    }

    @Test
    void connectGoogleDrive_storesEncryptedTokens() throws Exception {
        // Arrange
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);
        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // Act
        storageService.connectGoogleDrive(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_IN, FOLDER_ID);

        // Assert
        ArgumentCaptor<Map<String, Object>> storageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userDocument).update(eq("storage"), storageCaptor.capture());

        Map<String, Object> storage = storageCaptor.getValue();
        assertThat(storage.get("type")).isEqualTo("googleDrive");
        assertThat(storage.get("connected")).isEqualTo(true);
        assertThat(storage.get("accessToken")).isEqualTo(ENCRYPTED_ACCESS);
        assertThat(storage.get("refreshToken")).isEqualTo(ENCRYPTED_REFRESH);
        assertThat(storage.get("defaultFolderId")).isEqualTo(FOLDER_ID);
        assertThat(storage.get("expiresAt")).isInstanceOf(Timestamp.class);
        assertThat(storage.get("connectedAt")).isInstanceOf(Timestamp.class);
    }

    @Test
    void connectGoogleDrive_withNullRefreshToken_omitsRefreshToken() throws Exception {
        // Arrange
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // Act
        storageService.connectGoogleDrive(USER_ID, ACCESS_TOKEN, null, EXPIRES_IN, null);

        // Assert
        ArgumentCaptor<Map<String, Object>> storageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userDocument).update(eq("storage"), storageCaptor.capture());

        Map<String, Object> storage = storageCaptor.getValue();
        assertThat(storage.get("accessToken")).isEqualTo(ENCRYPTED_ACCESS);
        assertThat(storage).doesNotContainKey("refreshToken");
        assertThat(storage).doesNotContainKey("defaultFolderId");
    }

    @Test
    void getStorageInfo_returnsDecryptedTokens() throws Exception {
        // Arrange
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .refreshToken(ENCRYPTED_REFRESH)
                        .expiresAt(Timestamp.now())
                        .connectedAt(Timestamp.now())
                        .defaultFolderId(FOLDER_ID)
                        .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS)).thenReturn(ACCESS_TOKEN);
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH)).thenReturn(REFRESH_TOKEN);

        // Act
        StorageInfo result = storageService.getStorageInfo(USER_ID);

        // Assert
        assertThat(result.type()).isEqualTo(StorageType.GOOGLE_DRIVE);
        assertThat(result.connected()).isTrue();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.defaultFolderId()).isEqualTo(FOLDER_ID);
        assertThat(result.expiresAt()).isNotNull();
        assertThat(result.connectedAt()).isNotNull();
    }

    @Test
    void getStorageInfo_withNullRefreshToken_returnsNullRefreshToken() throws Exception {
        // Arrange
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .expiresAt(Timestamp.now())
                        .connectedAt(Timestamp.now())
                        .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS)).thenReturn(ACCESS_TOKEN);

        // Act
        StorageInfo result = storageService.getStorageInfo(USER_ID);

        // Assert
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isNull();
        assertThat(result.defaultFolderId()).isNull();
    }

    @Test
    void getStorageInfo_whenUserNotFound_throwsException() throws Exception {
        // Arrange
        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No user profile found");
    }

    @Test
    void getStorageInfo_whenStorageNull_throwsException() throws Exception {
        // Arrange
        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(null)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // Act & Assert
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configuration found");
    }

    @Test
    void getStorageInfo_whenNotConnected_throwsException() throws Exception {
        // Arrange
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .connected(false)
                        .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // Act & Assert
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void getStorageInfo_whenNoAccessToken_throwsException() throws Exception {
        // Arrange
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .connected(true)
                        .accessToken(null)  // accessToken is null
                        .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // Act & Assert
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("no access token found");
    }

    @Test
    void isStorageConnected_returnsTrueWhenConnected() throws Exception {
        // Arrange
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS)).thenReturn(ACCESS_TOKEN);

        // Act
        boolean result = storageService.isStorageConnected(USER_ID);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isStorageConnected_returnsFalseWhenNotConnected() throws Exception {
        // Arrange
        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(null)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // Act
        boolean result = storageService.isStorageConnected(USER_ID);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void disconnectStorage_removesStorageObject() throws Exception {
        // Arrange
        when(userDocument.update("storage", null)).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // Act
        storageService.disconnectStorage(USER_ID);

        // Assert
        verify(userDocument).update("storage", null);
    }

    @Test
    void updateDefaultFolder_updatesFieldInFirestore() throws Exception {
        // Arrange
        String newFolderId = "new-folder-456";
        when(userDocument.update("storage.defaultFolderId", newFolderId)).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // Act
        storageService.updateDefaultFolder(USER_ID, newFolderId);

        // Assert
        verify(userDocument).update("storage.defaultFolderId", newFolderId);
    }
}
