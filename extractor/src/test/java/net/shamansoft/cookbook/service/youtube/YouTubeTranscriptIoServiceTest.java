package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.exception.TranscriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeTranscriptIoServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private YouTubeTranscriptIoService service;

    @BeforeEach
    void setUp() {
        service = new YouTubeTranscriptIoService(restClient, "test-api-key");
    }

    @Test
    void getTranscript_successfulResponse_returnsTranscriptText() throws Exception {
        String responseJson = """
                [
                  {
                    "id": "abc123",
                    "title": "Cooking Video",
                    "tracks": [
                      {
                        "language": "English",
                        "transcript": [
                          {"text": "Hello and welcome", "start": 0.0, "dur": 2.5},
                          {"text": "to this cooking show", "start": 2.5, "dur": 2.0},
                          {"text": "today we make pasta", "start": 4.5, "dur": 3.0}
                        ]
                      }
                    ]
                  }
                ]
                """;

        setupMock(responseJson);

        String result = service.getTranscript("abc123");

        assertThat(result).isEqualTo("Hello and welcome to this cooking show today we make pasta");
    }

    @Test
    void getTranscript_nullResponse_throwsTranscriptException() {
        setupMockNull();

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void getTranscript_emptyArray_throwsTranscriptException() throws Exception {
        setupMock("[]");

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void getTranscript_noTracks_throwsTranscriptException() throws Exception {
        String responseJson = """
                [
                  {
                    "id": "abc123",
                    "title": "No Captions Video",
                    "tracks": []
                  }
                ]
                """;

        setupMock(responseJson);

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("No transcript tracks");
    }

    @Test
    void getTranscript_emptyTranscriptInTrack_throwsTranscriptException() throws Exception {
        String responseJson = """
                [
                  {
                    "id": "abc123",
                    "tracks": [
                      {
                        "language": "English",
                        "transcript": []
                      }
                    ]
                  }
                ]
                """;

        setupMock(responseJson);

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("Empty transcript in track");
    }

    @Test
    void getTranscript_httpError_throwsTranscriptException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class))
                .thenThrow(new RestClientResponseException(
                        "Unauthorized", 401, "Unauthorized",
                        null, "Invalid API key".getBytes(), null));

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("API error")
                .hasMessageContaining("401");
    }

    @Test
    void getTranscript_networkError_throwsTranscriptException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.getTranscript("abc123"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("network error");
    }

    @Test
    void getProviderName_returnsCorrectName() {
        assertThat(service.getProviderName()).isEqualTo("youtube-transcript.io");
    }

    @Test
    void getTranscript_multipleSegments_concatenatesWithSpaces() throws Exception {
        String responseJson = """
                [
                  {
                    "id": "vid1",
                    "tracks": [
                      {
                        "language": "English",
                        "transcript": [
                          {"text": "First"},
                          {"text": "Second"},
                          {"text": "Third"}
                        ]
                      }
                    ]
                  }
                ]
                """;

        setupMock(responseJson);

        String result = service.getTranscript("vid1");
        assertThat(result).isEqualTo("First Second Third");
    }

    private void setupMock(String responseJson) throws Exception {
        JsonNode responseNode = objectMapper.readTree(responseJson);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);
    }

    private void setupMockNull() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(null);
    }
}
