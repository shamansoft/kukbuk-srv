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
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService Tests")
class StorageServiceTest {

    @Mock(lenient = true)
    private Firestore firestore;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock(lenient = true)
    private CollectionReference usersCollection;

    @Mock(lenient = true)
    private DocumentReference userDocument;

    @Mock(lenient = true)
    private ApiFuture<DocumentSnapshot> documentFuture;

    @Mock(lenient = true)
    private ApiFuture<WriteResult> writeFuture;

    @Mock(lenient = true)
    private DocumentSnapshot documentSnapshot;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private static final String FOLDER_NAME = "test-folder";

    private StorageService storageService;

    private static final String USER_ID = "test-user-123";
    private static final String ACCESS_TOKEN = "access-token-123";
    private static final String REFRESH_TOKEN = "refresh-token-456";
    private static final String ENCRYPTED_ACCESS = "encrypted-access";
    private static final String ENCRYPTED_REFRESH = "encrypted-refresh";
    private static final long EXPIRES_IN = 3600L;
    private static final String FOLDER_ID = "folder-123";
    @Mock
    private net.shamansoft.cookbook.client.GoogleDrive googleDrive;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        storageService = new StorageService(firestore, tokenEncryptionService, webClientBuilder, googleDrive);
        ReflectionTestUtils.setField(storageService, "googleClientId", "test-client-id");
        ReflectionTestUtils.setField(storageService, "googleClientSecret", "test-client-secret");
        ReflectionTestUtils.setField(storageService, "defaultFolderName", "kukbuk");
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocument);
    }

    // ====================== connectGoogleDrive Tests ======================

    @Test
    @DisplayName("Should connect Google Drive with valid authorization code")
    void shouldConnectGoogleDriveSuccessfully() throws Exception {
        // Given
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("refresh_token", REFRESH_TOKEN);
        oauthResponse.put("expires_in", (int) EXPIRES_IN);

        setupWebClientMock(oauthResponse);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);

        // Mock Google Drive folder creation
        net.shamansoft.cookbook.client.GoogleDrive.Item folderItem =
                new net.shamansoft.cookbook.client.GoogleDrive.Item(FOLDER_ID, FOLDER_NAME);
        when(googleDrive.getFolder(eq(FOLDER_NAME), eq(ACCESS_TOKEN))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq(FOLDER_NAME), eq(ACCESS_TOKEN))).thenReturn(folderItem);

        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        StorageService.FolderInfo result = storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", FOLDER_NAME);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.folderId()).isEqualTo(FOLDER_ID);
        assertThat(result.folderName()).isEqualTo(FOLDER_NAME);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userDocument).update(eq("storage"), captor.capture());

        Map<String, Object> storage = captor.getValue();
        assertThat(storage.get("connected")).isEqualTo(true);
        assertThat(storage.get("accessToken")).isEqualTo(ENCRYPTED_ACCESS);
        assertThat(storage.get("refreshToken")).isEqualTo(ENCRYPTED_REFRESH);
        assertThat(storage.get("folderId")).isEqualTo(FOLDER_ID);
        assertThat(storage.get("folderName")).isEqualTo(FOLDER_NAME);
    }

    @Test
    @DisplayName("Should handle invalid authorization code")
    void shouldThrowExceptionForInvalidAuthCode() {
        // Given
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_grant");

        setupWebClientMock(errorResponse);

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "invalid-code", "https://callback", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid response from Google OAuth");
    }

    @Test
    @DisplayName("Should throw exception when refresh token is missing")
    void shouldThrowExceptionWhenRefreshTokenMissing() {
        // Given
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("expires_in", 3600);

        setupWebClientMock(oauthResponse);

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No refresh token received");
    }

    @Test
    @DisplayName("Should return null response from OAuth")
    void shouldHandleNullOAuthResponse() {
        // Given
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.empty());

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid response from Google OAuth");
    }

    @Test
    @DisplayName("Should use default expiration when not provided")
    void shouldUseDefaultExpiration() throws Exception {
        // Given
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("refresh_token", REFRESH_TOKEN);

        setupWebClientMock(oauthResponse);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);

        // Mock Google Drive folder creation (use default folder name "kukbuk")
        net.shamansoft.cookbook.client.GoogleDrive.Item folderItem =
                new net.shamansoft.cookbook.client.GoogleDrive.Item(FOLDER_ID, "kukbuk");
        when(googleDrive.getFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(folderItem);

        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null);

        // Then
        verify(userDocument).update(eq("storage"), any(Map.class));
    }

    @Test
    @DisplayName("Should handle WebClientResponseException during OAuth exchange")
    void shouldHandleWebClientResponseException() {
        // Given
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenThrow(WebClientResponseException.create(400, "Bad Request", null, "{\"error\":\"invalid_grant\"}".getBytes(), null));

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth token exchange failed");
    }

    @Test
    @DisplayName("Should handle InterruptedException during token storage")
    void shouldHandleInterruptedExceptionDuringStorage() throws Exception {
        // Given
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("refresh_token", REFRESH_TOKEN);
        oauthResponse.put("expires_in", 3600);

        setupWebClientMock(oauthResponse);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);

        // Mock Google Drive folder creation
        net.shamansoft.cookbook.client.GoogleDrive.Item folderItem =
                new net.shamansoft.cookbook.client.GoogleDrive.Item(FOLDER_ID, "kukbuk");
        when(googleDrive.getFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(folderItem);

        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new InterruptedException());

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("operation interrupted");
    }

    @Test
    @DisplayName("Should handle ExecutionException during token storage")
    void shouldHandleExecutionExceptionDuringStorage() throws Exception {
        // Given
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", ACCESS_TOKEN);
        oauthResponse.put("refresh_token", REFRESH_TOKEN);
        oauthResponse.put("expires_in", 3600);

        setupWebClientMock(oauthResponse);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);

        // Mock Google Drive folder creation
        net.shamansoft.cookbook.client.GoogleDrive.Item folderItem =
                new net.shamansoft.cookbook.client.GoogleDrive.Item(FOLDER_ID, "kukbuk");
        when(googleDrive.getFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(java.util.Optional.empty());
        when(googleDrive.createFolder(eq("kukbuk"), eq(ACCESS_TOKEN))).thenReturn(folderItem);

        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new ExecutionException(new RuntimeException("Database error")));

        // When & Then
        assertThatThrownBy(() -> storageService.connectGoogleDrive(USER_ID, "auth-code", "https://callback", null))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("Failed to connect Google Drive");
    }

    // ====================== getStorageInfo Tests ======================

    @Test
    @DisplayName("Should return storage info with valid token")
    void shouldReturnStorageInfoWithValidToken() throws Exception {
        // Given
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken(ENCRYPTED_ACCESS)
                .refreshToken(ENCRYPTED_REFRESH)
                .expiresAt(Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0))
                .connectedAt(Timestamp.now())
                .folderId(FOLDER_ID)
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

        // When
        StorageInfo result = storageService.getStorageInfo(USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(StorageType.GOOGLE_DRIVE);
        assertThat(result.connected()).isTrue();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.defaultFolderId()).isEqualTo(FOLDER_ID);
    }

    @Test
    @DisplayName("Should refresh expired token")
    void shouldRefreshExpiredToken() throws Exception {
        // Given - Token expired
        long pastTimestamp = System.currentTimeMillis() / 1000 - 3600;
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken(ENCRYPTED_ACCESS)
                .refreshToken(ENCRYPTED_REFRESH)
                .expiresAt(Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0))
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
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH)).thenReturn(REFRESH_TOKEN);

        String newAccessToken = "new-access-token";
        Map<String, Object> refreshResponse = new HashMap<>();
        refreshResponse.put("access_token", newAccessToken);
        refreshResponse.put("expires_in", 3600);

        setupWebClientMock(refreshResponse);
        when(tokenEncryptionService.encrypt(newAccessToken)).thenReturn("encrypted-new-token");
        when(userDocument.update(anyString(), any(), anyString(), any())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        StorageInfo result = storageService.getStorageInfo(USER_ID);

        // Then
        assertThat(result.accessToken()).isEqualTo(newAccessToken);
        verify(userDocument).update(eq("storage.accessToken"), any(), eq("storage.expiresAt"), any(Timestamp.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() throws Exception {
        // Given
        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No user profile found");
    }

    @Test
    @DisplayName("Should throw exception when storage is null")
    void shouldThrowExceptionWhenStorageNull() throws Exception {
        // Given
        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(null)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("No storage configuration found");
    }

    @Test
    @DisplayName("Should throw exception when storage not connected")
    void shouldThrowExceptionWhenStorageNotConnected() throws Exception {
        // Given
        StorageEntity storageEntity = StorageEntity.builder()
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

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("Should throw exception when access token is null")
    void shouldThrowExceptionWhenAccessTokenNull() throws Exception {
        // Given
        StorageEntity storageEntity = StorageEntity.builder()
                .connected(true)
                .accessToken(null)
                .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("no access token found");
    }

    @Test
    @DisplayName("Should throw exception when token refresh fails")
    void shouldThrowExceptionWhenTokenRefreshFails() throws Exception {
        // Given - Token expired, no refresh token available
        long pastTimestamp = System.currentTimeMillis() / 1000 - 3600;
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken(ENCRYPTED_ACCESS)
                .refreshToken(null)  // No refresh token
                .expiresAt(Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0))
                .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // When & Then - should fail because no refresh token is available
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("no refresh token available");
    }

    @Test
    @DisplayName("Should throw exception when no refresh token during refresh")
    void shouldThrowExceptionWhenNoRefreshTokenDuringRefresh() throws Exception {
        // Given - Token expired, no refresh token
        long pastTimestamp = System.currentTimeMillis() / 1000 - 3600;
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken(ENCRYPTED_ACCESS)
                .refreshToken(null)
                .expiresAt(Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0))
                .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(StorageNotConnectedException.class)
                .hasMessageContaining("no refresh token available");
    }

    @Test
    @DisplayName("Should handle null expiresAt timestamp")
    void shouldHandleNullExpiresAt() throws Exception {
        // Given - Token with null expiration
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken(ENCRYPTED_ACCESS)
                .refreshToken(ENCRYPTED_REFRESH)
                .expiresAt(null)
                .build();

        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(storageEntity)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH)).thenReturn(REFRESH_TOKEN);

        String newAccessToken = "new-access-token";
        Map<String, Object> refreshResponse = new HashMap<>();
        refreshResponse.put("access_token", newAccessToken);
        refreshResponse.put("expires_in", 3600);

        setupWebClientMock(refreshResponse);
        when(tokenEncryptionService.encrypt(newAccessToken)).thenReturn("encrypted-new-token");
        when(userDocument.update(anyString(), any(), anyString(), any())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        StorageInfo result = storageService.getStorageInfo(USER_ID);

        // Then
        assertThat(result).isNotNull();
        verify(userDocument).update(eq("storage.accessToken"), any(), eq("storage.expiresAt"), any(Timestamp.class));
    }

    @Test
    @DisplayName("Should handle InterruptedException during getStorageInfo")
    void shouldHandleInterruptedExceptionDuringGetStorageInfo() throws Exception {
        // Given
        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenThrow(new InterruptedException());

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("operation interrupted");
    }

    @Test
    @DisplayName("Should handle ExecutionException during getStorageInfo")
    void shouldHandleExecutionExceptionDuringGetStorageInfo() throws Exception {
        // Given
        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenThrow(new ExecutionException(new RuntimeException("Database error")));

        // When & Then
        assertThatThrownBy(() -> storageService.getStorageInfo(USER_ID))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("Failed to fetch user profile");
    }

    // ====================== isStorageConnected Tests ======================

    @Test
    @DisplayName("Should return true when storage is connected")
    void shouldReturnTrueWhenStorageConnected() throws Exception {
        // Given
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        StorageEntity storageEntity = StorageEntity.builder()
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

        // When
        boolean result = storageService.isStorageConnected(USER_ID);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when storage is not connected")
    void shouldReturnFalseWhenStorageNotConnected() throws Exception {
        // Given
        UserProfile userProfile = UserProfile.builder()
                .userId(USER_ID)
                .storage(null)
                .build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(UserProfile.class)).thenReturn(userProfile);

        // When
        boolean result = storageService.isStorageConnected(USER_ID);

        // Then
        assertThat(result).isFalse();
    }

    // ====================== disconnectStorage Tests ======================

    @Test
    @DisplayName("Should disconnect storage successfully")
    void shouldDisconnectStorageSuccessfully() throws Exception {
        // Given
        when(userDocument.update("storage", null)).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        storageService.disconnectStorage(USER_ID);

        // Then
        verify(userDocument).update("storage", null);
    }

    @Test
    @DisplayName("Should handle InterruptedException during disconnect")
    void shouldHandleInterruptedExceptionDuringDisconnect() throws Exception {
        // Given
        when(userDocument.update("storage", null)).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new InterruptedException());

        // When & Then
        assertThatThrownBy(() -> storageService.disconnectStorage(USER_ID))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("operation interrupted");
    }

    @Test
    @DisplayName("Should handle ExecutionException during disconnect")
    void shouldHandleExecutionExceptionDuringDisconnect() throws Exception {
        // Given
        when(userDocument.update("storage", null)).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new ExecutionException(new RuntimeException("Database error")));

        // When & Then
        assertThatThrownBy(() -> storageService.disconnectStorage(USER_ID))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("Failed to disconnect storage");
    }

    // ====================== updateDefaultFolder Tests ======================

    @Test
    @DisplayName("Should update default folder successfully")
    void shouldUpdateDefaultFolderSuccessfully() throws Exception {
        // Given
        String newFolderId = "new-folder-456";
        when(userDocument.update("storage.folderId", newFolderId)).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        storageService.updateDefaultFolder(USER_ID, newFolderId);

        // Then
        verify(userDocument).update("storage.folderId", newFolderId);
    }

    @Test
    @DisplayName("Should handle InterruptedException during folder update")
    void shouldHandleInterruptedExceptionDuringFolderUpdate() throws Exception {
        // Given
        String newFolderId = "new-folder-456";
        when(userDocument.update("storage.folderId", newFolderId)).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new InterruptedException());

        // When & Then
        assertThatThrownBy(() -> storageService.updateDefaultFolder(USER_ID, newFolderId))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("operation interrupted");
    }

    @Test
    @DisplayName("Should handle ExecutionException during folder update")
    void shouldHandleExecutionExceptionDuringFolderUpdate() throws Exception {
        // Given
        String newFolderId = "new-folder-456";
        when(userDocument.update("storage.folderId", newFolderId)).thenReturn(writeFuture);
        when(writeFuture.get()).thenThrow(new ExecutionException(new RuntimeException("Database error")));

        // When & Then
        assertThatThrownBy(() -> storageService.updateDefaultFolder(USER_ID, newFolderId))
                .isInstanceOf(DatabaseUnavailableException.class)
                .hasMessageContaining("Failed to update default folder");
    }

    // ====================== Helper Methods ======================

    private void setupWebClientMock(Map<String, Object> response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(response));
    }
}