package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.exception.TranscriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupadataTranscriptServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SupadataTranscriptService service;

    @BeforeEach
    void setUp() {
        service = new SupadataTranscriptService(restClient, "test-api-key");
    }

    @Test
    void getTranscript_successfulResponse_returnsContent() throws Exception {
        String responseJson = """
                {
                  "content": "Hello and welcome to this cooking show. Today we make pasta.",
                  "lang": "en",
                  "availableLangs": ["en", "ru"]
                }
                """;

        setupGetMock(responseJson, HttpStatus.OK);

        String result = service.getTranscript("abc123");

        assertThat(result).isEqualTo("Hello and welcome to this cooking show. Today we make pasta.");
    }

    @Test
    void getTranscript_nullResponse_throwsTranscriptException() {
        setupGetMockNull(HttpStatus.OK);

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("No content");
    }

    @Test
    void getTranscript_emptyContent_throwsTranscriptException() throws Exception {
        String responseJson = """
                {
                  "content": "",
                  "lang": "en"
                }
                """;

        setupGetMock(responseJson, HttpStatus.OK);

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("Empty transcript content");
    }

    @Test
    void getTranscript_httpError_throwsTranscriptException() {
        setupGetMockHttpError(404, "Not Found");

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("API error")
                .hasMessageContaining("404");
    }

    @Test
    void getTranscript_networkError_throwsTranscriptException() {
        setupGetMockException(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("network error");
    }

    @Test
    void getTranscript_asyncJob202_noJobId_throwsTranscriptException() throws Exception {
        String responseJson = """
                {
                  "message": "Processing"
                }
                """;

        setupGetMock(responseJson, HttpStatus.ACCEPTED);

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("202 but no jobId");
    }

    @Test
    void getProviderName_returnsCorrectName() {
        assertThat(service.getProviderName()).isEqualTo("supadata.ai");
    }

    @SuppressWarnings("unchecked")
    private void setupGetMock(String responseJson, HttpStatus status) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseJson);
            ResponseEntity<JsonNode> entity = ResponseEntity.status(status).body(responseNode);

            doReturn(requestHeadersUriSpec).when(restClient).get();
            doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
            doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
            doReturn(responseSpec).when(requestHeadersSpec).retrieve();
            doReturn(entity).when(responseSpec).toEntity(JsonNode.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void setupGetMockNull(HttpStatus status) {
        ResponseEntity<JsonNode> entity = ResponseEntity.status(status).build();

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(entity).when(responseSpec).toEntity(JsonNode.class);
    }

    @SuppressWarnings("unchecked")
    private void setupGetMockHttpError(int statusCode, String statusText) {
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.toEntity(JsonNode.class))
                .thenThrow(new RestClientResponseException(
                        statusText, statusCode, statusText,
                        null, statusText.getBytes(), null));
    }

    @SuppressWarnings("unchecked")
    private void setupGetMockException(RuntimeException exception) {
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.toEntity(JsonNode.class)).thenThrow(exception);
    }
}
