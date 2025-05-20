package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenRestServiceTest {

    @Mock
    private WebClient authClient;

    @InjectMocks
    private TokenRestService tokenRestService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testVerifyToken_success() {
        // Arrange
        String authToken = "test-token";
        WebClient.RequestHeadersSpec requestMock = mock(WebClient.RequestHeadersSpec.class);
        WebClient.RequestHeadersUriSpec uriMock = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.ResponseSpec responseMock = mock(WebClient.ResponseSpec.class);

        when(authClient.get()).thenReturn(requestMock);
        when(requestMock.uri(any())).thenReturn(uriMock);
        when(uriMock.retrieve()).thenReturn(responseMock);
        when(responseMock.bodyToMono(Map.class)).thenReturn(java.util.Optional.of(Map.of("aud", "test-aud")));

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isTrue();
        verify(authClient, times(1)).get();
    }

    @Test
    void testVerifyToken_failure() {
        // Arrange
        String authToken = "test-token";
        WebClient.RequestHeadersSpec requestMock = mock(WebClient.RequestHeadersSpec.class);
        WebClient.RequestHeadersUriSpec uriMock = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.ResponseSpec responseMock = mock(WebClient.ResponseSpec.class);

        when(authClient.get()).thenReturn(requestMock);
        when(requestMock.uri(any())).thenReturn(uriMock);
        when(uriMock.retrieve()).thenReturn(responseMock);
        when(responseMock.bodyToMono(Map.class)).thenThrow(new WebClientResponseException(401, "Unauthorized", "Unauthorized", null, null));

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authClient, times(1)).get();
    }

    @Test
    void testVerifyToken_exception() {
        // Arrange
        String authToken = "test-token";
        WebClient.RequestHeadersSpec requestMock = mock(WebClient.RequestHeadersSpec.class);

        when(authClient.get()).thenReturn(requestMock);
        when(requestMock.uri(any())).thenThrow(new RuntimeException("Network error"));

        // Act
        boolean result = tokenRestService.verifyToken(authToken);

        // Assert
        assertThat(result).isFalse();
        verify(authClient, times(1)).get();
    }
}
