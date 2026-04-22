package net.shamansoft.cookbook.client;

import com.google.cloud.Timestamp;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class GoogleAuthClientTest {

    @Test
    void testExchangeAuthorizationCodeSuccess() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        ReflectionTestUtils.setField(client, "googleClientId", "test-id");
        ReflectionTestUtils.setField(client, "googleClientSecret", "test-secret");

        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "token123");
        response.put("refresh_token", "refresh123");
        response.put("expires_in", 3600);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        GoogleAuthClient.TokenResponse result = client.exchangeAuthorizationCode("code", "http://localhost");

        assertThat(result.accessToken()).isEqualTo("token123");
        assertThat(result.refreshToken()).isEqualTo("refresh123");
        assertThat(result.expiresIn()).isEqualTo(3600);
    }

    @Test
    void testExchangeAuthorizationCodeMissingAccessToken() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        ReflectionTestUtils.setField(client, "googleClientId", "test-id");
        ReflectionTestUtils.setField(client, "googleClientSecret", "test-secret");

        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> response = new HashMap<>();
        response.put("refresh_token", "refresh123");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("code", "http://localhost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testExchangeAuthorizationCodeMissingRefreshToken() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        ReflectionTestUtils.setField(client, "googleClientId", "test-id");
        ReflectionTestUtils.setField(client, "googleClientSecret", "test-secret");

        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "token123");
        response.put("expires_in", 3600);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("code", "http://localhost"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRefreshAccessTokenSuccess() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        ReflectionTestUtils.setField(client, "googleClientId", "test-id");
        ReflectionTestUtils.setField(client, "googleClientSecret", "test-secret");

        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "token123");
        response.put("expires_in", 3600);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        GoogleAuthClient.RefreshTokenResponse result = client.refreshAccessToken("refresh");

        assertThat(result.accessToken()).isEqualTo("token123");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void testIsTokenExpiredNull() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        assertThat(client.isTokenExpired(null)).isTrue();
    }

    @Test
    void testIsTokenExpiredNotExpired() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        long now = System.currentTimeMillis() / 1000;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(now + 3600, 0);
        assertThat(client.isTokenExpired(expiresAt)).isFalse();
    }

    @Test
    void testIsTokenExpiredWithinBuffer() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        long now = System.currentTimeMillis() / 1000;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(now + 120, 0);
        assertThat(client.isTokenExpired(expiresAt)).isTrue();
    }

    @Test
    void testIsTokenExpiredAlreadyExpired() {
        RestClient mockRestClient = mock(RestClient.class);
        GoogleAuthClient client = new GoogleAuthClient(mockRestClient);
        long now = System.currentTimeMillis() / 1000;
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(now - 3600, 0);
        assertThat(client.isTokenExpired(expiresAt)).isTrue();
    }
}
