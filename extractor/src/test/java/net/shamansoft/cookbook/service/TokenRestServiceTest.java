package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRestServiceTest {

    @Mock
    private WebClient authWebClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private TokenRestService tokenRestService;

    @BeforeEach
    void setUp() {
        // Setup common WebClient mocking
        when(authWebClient.get()).thenReturn(requestHeadersUriSpec);
    }

    @Test
    void testVerifyToken_success() {
        // Arrange
        String authToken = "test-token";
        Map<String, Object> tokenInfo = Map.of("aud", "test-aud");

        // Mock the URI builder chain
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tokenInfo));

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
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenThrow(new WebClientResponseException(
                        401, "Unauthorized", null, null, null));

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

        // Mock the URI builder chain
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.empty());

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

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authWebClient, times(1)).get();
    }
}
