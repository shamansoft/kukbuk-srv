package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DropboxClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Stub restClient.post().uri(str).header(...).header(...).contentType(...).body(...).retrieve() */
    private RestClient.ResponseSpec stubPost(RestClient restClient) {
        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        doReturn(reqSpec).when(reqSpec).body(any(Object.class));
        when(reqSpec.retrieve()).thenReturn(responseSpec);
        return responseSpec;
    }

    /** Dropbox endpoint errors are HTTP 409 with the reason in the body's error_summary. */
    private static RestClientResponseException endpointError(String errorSummary) {
        byte[] body = ("{\"error_summary\": \"" + errorSummary + "\"}").getBytes(StandardCharsets.UTF_8);
        return new RestClientResponseException("conflict", 409, "Conflict", null, body, StandardCharsets.UTF_8);
    }

    @Test
    void createFolder_folderConflict409_treatedAsExists() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(endpointError("path/conflict/folder/.."));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatCode(() -> client.createFolder("/MyKukBuk", "token")).doesNotThrowAnyException();
    }

    @Test
    void createFolder_other409_throwsClientException() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(endpointError("path/conflict/file/."));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.createFolder("/MyKukBuk", "token"))
                .isInstanceOf(ClientException.class);
    }

    @Test
    void download_missingFile_reportedAsNotFound() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(byte[].class)).thenThrow(endpointError("path/not_found/.."));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.downloadAsBytes("id:gone", "token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getMetadata_missingFile_reportedAsNotFound() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(endpointError("path/not_found/."));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.getMetadata("id:gone", "token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getMetadata_otherError_propagatesUnchanged() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        RestClientResponseException serverError =
                new RestClientResponseException("server error", 500, "Error", null, null, null);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(serverError);

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.getMetadata("id:abc", "token")).isSameAs(serverError);
    }

    @Test
    void headerSafe_escapesNonAscii_keepsAscii() {
        assertThat(DropboxClient.headerSafe("{\"path\":\"/Рецепты/a.yaml\"}"))
                .isEqualTo("{\"path\":\"/\\u0420\\u0435\\u0446\\u0435\\u043f\\u0442\\u044b/a.yaml\"}");
    }

    @Test
    void createFolder_otherError_throwsClientException() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientResponseException("server error", 500, "Error", null, null, null));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.createFolder("/MyKukBuk", "token"))
                .isInstanceOf(ClientException.class);
    }

    @Test
    void upload_returnsFileEntryWithId() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("id", "id:abc123", "name", "pasta.yaml", "path_display", "/pasta.yaml"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.FileEntry entry = client.upload("/pasta.yaml", "content".getBytes(), "token");

        assertThat(entry.id()).isEqualTo("id:abc123");
        assertThat(entry.name()).isEqualTo("pasta.yaml");
        assertThat(entry.pathDisplay()).isEqualTo("/pasta.yaml");
    }

    @Test
    void downloadAsString_returnsUtf8() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(byte[].class))
                .thenReturn("title: Хачапури".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThat(client.downloadAsString("id:abc", "token")).isEqualTo("title: Хачапури");
    }

    @Test
    void listFolder_filtersToFilesAndMapsCursor() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> file = Map.of(".tag", "file", "id", "id:f1", "name", "a.yaml",
                "path_display", "/a.yaml", "server_modified", "2024-01-15T10:00:00Z");
        Map<String, Object> folder = Map.of(".tag", "folder", "id", "id:d1", "name", "sub");
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("entries", List.of(file, folder), "cursor", "CURSOR1", "has_more", true));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.ListResult result = client.listFolder("", 100, "token");

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).id()).isEqualTo("id:f1");
        assertThat(result.cursor()).isEqualTo("CURSOR1");
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void getMetadata_returnsFileEntry() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("id", "id:abc", "name", "photo.jpg",
                        "path_display", "/photo.jpg", "server_modified", "2024-02-01T00:00:00Z"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.FileEntry entry = client.getMetadata("id:abc", "token");

        assertThat(entry.name()).isEqualTo("photo.jpg");
        assertThat(entry.serverModified()).isEqualTo("2024-02-01T00:00:00Z");
    }
}
