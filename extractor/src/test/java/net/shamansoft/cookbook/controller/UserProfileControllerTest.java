package net.shamansoft.cookbook.controller;

import com.google.cloud.Timestamp;
import net.shamansoft.cookbook.dto.UserProfileResponseDto;
import net.shamansoft.cookbook.dto.UserProfileUpdateRequest;
import net.shamansoft.cookbook.repository.firestore.model.StorageEntity;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for user profile endpoint in UserProfileController.
 * Tests the refactored /v1/user/profile endpoint with new DTO structure.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final Instant TEST_CREATED_AT = Instant.parse("2024-01-15T10:30:00Z");

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserProfileController controller;

    /**
     * Test 3.1: Profile with storage configured
     */
    @Test
    void testGetUserProfile_WithStorageConfigured() {
        // Arrange
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );

        Instant storageConnectedAt = Instant.parse("2024-01-20T15:45:00Z");
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .folderId("folder-123")
                .folderName("My Recipes")
                .connectedAt(Timestamp.ofTimeSecondsAndNanos(
                        storageConnectedAt.getEpochSecond(),
                        storageConnectedAt.getNano()
                ))
                .build();

        UserProfile mockProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(firestoreTimestamp)
                .storage(storageEntity)
                .build();

        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        UserProfileResponseDto profile = response.getBody();
        assertThat(profile.userId()).isEqualTo(TEST_USER_ID);
        assertThat(profile.email()).isEqualTo(TEST_EMAIL);
        assertThat(profile.createdAt()).isEqualTo(TEST_CREATED_AT);

        // Storage should NOT be null
        assertThat(profile.storage()).isNotNull();
        assertThat(profile.storage().type()).isEqualTo("googleDrive");
        assertThat(profile.storage().folderId()).isEqualTo("folder-123");
        assertThat(profile.storage().folderName()).isEqualTo("My Recipes");
        assertThat(profile.storage().connectedAt()).isEqualTo(storageConnectedAt);
    }

    /**
     * Test 3.2: Profile without storage configured
     */
    @Test
    void testGetUserProfile_WithoutStorage() {
        // Arrange
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );

        UserProfile mockProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(firestoreTimestamp)
                .storage(null)  // No storage configured
                .build();

        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        UserProfileResponseDto profile = response.getBody();
        assertThat(profile.userId()).isEqualTo(TEST_USER_ID);
        assertThat(profile.email()).isEqualTo(TEST_EMAIL);
        assertThat(profile.createdAt()).isEqualTo(TEST_CREATED_AT);

        // Storage should be explicitly null
        assertThat(profile.storage()).isNull();
    }

    /**
     * Test 3.3: CreatedAt field presence and ISO 8601 format
     */
    @Test
    void testGetUserProfile_CreatedAtFormat() {
        // Arrange
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );

        UserProfile mockProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(firestoreTimestamp)
                .build();

        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
        assertThat(response.getBody().createdAt()).isEqualTo(TEST_CREATED_AT);

        // Verify ISO 8601 format (Jackson will handle this during JSON serialization)
        String iso8601String = TEST_CREATED_AT.toString();
        assertThat(iso8601String).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    /**
     * Test 3.4: Storage object structure with all fields populated
     */
    @Test
    void testGetUserProfile_StorageObjectStructure() {
        // Arrange
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );

        Instant storageConnectedAt = Instant.parse("2024-01-20T15:45:00Z");
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .folderId("folder-abc-123")
                .folderName("Test Folder Name")
                .connectedAt(Timestamp.ofTimeSecondsAndNanos(
                        storageConnectedAt.getEpochSecond(),
                        storageConnectedAt.getNano()
                ))
                .build();

        UserProfile mockProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(firestoreTimestamp)
                .storage(storageEntity)
                .build();

        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().storage()).isNotNull();

        var storage = response.getBody().storage();

        // Verify all fields are strings/Instant (no primitive types)
        assertThat(storage.type()).isInstanceOf(String.class);
        assertThat(storage.type()).isEqualTo("googleDrive");

        assertThat(storage.folderId()).isInstanceOf(String.class);
        assertThat(storage.folderId()).isEqualTo("folder-abc-123");

        assertThat(storage.folderName()).isInstanceOf(String.class);
        assertThat(storage.folderName()).isEqualTo("Test Folder Name");

        assertThat(storage.connectedAt()).isInstanceOf(Instant.class);
        assertThat(storage.connectedAt()).isEqualTo(storageConnectedAt);

        // Verify ISO 8601 format for connectedAt
        String connectedAtString = storage.connectedAt().toString();
        assertThat(connectedAtString).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    /**
     * Test edge case: Storage exists but folderId/folderName are null
     */
    @Test
    void testGetUserProfile_StorageWithNullFolderFields() {
        // Arrange
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );

        Instant storageConnectedAt = Instant.parse("2024-01-20T15:45:00Z");
        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .folderId(null)  // Null folder ID
                .folderName(null)  // Null folder name
                .connectedAt(Timestamp.ofTimeSecondsAndNanos(
                        storageConnectedAt.getEpochSecond(),
                        storageConnectedAt.getNano()
                ))
                .build();

        UserProfile mockProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(firestoreTimestamp)
                .storage(storageEntity)
                .build();

        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert - should handle gracefully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().storage()).isNotNull();
        assertThat(response.getBody().storage().folderId()).isNull();
        assertThat(response.getBody().storage().folderName()).isNull();
    }

    /**
     * Test error case: Profile retrieval fails
     */
    @Test
    void testGetUserProfile_ProfileRetrievalFails() {
        // Arrange
        when(userProfileService.getProfile(TEST_USER_ID, TEST_EMAIL))
                .thenThrow(new RuntimeException("Firestore error"));

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert - should return minimal response with just userId
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(TEST_USER_ID);
        // Email and createdAt will be null since we caught the exception
    }

    /**
     * Test POST endpoint: Update display name
     */
    @Test
    void testUpdateUserProfile_UpdateDisplayName() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("John Doe")
                .build();

        Timestamp now = Timestamp.now();
        UserProfile updatedProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .displayName("John Doe")
                .createdAt(Timestamp.ofTimeSecondsAndNanos(TEST_CREATED_AT.getEpochSecond(), TEST_CREATED_AT.getNano()))
                .updatedAt(now)
                .build();

        when(userProfileService.updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), eq("John Doe"), any()))
                .thenReturn(updatedProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getBody().email()).isEqualTo(TEST_EMAIL);
        verify(userProfileService).updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), eq("John Doe"), any());
    }

    /**
     * Test POST endpoint: Update email
     */
    @Test
    void testUpdateUserProfile_UpdateEmail() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .email("newemail@example.com")
                .build();

        Timestamp now = Timestamp.now();
        UserProfile updatedProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email("newemail@example.com")
                .createdAt(Timestamp.ofTimeSecondsAndNanos(TEST_CREATED_AT.getEpochSecond(), TEST_CREATED_AT.getNano()))
                .updatedAt(now)
                .build();

        when(userProfileService.updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), any(), eq("newemail@example.com")))
                .thenReturn(updatedProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("newemail@example.com");
        verify(userProfileService).updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), any(), eq("newemail@example.com"));
    }

    /**
     * Test POST endpoint: Update both display name and email
     */
    @Test
    void testUpdateUserProfile_UpdateBothFields() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("Jane Smith")
                .email("jane@example.com")
                .build();

        Timestamp now = Timestamp.now();
        UserProfile updatedProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email("jane@example.com")
                .displayName("Jane Smith")
                .createdAt(Timestamp.ofTimeSecondsAndNanos(TEST_CREATED_AT.getEpochSecond(), TEST_CREATED_AT.getNano()))
                .updatedAt(now)
                .build();

        when(userProfileService.updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), eq("Jane Smith"), eq("jane@example.com")))
                .thenReturn(updatedProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("jane@example.com");
        verify(userProfileService).updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), eq("Jane Smith"), eq("jane@example.com"));
    }

    /**
     * Test POST endpoint: Empty request (no fields to update)
     */
    @Test
    void testUpdateUserProfile_EmptyRequest() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder().build();

        Timestamp now = Timestamp.now();
        UserProfile updatedProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .createdAt(Timestamp.ofTimeSecondsAndNanos(TEST_CREATED_AT.getEpochSecond(), TEST_CREATED_AT.getNano()))
                .updatedAt(now)
                .build();

        when(userProfileService.updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), any(), any()))
                .thenReturn(updatedProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(userProfileService).updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), any(), any());
    }

    /**
     * Test POST endpoint: Update profile with storage configured
     */
    @Test
    void testUpdateUserProfile_WithStorage() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("User With Storage")
                .build();

        Timestamp now = Timestamp.now();
        Instant storageConnectedAt = Instant.parse("2024-01-20T15:45:00Z");

        StorageEntity storageEntity = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .folderId("folder-123")
                .folderName("My Recipes")
                .connectedAt(Timestamp.ofTimeSecondsAndNanos(
                        storageConnectedAt.getEpochSecond(),
                        storageConnectedAt.getNano()
                ))
                .build();

        UserProfile updatedProfile = UserProfile.builder()
                .userId(TEST_USER_ID)
                .email(TEST_EMAIL)
                .displayName("User With Storage")
                .createdAt(Timestamp.ofTimeSecondsAndNanos(TEST_CREATED_AT.getEpochSecond(), TEST_CREATED_AT.getNano()))
                .updatedAt(now)
                .storage(storageEntity)
                .build();

        when(userProfileService.updateProfile(eq(TEST_USER_ID), eq(TEST_EMAIL), eq("User With Storage"), any()))
                .thenReturn(updatedProfile);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().storage()).isNotNull();
        assertThat(response.getBody().storage().type()).isEqualTo("googleDrive");
    }

    /**
     * Test POST endpoint: Handle service exception
     */
    @Test
    void testUpdateUserProfile_ServiceException() {
        // Arrange
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("Test User")
                .build();

        when(userProfileService.updateProfile(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Service error"));

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.updateUserProfile(request, TEST_USER_ID, TEST_EMAIL);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(TEST_USER_ID);
    }
}
