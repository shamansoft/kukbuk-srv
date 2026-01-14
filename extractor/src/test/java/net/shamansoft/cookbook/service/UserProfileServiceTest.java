package net.shamansoft.cookbook.service;

import com.google.cloud.Timestamp;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import net.shamansoft.cookbook.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Tests")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private UserProfileService userProfileService;

    private static final String USER_ID = "test-user-123";
    private static final String ACCESS_TOKEN = "access-token-123";
    private static final String REFRESH_TOKEN = "refresh-token-456";
    private static final String ENCRYPTED_ACCESS_TOKEN = "encrypted-access-123";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-456";
    private static final long EXPIRES_IN = 3600L;
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository, tokenEncryptionService, webClient);
    }

    @Test
    @DisplayName("Should store OAuth tokens for existing user profile")
    void shouldStoreOAuthTokensForExistingUser() throws Exception {
        // Given
        UserProfile existingProfile = UserProfile.builder()
                .userId(USER_ID)
                .email(USER_EMAIL)
                .createdAt(Timestamp.now())
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingProfile)));
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userProfileRepository.update(eq(USER_ID), any(Map.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        userProfileService.storeOAuthTokens(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_IN);

        // Then
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userProfileRepository).update(eq(USER_ID), updateCaptor.capture());

        Map<String, Object> updates = updateCaptor.getValue();
        assertThat(updates.get("googleOAuthToken")).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(updates.get("googleRefreshToken")).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(updates.get("tokenExpiresAt")).isInstanceOf(Timestamp.class);
        assertThat(updates.get("updatedAt")).isInstanceOf(Timestamp.class);
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Should create new user profile when storing OAuth tokens")
    void shouldCreateNewUserProfileWhenStoringOAuthTokens() throws Exception {
        // Given
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        userProfileService.storeOAuthTokens(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_IN);

        // Then
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.userId()).isEqualTo(USER_ID);
        assertThat(savedProfile.googleOAuthToken()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(savedProfile.googleRefreshToken()).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(savedProfile.tokenExpiresAt()).isInstanceOf(Timestamp.class);
        assertThat(savedProfile.createdAt()).isInstanceOf(Timestamp.class);
        assertThat(savedProfile.updatedAt()).isInstanceOf(Timestamp.class);
        verify(userProfileRepository, never()).update(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Should get valid OAuth token when not expired")
    void shouldGetValidOAuthTokenWhenNotExpired() throws Exception {
        // Given - token expires in 1 hour (way beyond the 5-minute buffer)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0);

        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .tokenExpiresAt(expiresAt)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS_TOKEN)).thenReturn(ACCESS_TOKEN);

        // When
        String result = userProfileService.getValidOAuthToken(USER_ID);

        // Then
        assertThat(result).isEqualTo(ACCESS_TOKEN);
        verify(tokenEncryptionService).decrypt(ENCRYPTED_ACCESS_TOKEN);
        verify(webClient, never()).post(); // No refresh should occur
    }

    @Test
    @DisplayName("Should refresh OAuth token when expired")
    void shouldRefreshOAuthTokenWhenExpired() throws Exception {
        // Given - token expires in 2 minutes (within 5-minute buffer)
        long nearFutureTimestamp = System.currentTimeMillis() / 1000 + 120;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(nearFutureTimestamp, 0);

        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .googleRefreshToken(ENCRYPTED_REFRESH_TOKEN)
                .tokenExpiresAt(expiresAt)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN);
        when(tokenEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_ACCESS_TOKEN, ENCRYPTED_REFRESH_TOKEN);

        // Mock OAuth refresh response
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", "new-access-token");
        oauthResponse.put("expires_in", 3600);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(oauthResponse));

        when(userProfileRepository.update(eq(USER_ID), any(Map.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        String result = userProfileService.getValidOAuthToken(USER_ID);

        // Then
        assertThat(result).isEqualTo("new-access-token");
        verify(webClient).post();
        verify(userProfileRepository).update(eq(USER_ID), any(Map.class));
    }

    @Test
    @DisplayName("Should throw exception when user profile not found")
    void shouldThrowExceptionWhenUserProfileNotFound() {
        // Given
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User profile not found");
    }

    @Test
    @DisplayName("Should throw exception when OAuth token missing")
    void shouldThrowExceptionWhenOAuthTokenMissing() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(null)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No OAuth token in profile");
    }

    @Test
    @DisplayName("Should throw exception when token expiration missing")
    void shouldThrowExceptionWhenTokenExpirationMissing() {
        // Given
        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .tokenExpiresAt(null)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No token expiration in profile");
    }

    @Test
    @DisplayName("Should throw exception when refresh token missing during refresh")
    void shouldThrowExceptionWhenRefreshTokenMissingDuringRefresh() {
        // Given - token is expired
        long pastTimestamp = System.currentTimeMillis() / 1000 - 100;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0);

        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .googleRefreshToken(null)
                .tokenExpiresAt(expiresAt)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No refresh token in profile");
    }

    @Test
    @DisplayName("Should handle OAuth refresh failure")
    void shouldHandleOAuthRefreshFailure() throws Exception {
        // Given - token is expired
        long pastTimestamp = System.currentTimeMillis() / 1000 - 100;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0);

        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .googleRefreshToken(ENCRYPTED_REFRESH_TOKEN)
                .tokenExpiresAt(expiresAt)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("OAuth refresh failed")));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should use default expires_in when not provided in OAuth response")
    void shouldUseDefaultExpiresInWhenNotProvided() throws Exception {
        // Given - token is expired
        long pastTimestamp = System.currentTimeMillis() / 1000 - 100;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0);

        UserProfile profile = UserProfile.builder()
                .userId(USER_ID)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .googleRefreshToken(ENCRYPTED_REFRESH_TOKEN)
                .tokenExpiresAt(expiresAt)
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN);
        when(tokenEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_ACCESS_TOKEN, ENCRYPTED_REFRESH_TOKEN);

        // Mock OAuth refresh response without expires_in
        Map<String, Object> oauthResponse = new HashMap<>();
        oauthResponse.put("access_token", "new-access-token");
        // No expires_in field

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(oauthResponse));

        when(userProfileRepository.update(eq(USER_ID), any(Map.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        String result = userProfileService.getValidOAuthToken(USER_ID);

        // Then
        assertThat(result).isEqualTo("new-access-token");
        verify(userProfileRepository).update(eq(USER_ID), any(Map.class));
    }

    @Test
    @DisplayName("Should get existing user profile")
    void shouldGetExistingUserProfile() throws Exception {
        // Given
        UserProfile existingProfile = UserProfile.builder()
                .userId(USER_ID)
                .email(USER_EMAIL)
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingProfile)));

        // When
        UserProfile result = userProfileService.getProfile(USER_ID, USER_EMAIL);

        // Then
        assertThat(result).isEqualTo(existingProfile);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo(USER_EMAIL);
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Should create new user profile when not exists")
    void shouldCreateNewUserProfileWhenNotExists() throws Exception {
        // Given
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        UserProfile result = userProfileService.getProfile(USER_ID, USER_EMAIL);

        // Then
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo(USER_EMAIL);
        assertThat(result.createdAt()).isInstanceOf(Timestamp.class);
        assertThat(result.updatedAt()).isInstanceOf(Timestamp.class);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.userId()).isEqualTo(USER_ID);
        assertThat(savedProfile.email()).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("Should handle repository execution exception")
    void shouldHandleRepositoryExecutionException() {
        // Given
        CompletableFuture<Optional<UserProfile>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Repository error"));

        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(failedFuture);

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should update existing user profile with display name")
    void shouldUpdateExistingProfileWithDisplayName() throws Exception {
        // Given
        UserProfile existingProfile = UserProfile.builder()
                .userId(USER_ID)
                .email(USER_EMAIL)
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingProfile)));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        UserProfile result = userProfileService.updateProfile(USER_ID, USER_EMAIL, "John Doe", null);

        // Then
        assertThat(result.displayName()).isEqualTo("John Doe");
        assertThat(result.email()).isEqualTo(USER_EMAIL);
        assertThat(result.userId()).isEqualTo(USER_ID);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.displayName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should update existing user profile with email")
    void shouldUpdateExistingProfileWithEmail() throws Exception {
        // Given
        UserProfile existingProfile = UserProfile.builder()
                .userId(USER_ID)
                .email(USER_EMAIL)
                .displayName("John Doe")
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingProfile)));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        UserProfile result = userProfileService.updateProfile(USER_ID, USER_EMAIL, null, "newemail@example.com");

        // Then
        assertThat(result.email()).isEqualTo("newemail@example.com");
        assertThat(result.displayName()).isEqualTo("John Doe");

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.email()).isEqualTo("newemail@example.com");
    }

    @Test
    @DisplayName("Should create new profile when updating non-existent user")
    void shouldCreateNewProfileWhenUpdatingNonExistentUser() throws Exception {
        // Given
        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        UserProfile result = userProfileService.updateProfile(USER_ID, USER_EMAIL, "New User", null);

        // Then
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo(USER_EMAIL);
        assertThat(result.displayName()).isEqualTo("New User");
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.displayName()).isEqualTo("New User");
    }

    @Test
    @DisplayName("Should preserve OAuth tokens when updating profile")
    void shouldPreserveOAuthTokensWhenUpdating() throws Exception {
        // Given
        Timestamp expiresAt = Timestamp.now();
        UserProfile existingProfile = UserProfile.builder()
                .userId(USER_ID)
                .email(USER_EMAIL)
                .googleOAuthToken(ENCRYPTED_ACCESS_TOKEN)
                .googleRefreshToken(ENCRYPTED_REFRESH_TOKEN)
                .tokenExpiresAt(expiresAt)
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        when(userProfileRepository.findByUserId(USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingProfile)));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

        // When
        UserProfile result = userProfileService.updateProfile(USER_ID, USER_EMAIL, "Updated Name", null);

        // Then
        assertThat(result.googleOAuthToken()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(result.googleRefreshToken()).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(result.tokenExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.displayName()).isEqualTo("Updated Name");

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.googleOAuthToken()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
    }
}
