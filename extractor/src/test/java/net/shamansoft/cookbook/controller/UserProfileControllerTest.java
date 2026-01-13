package net.shamansoft.cookbook.controller;

import com.google.cloud.Timestamp;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.dto.StorageType;
import net.shamansoft.cookbook.dto.UserProfileResponseDto;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for user profile endpoint in CookbookController.
 * Tests the refactored /v1/user/profile endpoint with new DTO structure.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final Instant TEST_CREATED_AT = Instant.parse("2024-01-15T10:30:00Z");
    @Mock
    private StorageService storageService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private RecipeService recipeService;
    @InjectMocks
    private UserProfileController controller;
    private Map<String, Object> mockProfileData;

    @BeforeEach
    void setUp() {
        // Setup mock profile data with createdAt timestamp
        mockProfileData = new HashMap<>();
        mockProfileData.put("userId", TEST_USER_ID);
        mockProfileData.put("email", TEST_EMAIL);

        // Convert Instant to Firestore Timestamp
        Timestamp firestoreTimestamp = Timestamp.ofTimeSecondsAndNanos(
                TEST_CREATED_AT.getEpochSecond(),
                TEST_CREATED_AT.getNano()
        );
        mockProfileData.put("createdAt", firestoreTimestamp);
    }

    /**
     * Test 3.1: Profile with storage configured
     */
    @Test
    void testGetUserProfile_WithStorageConfigured() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(true);

        StorageInfo mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .folderId("folder-123")
                .folderName("My Recipes")
                .connectedAt(Instant.parse("2024-01-20T15:45:00Z"))
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(storageService.getStorageInfo(TEST_USER_ID)).thenReturn(mockStorageInfo);

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
        assertThat(profile.storage().connectedAt()).isEqualTo(Instant.parse("2024-01-20T15:45:00Z"));
    }

    /**
     * Test 3.2: Profile without storage configured
     */
    @Test
    void testGetUserProfile_WithoutStorage() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(false);

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
    void testGetUserProfile_CreatedAtFormat() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(false);

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
    void testGetUserProfile_StorageObjectStructure() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(true);

        Instant storageConnectedAt = Instant.parse("2024-01-20T15:45:00Z");
        StorageInfo mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .folderId("folder-abc-123")
                .folderName("Test Folder Name")
                .connectedAt(storageConnectedAt)
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(storageService.getStorageInfo(TEST_USER_ID)).thenReturn(mockStorageInfo);

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
    void testGetUserProfile_StorageWithNullFolderFields() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(true);

        StorageInfo mockStorageInfo = StorageInfo.builder()
                .type(StorageType.GOOGLE_DRIVE)
                .connected(true)
                .folderId(null)  // Null folder ID
                .folderName(null)  // Null folder name
                .connectedAt(Instant.parse("2024-01-20T15:45:00Z"))
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(storageService.getStorageInfo(TEST_USER_ID)).thenReturn(mockStorageInfo);

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
    void testGetUserProfile_ProfileRetrievalFails() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenThrow(new RuntimeException("Firestore error"));

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(false);

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert - should fallback to current time
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getBody().email()).isEqualTo(TEST_EMAIL);
    }

    /**
     * Test error case: Storage retrieval fails
     */
    @Test
    void testGetUserProfile_StorageRetrievalFails() throws Exception {
        // Arrange
        when(userProfileService.getOrCreateProfile(TEST_USER_ID, TEST_EMAIL))
                .thenReturn(mockProfileData);

        when(storageService.isStorageConnected(TEST_USER_ID)).thenReturn(true);
        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenThrow(new RuntimeException("Storage error"));

        // Act
        ResponseEntity<UserProfileResponseDto> response = controller.getUserProfile(TEST_USER_ID, TEST_EMAIL);

        // Assert - should handle gracefully with null storage
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().storage()).isNull();
    }
}
