package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.exception.YouTubeApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeDataServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private YouTubeDataService service;

    @BeforeEach
    void setUp() {
        service = new YouTubeDataService(restClient, "test-api-key");
    }

    @Test
    void getMetadata_successfulResponse_returnsMetadata() throws Exception {
        String responseJson = """
                {
                  "items": [
                    {
                      "snippet": {
                        "title": "Easy Pasta Recipe",
                        "description": "In this video I show you how to make pasta.",
                        "channelTitle": "Cooking Channel",
                        "publishedAt": "2024-05-08T10:00:00Z"
                      }
                    }
                  ]
                }
                """;

        setupMock(responseJson);

        YouTubeMetadata result = service.getMetadata("abc123");

        assertThat(result.title()).isEqualTo("Easy Pasta Recipe");
        assertThat(result.description()).isEqualTo("In this video I show you how to make pasta.");
        assertThat(result.channelName()).isEqualTo("Cooking Channel");
        assertThat(result.publishedAt()).isEqualTo(Instant.parse("2024-05-08T10:00:00Z"));
    }

    @Test
    void getMetadata_missingOptionalFields_returnsDefaults() throws Exception {
        String responseJson = """
                {
                  "items": [
                    {
                      "snippet": {
                        "title": "Minimal Video"
                      }
                    }
                  ]
                }
                """;

        setupMock(responseJson);

        YouTubeMetadata result = service.getMetadata("abc123");

        assertThat(result.title()).isEqualTo("Minimal Video");
        assertThat(result.description()).isEmpty();
        assertThat(result.channelName()).isEmpty();
        assertThat(result.publishedAt()).isNull();
    }

    @Test
    void getMetadata_videoNotFound_throwsYouTubeApiException() throws Exception {
        String responseJson = """
                {
                  "items": []
                }
                """;

        setupMock(responseJson);

        assertThatThrownBy(() -> service.getMetadata("nonexistent"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("Video not found");
    }

    @Test
    void getMetadata_nullVideoId_throwsYouTubeApiException() {
        assertThatThrownBy(() -> service.getMetadata(null))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    void getMetadata_blankVideoId_throwsYouTubeApiException() {
        assertThatThrownBy(() -> service.getMetadata("  "))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    void getMetadata_nullResponse_throwsYouTubeApiException() {
        setupMockNull();

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("Invalid response");
    }

    @Test
    void getMetadata_noItemsField_throwsYouTubeApiException() throws Exception {
        String responseJson = """
                {
                  "kind": "youtube#videoListResponse"
                }
                """;

        setupMock(responseJson);

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("Invalid response");
    }

    @Test
    void getMetadata_noSnippet_throwsYouTubeApiException() throws Exception {
        String responseJson = """
                {
                  "items": [
                    {
                      "id": "abc123"
                    }
                  ]
                }
                """;

        setupMock(responseJson);

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("No snippet data");
    }

    @Test
    void getMetadata_httpError_throwsYouTubeApiException() {
        setupMockForHttpError(new RestClientResponseException(
                "Forbidden", 403, "Forbidden",
                null, "API key invalid".getBytes(), null));

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("YouTube Data API error")
                .hasMessageContaining("403");
    }

    @Test
    void getMetadata_networkError_throwsYouTubeApiException() {
        setupMockForHttpError(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(YouTubeApiException.class)
                .hasMessageContaining("network error");
    }

    @SuppressWarnings("unchecked")
    private void setupMock(String responseJson) throws Exception {
        JsonNode responseNode = objectMapper.readTree(responseJson);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);
    }

    @SuppressWarnings("unchecked")
    private void setupMockNull() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    private void setupMockForHttpError(RuntimeException exception) {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(exception);
    }
}
