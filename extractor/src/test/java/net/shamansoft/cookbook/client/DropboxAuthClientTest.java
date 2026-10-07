package net.shamansoft.cookbook.client;

import com.google.cloud.Timestamp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DropboxAuthClientTest {

    private DropboxAuthClient newClient(RestClient restClient) {
        DropboxAuthClient client = new DropboxAuthClient(restClient);
        ReflectionTestUtils.setField(client, "appKey", "app-key");
        ReflectionTestUtils.setField(client, "appSecret", "app-secret");
        return client;
    }

    private RestClient.ResponseSpec stubPost(RestClient restClient) {
        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);
        return responseSpec;
    }

    @Test
    void exchangeAuthorizationCode_returnsTokens() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-access");
        response.put("refresh_token", "dbx-refresh");
        response.put("expires_in", 14400);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        DropboxAuthClient.TokenResponse result =
                newClient(restClient).exchangeAuthorizationCode("code", "sar://dropbox-callback", null);

        assertThat(result.accessToken()).isEqualTo("dbx-access");
        assertThat(result.refreshToken()).isEqualTo("dbx-refresh");
        assertThat(result.expiresIn()).isEqualTo(14400);
    }

    /** Captures the form the client posts to the token endpoint. */
    private MultiValueMap<String, String> exchangeAndCaptureForm(String codeVerifier) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        ArgumentCaptor<Object> form = ArgumentCaptor.forClass(Object.class);
        when(reqSpec.body(form.capture())).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("access_token", "a", "refresh_token", "r", "expires_in", 14400));

        newClient(restClient).exchangeAuthorizationCode("code", "sar://dropbox-callback", codeVerifier);

        return (MultiValueMap<String, String>) form.getValue();
    }

    @Test
    void exchangeAuthorizationCode_sendsPkceVerifierAlongsideTheSecret() {
        MultiValueMap<String, String> form = exchangeAndCaptureForm("verifier-123");

        assertThat(form.getFirst("code_verifier")).isEqualTo("verifier-123");
        assertThat(form.getFirst("client_secret")).isEqualTo("app-secret");
    }

    @Test
    void exchangeAuthorizationCode_withoutVerifier_omitsTheParameter() {
        assertThat(exchangeAndCaptureForm(null)).doesNotContainKey("code_verifier");
        assertThat(exchangeAndCaptureForm(" ")).doesNotContainKey("code_verifier");
    }

    @Test
    void exchangeAuthorizationCode_missingRefreshToken_throwsIllegalState() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-access");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        assertThatThrownBy(() -> newClient(restClient).exchangeAuthorizationCode("code", "sar://cb", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refreshAccessToken_returnsNewAccessTokenAndExpiry() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-new");
        response.put("expires_in", 14400);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        DropboxAuthClient.RefreshTokenResponse result =
                newClient(restClient).refreshAccessToken("dbx-refresh");

        assertThat(result.accessToken()).isEqualTo("dbx-new");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void isTokenExpired_nullOrPast_true_future_false() {
        DropboxAuthClient client = newClient(mock(RestClient.class));
        long now = System.currentTimeMillis() / 1000;
        assertThat(client.isTokenExpired(null)).isTrue();
        assertThat(client.isTokenExpired(Timestamp.ofTimeSecondsAndNanos(now - 60, 0))).isTrue();
        assertThat(client.isTokenExpired(Timestamp.ofTimeSecondsAndNanos(now + 3600, 0))).isFalse();
    }

    @Test
    void revokeToken_swallowsErrors() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RuntimeException("boom"));
        assertThatCode(() -> newClient(restClient).revokeToken("dbx-access")).doesNotThrowAnyException();
    }
}
