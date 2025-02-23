package net.shamansoft.cookbook;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceConfigTest {

    @Test
    void testHideKey() {
        // Arrange
        URI uri = URI.create("http://example.com/path?key=abcd1234&otherParam=value");
        ClientRequest clientRequest = Mockito.mock(ClientRequest.class);
        Mockito.when(clientRequest.url()).thenReturn(uri);

        // Act
        String result = ServiceConfig.hideKey(clientRequest);

        // Assert
        assertThat(result).isEqualTo("/path?key=ab***&otherParam=value");
    }
}