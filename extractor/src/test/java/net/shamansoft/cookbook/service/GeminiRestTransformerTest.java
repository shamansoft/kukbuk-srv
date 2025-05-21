package net.shamansoft.cookbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiRestTransformerTest {

    @Mock
    private WebClient geminiWebClient;

    @Mock
    private ResourcesLoader resourceLoader;

    @Mock
    private CleanupService cleanupService;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private ObjectMapper objectMapper;

    private ObjectMapper testObjectMapper = new ObjectMapper();

    @InjectMocks
    private GeminiRestTransformer geminiRestTransformer;

    @BeforeEach
    void setUp() throws IOException {


        when(resourceLoader.loadYaml(anyString())).thenReturn("yaml-example");
        when(objectMapper.writeValueAsString(any())).thenReturn("valid-json");
        geminiRestTransformer.init();

        // Setup common WebClient mocking
        when(geminiWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

    }

    @Test
    void testTransform_success() throws IOException {
        // Arrange
        String input = "test-input";
        String rawResult = "raw-output";
        String cleanedResult = "cleaned-output";

        // Create JSON response
        ObjectNode responseNode = testObjectMapper.createObjectNode();
        ObjectNode candidateNode = testObjectMapper.createObjectNode();
        ObjectNode contentNode = testObjectMapper.createObjectNode();
        ObjectNode partNode = testObjectMapper.createObjectNode();

        partNode.put("text", rawResult);
        contentNode.set("parts", testObjectMapper.createArrayNode().add(partNode));
        candidateNode.set("content", contentNode);
        responseNode.set("candidates", testObjectMapper.createArrayNode().add(candidateNode));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
        when(cleanupService.removeYamlSign(eq(rawResult))).thenReturn(cleanedResult);

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo(cleanedResult);
        verify(geminiWebClient, times(1)).post();
        verify(cleanupService, times(1)).removeYamlSign(rawResult);
    }

    @Test
    void testTransform_noCandidates() {
        // Arrange
        String input = "test-input";
        ObjectNode emptyResponse = testObjectMapper.createObjectNode();

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(emptyResponse));

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo("Could not transform content. Try again later.");
        verify(cleanupService, never()).removeYamlSign(anyString());
    }

    @Test
    void testTransform_exception() {
        // Arrange
        String input = "test-input";

        when(responseSpec.bodyToMono(JsonNode.class)).thenThrow(new RuntimeException("API error"));

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo("Could not transform content. Try again later.");
        verify(cleanupService, never()).removeYamlSign(anyString());
    }

    @Test
    void testTransform_malformedResponse() {
        // Arrange
        String input = "test-input";

        // Create a malformed response with missing fields
        ObjectNode malformedResponse = testObjectMapper.createObjectNode();
        malformedResponse.set("candidates", testObjectMapper.createArrayNode().add(
                testObjectMapper.createObjectNode()  // Empty candidate with no content field
        ));

        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(malformedResponse));

        // Act
        String result = geminiRestTransformer.transform(input);

        // Assert
        assertThat(result).isEqualTo("Could not transform content. Try again later.");
        verify(cleanupService, never()).removeYamlSign(anyString());
    }
}