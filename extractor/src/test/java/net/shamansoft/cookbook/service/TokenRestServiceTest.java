package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRestServiceTest {

    @Mock
    private RestClient authWebClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private TokenRestService tokenRestService;

    @Test
    void testVerifyToken_success() {
        // Arrange
        String authToken = "test-token";
        Map<String, Object> tokenInfo = Map.of("aud", "test-aud");
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
        // Mock the URI builder chain
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(tokenInfo);

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isTrue();
        verify(authWebClient, times(1)).get();
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void testVerifyToken_failure() {
        // Arrange
        String authToken = "test-token";

        // Mock the URI builder chain
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class))
                .thenThrow(new RestClientResponseException(
                        "Unauthorized", 401, "Unauthorized", null, null, null));

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authWebClient, times(1)).get();
    }

    @Test
    void testVerifyToken_nullResponse() {
        // Arrange
        String authToken = "test-token";
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);

        // Mock the URI builder chain
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(null);

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authWebClient, times(1)).get();
    }

    @Test
    void testVerifyToken_exception() {
        // Arrange
        String authToken = "test-token";

        // Mock the URI builder to throw exception
        when(requestHeadersUriSpec.uri(any(Function.class))).thenThrow(new RuntimeException("Network error"));
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authWebClient, times(1)).get();
    }

    @Test
    void testVerifyToken_nullToken() {
        // Act
        boolean result = tokenRestService.verifyToken(null);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testVerifyToken_emptyToken() {
        // Act
        boolean result = tokenRestService.verifyToken("");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testGetAuthToken_success() throws Exception {
        // Arrange
        String authToken = "test-token";
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Google-Token", authToken);
        Map<String, Object> tokenInfo = Map.of("aud", "test-aud");

        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(tokenInfo);
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
        // Act
        String result = tokenRestService.getAuthToken(headers);

        // Assert
        assertThat(result).isEqualTo(authToken);
    }

    @Test
    void testGetAuthToken_noToken() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();

        // Act & Assert
        assertThatThrownBy(() -> tokenRestService.getAuthToken(headers))
                .isInstanceOf(javax.naming.AuthenticationException.class)
                .hasMessage("Google OAuth token required for Drive access");
    }

    @Test
    void testGetAuthToken_invalidToken() {
        // Arrange
        String authToken = "invalid-token";
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Google-Token", authToken);
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class))
                .thenThrow(new RestClientResponseException(
                        "Unauthorized", 401, "Unauthorized", null, null, null));

        // Act & Assert
        assertThatThrownBy(() -> tokenRestService.getAuthToken(headers))
                .isInstanceOf(javax.naming.AuthenticationException.class)
                .hasMessage("Invalid Google OAuth token");
    }
}
