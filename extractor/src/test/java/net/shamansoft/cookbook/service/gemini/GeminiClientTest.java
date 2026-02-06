package net.shamansoft.cookbook.service.gemini;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geminiClient = new GeminiClient(restClient, objectMapper);

        // Set private fields using reflection
        ReflectionTestUtils.setField(geminiClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiClient, "model", "gemini-2.0-flash");

        // Initialize the URL
        geminiClient.init();
    }

    @Test
    void requestSuccessfullyReturnsRecipeData() throws JacksonException {
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

        setupSuccessfulRestClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().title).isEqualTo("Test Recipe");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void requestHandlesMultiplePartsInResponse() throws JacksonException {
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

        setupSuccessfulRestClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().title).isEqualTo("Test Recipe");
    }

    @Test
    void requestReturnsBlockedWhenContentIsBlocked() throws JacksonException {
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

        setupSuccessfulRestClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.BLOCKED);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("SAFETY");
    }

    @Test
    void requestHandlesNoCandidatesInResponse() throws JacksonException {
        // Given
        GeminiRequest request = createSampleRequest();
        String geminiResponseJson = """
                {
                  "candidates": []
                }
                """;

        setupSuccessfulRestClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.OTHER);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("No candidates");
    }

    @Test
    void requestHandlesRestClientResponseException() throws JacksonException {
        // Given
        GeminiRequest request = createSampleRequest();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(String.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class))
                .thenThrow(new RestClientResponseException(
                        "Internal Server Error",
                        500,
                        "Internal Server Error",
                        null,
                        "Server error".getBytes(),
                        null));

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.OTHER);
        assertThat(response.data()).isNull();
        assertThat(response.errorMessage()).contains("Gemini API error");
    }

    @Test
    void requestHandlesGenericException() throws JacksonException {
        // Given
        GeminiRequest request = createSampleRequest();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(String.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class))
                .thenThrow(new RuntimeException("Network failure"));

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
    void requestLogsWarningWhenFinishReasonIsNotStop() throws JacksonException {
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

        setupSuccessfulRestClientMock(geminiResponseJson);

        // When
        GeminiResponse<TestRecipe> response = geminiClient.request(request, TestRecipe.class);

        // Then
        assertThat(response.code()).isEqualTo(GeminiResponse.Code.SUCCESS);
        // Note: We would need a log capture library to verify the warning was logged
    }

    @Test
    void initSetsUrlFromModelName() {
        // Given
        GeminiClient client = new GeminiClient(restClient, objectMapper);
        ReflectionTestUtils.setField(client, "model", "gemini-2.5-flash-lite");

        // When
        client.init();

        // Then
        String url = (String) ReflectionTestUtils.getField(client, "url");
        assertThat(url).isEqualTo("/models/gemini-2.5-flash-lite:generateContent");
    }

    @Test
    void postConstructInitializesUrlCorrectly() {
        // Given/When
        // setUp already calls init() via @PostConstruct simulation

        // Then
        String url = (String) ReflectionTestUtils.getField(geminiClient, "url");
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("/models/gemini-2.0-flash:generateContent");
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

    private void setupSuccessfulRestClientMock(String responseJson) throws JacksonException {
        JsonNode responseNode = objectMapper.readTree(responseJson);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(String.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);
    }

    // Test data class
    static class TestRecipe {
        public String title;
    }
}
