package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        when(responseSpec.toEntity(byte[].class)).thenThrow(endpointError("path/not_found/.."));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.downloadAsBytes("id:gone", "token"))
                .isInstanceOf(DropboxClient.FileMissingException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void downloadForWrite_pairsContentWithRevisionFromTheDownload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://content.dropboxapi.com/2/files/download"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("title: Хачапури".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM)
                        .header("Dropbox-API-Result", """
                                {"id":"id:abc","rev":"123456789"}
                                """.strip()));

        DropboxClient client = new DropboxClient(builder.build(), objectMapper);
        assertThat(client.downloadForWrite("id:abc", "token"))
                .isEqualTo(new DropboxClient.RecipeDownload("id:abc", "123456789", "title: Хачапури"));
        server.verify();
    }

    @Test
    void downloadForWrite_rejectsContentWithoutRevision() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.ok()
                        .header("Dropbox-API-Result", "{\"id\":\"id:abc\"}")
                        .body("title: Recipe".getBytes(StandardCharsets.UTF_8)));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.downloadForWrite("id:abc", "token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void downloadForWrite_doesNotTreatOtherConflictsAsMissing() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        RestClientResponseException error = endpointError("path/not_file/..");
        when(responseSpec.toEntity(byte[].class)).thenThrow(error);

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.downloadForWrite("/a.yaml", "token")).isSameAs(error);
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

    private static RestClientResponseException rateLimited(String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.add("Retry-After", retryAfter);
        }
        return new RestClientResponseException("Too Many Requests", 429, "Too Many Requests", headers, null, null);
    }

    @Test
    void rateLimited_waitsRetryAfterAndRetries() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(rateLimited("2"))
                .thenReturn(Map.of("id", "id:abc", "name", "a.yaml"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        List<Long> slept = new ArrayList<>();
        client.sleeper = slept::add;

        assertThat(client.getMetadata("id:abc", "token").id()).isEqualTo("id:abc");
        assertThat(slept).containsExactly(2000L);
    }

    @Test
    void rateLimited_givesUpAfterTwoRetries() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(rateLimited(null));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        List<Long> slept = new ArrayList<>();
        client.sleeper = slept::add;

        assertThatThrownBy(() -> client.listFolder("", 20, "token"))
                .isInstanceOf(RestClientResponseException.class);
        assertThat(slept).containsExactly(1000L, 1000L);
    }

    @Test
    void rateLimited_longRetryAfter_failsFastWithoutSleeping() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(rateLimited("300"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        List<Long> slept = new ArrayList<>();
        client.sleeper = slept::add;

        assertThatThrownBy(() -> client.listFolder("", 20, "token"))
                .isInstanceOf(RestClientResponseException.class);
        assertThat(slept).isEmpty();
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
    void create_exclusivelyAddsFileAndReturnsItsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://content.dropboxapi.com/2/files/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var arg = objectMapper.readTree(request.getHeaders().getFirst("Dropbox-API-Arg"));
                    assertThat(arg.path("path").asText()).isEqualTo("/pasta.yaml");
                    assertThat(arg.path("mode").asText()).isEqualTo("add");
                    assertThat(arg.path("autorename").asBoolean(true)).isFalse();
                    assertThat(arg.path("strict_conflict").asBoolean()).isTrue();
                })
                .andExpect(content().bytes("content".getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess("""
                        {"id":"id:abc123","name":"pasta.yaml","path_display":"/pasta.yaml"}
                        """, MediaType.APPLICATION_JSON));

        DropboxClient client = new DropboxClient(builder.build(), objectMapper);
        DropboxClient.FileEntry entry = client.create("/pasta.yaml", "content".getBytes(StandardCharsets.UTF_8), "token");

        assertThat(entry.id()).isEqualTo("id:abc123");
        assertThat(entry.name()).isEqualTo("pasta.yaml");
        assertThat(entry.pathDisplay()).isEqualTo("/pasta.yaml");
        server.verify();
    }

    @Test
    void update_usesStrictRevisionConditionAndReportsConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://content.dropboxapi.com/2/files/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var arg = objectMapper.readTree(request.getHeaders().getFirst("Dropbox-API-Arg"));
                    assertThat(arg.path("path").asText()).isEqualTo("id:abc");
                    assertThat(arg.path("mode").path(".tag").asText()).isEqualTo("update");
                    assertThat(arg.path("mode").path("update").asText()).isEqualTo("123456789");
                    assertThat(arg.path("autorename").asBoolean(true)).isFalse();
                    assertThat(arg.path("strict_conflict").asBoolean()).isTrue();
                })
                .andExpect(content().bytes("content".getBytes(StandardCharsets.UTF_8)))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_summary\":\"path/conflict/file/..\"}"));

        DropboxClient client = new DropboxClient(builder.build(), objectMapper);
        assertThatThrownBy(() -> client.update("id:abc", "123456789",
                "content".getBytes(StandardCharsets.UTF_8), "token"))
                .isInstanceOf(DropboxClient.WriteConflictException.class);
        server.verify();
    }

    @Test
    void downloadAsString_returnsUtf8() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.toEntity(byte[].class))
                .thenReturn(ResponseEntity.ok("title: Хачапури".getBytes(StandardCharsets.UTF_8)));

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
