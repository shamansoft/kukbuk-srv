package net.shamansoft.cookbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GeminiRestTransformerTest {

    @Mock
    private WebClient geminiWebClient;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private CleanupService cleanupService;

    @InjectMocks
    private GeminiRestTransformer geminiRestTransformer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        // Use reflection to set the private fields
        ReflectionTestUtils.setField(geminiRestTransformer, "model", "gemini-pro");
        ReflectionTestUtils.setField(geminiRestTransformer, "prompt", "test-prompt");
        ReflectionTestUtils.setField(geminiRestTransformer, "temperature", 0.5f);
        ReflectionTestUtils.setField(geminiRestTransformer, "exampleYaml", "example-yaml-content");
    }

    @Test
    void testTransform_success() throws IOException {
        // Arrange
        String input = "test-input";
        String cleanedResult = "cleaned-output";

        // Create JSON response
        ObjectNode responseNode = objectMapper.createObjectNode();
        ObjectNode candidatesNode = objectMapper.createArrayNode().addObject();
        ObjectNode contentNode = candidatesNode.putObject("content");
        contentNode.put("text", cleanedResult);
        responseNode.set("candidates", candidatesNode);

        when(geminiWebClient.post().uri(anyString()).header(anyString(), anyString()).bodyValue(anyString()).retrieve().bodyToMono(JsonNode.class))
                .thenReturn(java.util.Optional.of(responseNode).map(body -> (JsonNode) body).orElseThrow());

        when(cleanupService.removeYamlSign(eq(cleanedResult))).thenReturn(cleanedResult);

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo(cleanedResult);
        verify(geminiWebClient, times(1)).post();
    }

    @Test
    void testTransform_noCandidates() {
        // Arrange
        String input = "test-input";

        when(geminiWebClient.post().uri(anyString()).header(anyString(), anyString()).bodyValue(anyString()).retrieve().bodyToMono(JsonNode.class))
                .thenReturn(java.util.Optional.of(objectMapper.createObjectNode()));

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo("Could not transform content. Try again later.");
    }

    @Test
    void testTransform_exception() {
        // Arrange
        String input = "test-input";

        when(geminiWebClient.post().uri(anyString()).header(anyString(), anyString()).bodyValue(anyString()).retrieve().bodyToMono(JsonNode.class))
                .thenThrow(new RuntimeException("API error"));

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo("Could not transform content. Try again later.");
    }
}
