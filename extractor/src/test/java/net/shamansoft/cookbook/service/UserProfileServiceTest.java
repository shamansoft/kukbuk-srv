package net.shamansoft.cookbook.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
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

import static org.mockito.Mockito.doReturn;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Tests")
class UserProfileServiceTest {

    @Mock
    private Firestore firestore;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private WebClient webClient;

    @Mock
    private CollectionReference usersCollection;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private ApiFuture<DocumentSnapshot> documentFuture;

    @Mock
    private ApiFuture<WriteResult> writeFuture;

    @Mock
    private DocumentSnapshot documentSnapshot;

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
    void setUp() throws Exception {
        userProfileService = new UserProfileService(firestore, tokenEncryptionService, webClient);

        // Set up common mocks
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(documentReference);
        when(documentReference.get()).thenReturn(documentFuture);
    }

    @Test
    @DisplayName("Should store OAuth tokens for existing user profile")
    void shouldStoreOAuthTokensForExistingUser() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(documentReference.update(any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        userProfileService.storeOAuthTokens(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_IN);

        // Then
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(documentReference).update(updateCaptor.capture());

        Map<String, Object> updates = updateCaptor.getValue();
        assertThat(updates.get("googleOAuthToken")).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(updates.get("googleRefreshToken")).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(updates.get("tokenExpiresAt")).isInstanceOf(Timestamp.class);
        assertThat(updates.get("updatedAt")).isInstanceOf(Timestamp.class);
        verify(documentReference, never()).set(any());
    }

    @Test
    @DisplayName("Should create new user profile when storing OAuth tokens")
    void shouldCreateNewUserProfileWhenStoringOAuthTokens() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(documentReference.set(any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        userProfileService.storeOAuthTokens(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_IN);

        // Then
        ArgumentCaptor<Map<String, Object>> setCaptor = ArgumentCaptor.forClass(Map.class);
        verify(documentReference).set(setCaptor.capture());

        Map<String, Object> updates = setCaptor.getValue();
        assertThat(updates.get("userId")).isEqualTo(USER_ID);
        assertThat(updates.get("googleOAuthToken")).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(updates.get("googleRefreshToken")).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(updates.get("tokenExpiresAt")).isInstanceOf(Timestamp.class);
        assertThat(updates.get("createdAt")).isInstanceOf(Timestamp.class);
        assertThat(updates.get("updatedAt")).isInstanceOf(Timestamp.class);
        verify(documentReference, never()).update(any(Map.class));
    }

    @Test
    @DisplayName("Should get valid OAuth token when not expired")
    void shouldGetValidOAuthTokenWhenNotExpired() throws Exception {
        // Given - token expires in 1 hour (way beyond the 5-minute buffer)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(futureTimestamp, 0);

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(expiresAt);
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

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getString("googleRefreshToken")).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(expiresAt);
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

        when(documentReference.update(any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        String result = userProfileService.getValidOAuthToken(USER_ID);

        // Then
        assertThat(result).isEqualTo("new-access-token");
        verify(webClient).post();
        verify(documentReference).update(any(Map.class));
    }

    @Test
    @DisplayName("Should throw exception when user profile not found")
    void shouldThrowExceptionWhenUserProfileNotFound() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User profile not found");
    }

    @Test
    @DisplayName("Should throw exception when OAuth token missing")
    void shouldThrowExceptionWhenOAuthTokenMissing() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No OAuth token in profile");
    }

    @Test
    @DisplayName("Should throw exception when token expiration missing")
    void shouldThrowExceptionWhenTokenExpirationMissing() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No token expiration in profile");
    }

    @Test
    @DisplayName("Should throw exception when refresh token missing during refresh")
    void shouldThrowExceptionWhenRefreshTokenMissingDuringRefresh() throws Exception {
        // Given - token is expired
        long pastTimestamp = System.currentTimeMillis() / 1000 - 100;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(pastTimestamp, 0);

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getString("googleRefreshToken")).thenReturn(null);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(expiresAt);

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

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getString("googleRefreshToken")).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(expiresAt);
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

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("googleOAuthToken")).thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(documentSnapshot.getString("googleRefreshToken")).thenReturn(ENCRYPTED_REFRESH_TOKEN);
        when(documentSnapshot.getTimestamp("tokenExpiresAt")).thenReturn(expiresAt);
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

        when(documentReference.update(any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        String result = userProfileService.getValidOAuthToken(USER_ID);

        // Then
        assertThat(result).isEqualTo("new-access-token");
        verify(documentReference).update(any(Map.class));
    }

    @Test
    @DisplayName("Should get existing user profile")
    void shouldGetExistingUserProfile() throws Exception {
        // Given
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("userId", USER_ID);
        profileData.put("email", USER_EMAIL);

        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getData()).thenReturn(profileData);

        // When
        Map<String, Object> result = userProfileService.getOrCreateProfile(USER_ID, USER_EMAIL);

        // Then
        assertThat(result).isEqualTo(profileData);
        verify(documentReference, never()).set(any());
    }

    @Test
    @DisplayName("Should create new user profile when not exists")
    void shouldCreateNewUserProfileWhenNotExists() throws Exception {
        // Given
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);
        when(documentReference.set(any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        // When
        Map<String, Object> result = userProfileService.getOrCreateProfile(USER_ID, USER_EMAIL);

        // Then
        assertThat(result.get("userId")).isEqualTo(USER_ID);
        assertThat(result.get("email")).isEqualTo(USER_EMAIL);
        assertThat(result.get("createdAt")).isInstanceOf(Timestamp.class);
        assertThat(result.get("updatedAt")).isInstanceOf(Timestamp.class);

        ArgumentCaptor<Map<String, Object>> setCaptor = ArgumentCaptor.forClass(Map.class);
        verify(documentReference).set(setCaptor.capture());

        Map<String, Object> savedProfile = setCaptor.getValue();
        assertThat(savedProfile.get("userId")).isEqualTo(USER_ID);
        assertThat(savedProfile.get("email")).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("Should handle Firestore execution exception")
    void shouldHandleFirestoreExecutionException() throws Exception {
        // Given
        when(documentFuture.get()).thenThrow(new ExecutionException(new RuntimeException("Firestore error")));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("Should handle Firestore interrupted exception")
    void shouldHandleFirestoreInterruptedException() throws Exception {
        // Given
        when(documentFuture.get()).thenThrow(new InterruptedException("Thread interrupted"));

        // When & Then
        assertThatThrownBy(() -> userProfileService.getValidOAuthToken(USER_ID))
                .isInstanceOf(InterruptedException.class);
    }




}
