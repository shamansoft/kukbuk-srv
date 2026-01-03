package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.config.TestFirebaseConfig;
import net.shamansoft.cookbook.dto.StorageConnectionRequest;
import net.shamansoft.cookbook.dto.StorageConnectionResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageStatusResponse;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestFirebaseConfig.class)
class StorageControllerTest {

    @Autowired
    private StorageController controller;

    @MockitoBean
    private StorageService storageService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_AUTH_CODE = "auth-code-123";
    private static final String TEST_REDIRECT_URI = "https://example.com/callback";
    private static final String TEST_FOLDER_ID = "folder-123";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(storageService);
    }

    // ========== CONNECT TESTS ==========

    @Test
    @DisplayName("POST /connect - Success with all fields")
    void connectGoogleDrive_Success_AllFields() {
        // Given
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode(TEST_AUTH_CODE)
                .redirectUri(TEST_REDIRECT_URI)
                .defaultFolderId(TEST_FOLDER_ID)
                .build();

        // storageService.connectGoogleDrive is void, so no need to stub return value
        doNothing().when(storageService).connectGoogleDrive(
                eq(TEST_USER_ID),
                eq(TEST_AUTH_CODE),
                eq(TEST_REDIRECT_URI),
                eq(TEST_FOLDER_ID)
        );

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.connectGoogleDrive(TEST_USER_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("success");
        assertThat(response.getBody().getMessage()).isEqualTo("Google Drive connected successfully");
        assertThat(response.getBody().isConnected()).isTrue();
        assertThat(response.getBody().getTimestamp()).isNotNull();

        // Verify service was called with correct parameters
        verify(storageService).connectGoogleDrive(
                TEST_USER_ID,
                TEST_AUTH_CODE,
                TEST_REDIRECT_URI,
                TEST_FOLDER_ID
        );
    }

    @Test
    @DisplayName("POST /connect - Success without optional fields")
    void connectGoogleDrive_Success_MissingOptionalFields() {
        // Given - no folder ID
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode(TEST_AUTH_CODE)
                .redirectUri(TEST_REDIRECT_URI)
                .defaultFolderId(null)
                .build();

        doNothing().when(storageService).connectGoogleDrive(
                anyString(), anyString(), anyString(), isNull()
        );

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.connectGoogleDrive(TEST_USER_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isConnected()).isTrue();

        verify(storageService).connectGoogleDrive(
                TEST_USER_ID,
                TEST_AUTH_CODE,
                TEST_REDIRECT_URI,
                null   // folder ID
        );
    }

    @Test
    @DisplayName("POST /connect - Invalid authorization code returns 400")
    void connectGoogleDrive_InvalidAuthCode_Returns400() {
        // Given
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode("invalid-code")
                .redirectUri(TEST_REDIRECT_URI)
                .build();

        doThrow(new IllegalArgumentException("Invalid authorization code"))
                .when(storageService).connectGoogleDrive(
                        anyString(), anyString(), anyString(), any()
                );

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.connectGoogleDrive(TEST_USER_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
        assertThat(response.getBody().getMessage()).contains("Invalid authorization code");
    }

    @Test
    @DisplayName("POST /connect - Missing refresh token returns 400")
    void connectGoogleDrive_MissingRefreshToken_Returns400() {
        // Given
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode(TEST_AUTH_CODE)
                .redirectUri(TEST_REDIRECT_URI)
                .build();

        doThrow(new IllegalStateException("No refresh token received"))
                .when(storageService).connectGoogleDrive(
                        anyString(), anyString(), anyString(), any()
                );

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.connectGoogleDrive(TEST_USER_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
        assertThat(response.getBody().getMessage()).contains("OAuth error");
    }

    // ========== DISCONNECT TESTS ==========

    @Test
    @DisplayName("DELETE /disconnect - Success")
    void disconnectGoogleDrive_Success() {
        // Given
        doNothing().when(storageService).disconnectStorage(TEST_USER_ID);

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.disconnectGoogleDrive(TEST_USER_ID);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("success");
        assertThat(response.getBody().getMessage()).isEqualTo("Google Drive disconnected successfully");
        assertThat(response.getBody().isConnected()).isFalse();

        verify(storageService).disconnectStorage(TEST_USER_ID);
    }

    @Test
    @DisplayName("DELETE /disconnect - Idempotent (already disconnected)")
    void disconnectGoogleDrive_AlreadyDisconnected_Success() {
        // Given - service doesn't throw exception even if not connected
        doNothing().when(storageService).disconnectStorage(TEST_USER_ID);

        // When
        ResponseEntity<StorageConnectionResponse> response =
                controller.disconnectGoogleDrive(TEST_USER_ID);

        // Then - should still succeed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isConnected()).isFalse();
    }

    @Test
    @DisplayName("DELETE /disconnect - DatabaseUnavailableException throws 503")
    void disconnectGoogleDrive_DatabaseUnavailable_Throws503() {
        // Given
        doThrow(new DatabaseUnavailableException("Firestore timeout"))
                .when(storageService).disconnectStorage(TEST_USER_ID);

        // When/Then
        assertThatThrownBy(() ->
                controller.disconnectGoogleDrive(TEST_USER_ID)
        ).isInstanceOf(DatabaseUnavailableException.class);
    }

    // ========== STATUS TESTS ==========

    @Test
    @DisplayName("GET /status - Connected state returns full info")
    void getGoogleDriveStatus_Connected_ReturnsFullInfo() {
        // Given
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);

        StorageInfo storageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .accessToken("ya29.test_token")  // Won't be in response
                .refreshToken("1//test_refresh") // Won't be in response
                .expiresAt(expiresAt)
                .connectedAt(now)
                .defaultFolderId(TEST_FOLDER_ID)
                .build();

        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenReturn(storageInfo);

        // When
        ResponseEntity<StorageStatusResponse> response =
                controller.getGoogleDriveStatus(TEST_USER_ID);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isConnected()).isTrue();
        assertThat(response.getBody().getStorageType()).isEqualTo("googleDrive");
        assertThat(response.getBody().getConnectedAt()).isEqualTo(now);
        assertThat(response.getBody().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.getBody().getDefaultFolderId()).isEqualTo(TEST_FOLDER_ID);

        verify(storageService).getStorageInfo(TEST_USER_ID);
    }

    @Test
    @DisplayName("GET /status - Not connected returns connected=false")
    void getGoogleDriveStatus_NotConnected_ReturnsNotConnected() {
        // Given
        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenThrow(new StorageNotConnectedException("No storage configured"));

        // When
        ResponseEntity<StorageStatusResponse> response =
                controller.getGoogleDriveStatus(TEST_USER_ID);

        // Then - should NOT throw exception, should return connected=false
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isConnected()).isFalse();
        assertThat(response.getBody().getStorageType()).isNull();
        assertThat(response.getBody().getConnectedAt()).isNull();
        assertThat(response.getBody().getExpiresAt()).isNull();
        assertThat(response.getBody().getDefaultFolderId()).isNull();

        verify(storageService).getStorageInfo(TEST_USER_ID);
    }

    @Test
    @DisplayName("GET /status - UserNotFoundException throws 404")
    void getGoogleDriveStatus_UserNotFound_Throws404() {
        // Given
        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenThrow(new UserNotFoundException("User not found"));

        // When/Then - should propagate to exception handler
        assertThatThrownBy(() ->
                controller.getGoogleDriveStatus(TEST_USER_ID)
        ).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("GET /status - DatabaseUnavailableException throws 503")
    void getGoogleDriveStatus_DatabaseUnavailable_Throws503() {
        // Given
        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenThrow(new DatabaseUnavailableException("Firestore timeout"));

        // When/Then
        assertThatThrownBy(() ->
                controller.getGoogleDriveStatus(TEST_USER_ID)
        ).isInstanceOf(DatabaseUnavailableException.class);
    }
}
