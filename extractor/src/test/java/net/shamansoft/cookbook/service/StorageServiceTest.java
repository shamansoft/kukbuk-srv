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
import static org.mockito.ArgumentMatchers.anyString;
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
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Mock
    private org.springframework.web.reactive.function.client.WebClient webClient;

    private static final String AUTH_CODE = "auth-code-789";
    private static final String REDIRECT_URI = "https://example.com/callback";
    @Mock
    private org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private org.springframework.web.reactive.function.client.WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec requestHeadersSpec;

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
    @Mock
    private org.springframework.web.reactive.function.client.WebClient.ResponseSpec responseSpec;
    @Mock
    private reactor.core.publisher.Mono<java.util.Map> monoMap;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock WebClient.Builder to return WebClient
        when(webClientBuilder.build()).thenReturn(webClient);

        storageService = new StorageService(firestore, tokenEncryptionService, webClientBuilder);

        // Default Firestore mock setup
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocument);
    }

    @Test
    void connectGoogleDrive_exchangesCodeAndStoresEncryptedTokens() throws Exception {
        // Arrange
        // Mock OAuth token exchange response
        Map<String, Object> oauthResponse = new java.util.HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("refresh_token", REFRESH_TOKEN);
        oauthResponse.put("expires_in", (int) EXPIRES_IN);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(monoMap);
        when(monoMap.block()).thenReturn(oauthResponse);

        // Mock token encryption
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);
        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // Act
        storageService.connectGoogleDrive(USER_ID, AUTH_CODE, REDIRECT_URI, FOLDER_ID);

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
    void connectGoogleDrive_invalidAuthCode_throwsException() throws Exception {
        // Arrange
        // Mock OAuth error response
        Map<String, Object> errorResponse = new java.util.HashMap<>();
        errorResponse.put("error", "invalid_grant");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(monoMap);
        when(monoMap.block()).thenReturn(errorResponse);

        // Act & Assert
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "invalid-code", REDIRECT_URI, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid response from Google OAuth");
    }

    @Test
    void connectGoogleDrive_missingRefreshToken_throwsException() throws Exception {
        // Arrange
        // Mock OAuth response without refresh token
        Map<String, Object> oauthResponse = new java.util.HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("expires_in", (int) EXPIRES_IN);
        // No refresh_token

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(monoMap);
        when(monoMap.block()).thenReturn(oauthResponse);

        // Act & Assert
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, AUTH_CODE, REDIRECT_URI, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No refresh token received");
    }

    @Test
    void getStorageInfo_returnsDecryptedTokens() throws Exception {
        // Arrange
        // Set expiration to 1 hour in the future (token is valid)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .refreshToken(ENCRYPTED_REFRESH)
                        .expiresAt(Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0))
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
        // Set expiration to 1 hour in the future (token is valid)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .expiresAt(Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0))
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
        // Set expiration to 1 hour in the future (token is valid)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        StorageEntity storageEntity =
                StorageEntity.builder()
                        .type("googleDrive")
                        .connected(true)
                        .accessToken(ENCRYPTED_ACCESS)
                        .expiresAt(Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0))
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
