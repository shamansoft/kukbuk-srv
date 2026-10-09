package net.shamansoft.cookbook.service.openai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new OpenAiClient(restClient, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    private void stubRequestChain() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(String.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private void stubResponse(String responseJson) {
        stubRequestChain();
        JsonNode responseNode = objectMapper.readTree(responseJson);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);
    }

    private void stubHttpError(int status, String body) {
        stubRequestChain();
        when(responseSpec.body(JsonNode.class)).thenThrow(new RestClientResponseException(
                "error", status, "error", null, body.getBytes(), null));
    }

    @Test
    void isConfiguredOnlyWhenApiKeyIsPresent() {
        assertThat(client.isConfigured()).isTrue();

        ReflectionTestUtils.setField(client, "apiKey", " ");
        assertThat(client.isConfigured()).isFalse();

        ReflectionTestUtils.setField(client, "apiKey", null);
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    void postsBodyToResponsesEndpointWithBearerToken() {
        stubResponse("""
                {"status": "completed",
                 "output": [{"type": "message", "content": [{"type": "output_text", "text": "{}"}]}]}
                """);

        client.request(Map.of("model", "gpt-6-luna"));

        verify(requestBodyUriSpec).uri("/responses");
        verify(requestBodySpec).header("Authorization", "Bearer test-api-key");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).body(body.capture());
        assertThat(objectMapper.readTree(body.getValue()).path("model").asText()).isEqualTo("gpt-6-luna");
    }

    @Test
    void returnsOutputTextOfCompletedResponse() {
        stubResponse("""
                {"status": "completed",
                 "output": [{"type": "message", "content": [{"type": "output_text", "text": "{\\"is_recipe\\": true}"}]}]}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("{\"is_recipe\": true}");
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void skipsReasoningItemsAndJoinsTextParts() {
        stubResponse("""
                {"status": "completed",
                 "output": [
                   {"type": "reasoning", "summary": []},
                   {"type": "message", "content": [
                     {"type": "output_text", "text": "{\\"is_recipe\\":"},
                     {"type": "output_text", "text": " false}"}]}]}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("{\"is_recipe\": false}");
    }

    @Test
    void failsWhenResponseIsIncomplete() {
        stubResponse("""
                {"status": "incomplete", "incomplete_details": {"reason": "max_output_tokens"}, "output": []}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("incomplete").contains("max_output_tokens");
    }

    @Test
    void failsWhenModelRefuses() {
        stubResponse("""
                {"status": "completed",
                 "output": [{"type": "message", "content": [{"type": "refusal", "refusal": "I cannot help with that"}]}]}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("refused").contains("I cannot help with that");
    }

    @Test
    void failsWhenThereIsNoOutputText() {
        stubResponse("""
                {"status": "completed", "output": []}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("No output text");
    }

    @Test
    void failsOnNullResponse() {
        stubRequestChain();
        when(responseSpec.body(JsonNode.class)).thenReturn(null);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Empty response");
    }

    @Test
    void surfacesOpenAiErrorMessageOnHttpError() {
        stubHttpError(400, """
                {"error": {"message": "Unsupported parameter: 'temperature' is not supported with this model.",
                           "type": "invalid_request_error", "param": "temperature", "code": null}}
                """);

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .isEqualTo("OpenAI API error 400: Unsupported parameter: 'temperature' is not supported with this model.");
    }

    @Test
    void fallsBackToRawBodyWhenHttpErrorIsNotOpenAiJson() {
        stubHttpError(502, "Bad Gateway");

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("OpenAI API error 502: Bad Gateway");
    }

    @Test
    void reportsNetworkFailures() {
        stubRequestChain();
        when(responseSpec.body(JsonNode.class)).thenThrow(new RuntimeException("Connection reset"));

        OpenAiClient.Result result = client.request(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Network error").contains("Connection reset");
    }
}
