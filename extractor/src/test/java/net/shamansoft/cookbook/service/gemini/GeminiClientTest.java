package net.shamansoft.cookbook.service.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geminiClient = new GeminiClient(webClient, objectMapper);

        // Set private fields using reflection
        ReflectionTestUtils.setField(geminiClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiClient, "model", "gemini-2.0-flash");

        // Initialize the URL
        geminiClient.init();
    }

    @Test
    void requestSuccessfullyReturnsRecipeData() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"title\\": \\"Test Recipe\\"}"
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        setupSuccessfulWebClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().title).isEqualTo("Test Recipe");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void requestHandlesMultiplePartsInResponse() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"title\\": "
                          },
                          {
                            "text": "\\"Test Recipe\\"}"
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        setupSuccessfulWebClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().title).isEqualTo("Test Recipe");
    }

    @Test
    void requestReturnsBlockedWhenContentIsBlocked() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "some content"
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ],
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
                """;

        setupSuccessfulWebClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.BLOCKED);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("SAFETY");
    }

    @Test
    void requestHandlesNoCandidatesInResponse() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": []
                }
                """;

        setupSuccessfulWebClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.OTHER);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("No candidates");
    }

    @Test
    void requestHandlesWebClientResponseException() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500,
                        "Internal Server Error",
                        null,
                        "Server error".getBytes(),
                        null)));

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.OTHER);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("Gemini API error");
    }

    @Test
    void requestHandlesGenericException() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(new RuntimeException("Network failure")));

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.OTHER);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("Network error");
    }

    @Test
    void requestThrowsExceptionWhenGeminiRequestIsNull() {
        // When/Then
        assertThatThrownBy(() -> geminiClient.request(null, TestRecipe.class))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("geminiRequest cannot be null");
    }

    @Test
    void requestThrowsExceptionWhenClazzIsNull() {
        // Given
        GeminiRequest request = createSampleRequest();

        // When/Then
        assertThatThrownBy(() -> geminiClient.request(request, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clazz cannot be null");
    }

    @Test
    void requestLogsWarningWhenFinishReasonIsNotStop() throws JsonProcessingException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"title\\": \\"Test Recipe\\"}"
                          }
                        ]
                      },
                      "finishReason": "MAX_TOKENS"
                    }
                  ]
                }
                """;

        setupSuccessfulWebClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        // Note: We would need a log capture library to verify the warning was logged
    }

    private GeminiRequest createSampleRequest() {
        return GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text("Sample prompt")
                                                .build()))
                                .build()))
                .build();
    }

    private void setupSuccessfulWebClientMock(String responseJson) throws JsonProcessingException {
        JsonNode responseNode = objectMapper.readTree(responseJson);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));
    }

    // Test data class
    static class TestRecipe {
        public String title;
    }
}
