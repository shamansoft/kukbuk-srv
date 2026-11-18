package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings({"rawtypes", "unchecked"})
class GoogleDriveTest {

    private static final String MULTIPART_UPLOAD_URL =
            "https://drive.example.test/upload/drive/v3/files?uploadType=multipart&fields=id";

    @Test
    void testCreateFileSendsJsonMetadataAndUtf8ContentInOneMultipartRequest() {
        HttpDrive drive = HttpDrive.create();
        String name = "Café \"мечта\" \\ spéciale.yaml";
        String yaml = "title: Crème brûlée 🍮\n";

        drive.uploadServer().expect(requestTo(MULTIPART_UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(request -> {
                    MediaType type = request.getHeaders().getContentType();
                    assertThat(type).isNotNull();
                    assertThat(type.getType()).isEqualTo("multipart");
                    assertThat(type.getSubtype()).isEqualTo("related");
                    String boundary = type.getParameter("boundary");
                    assertThat(boundary).isNotBlank();

                    byte[] body = ((MockClientHttpRequest) request).getBodyAsBytes();
                    assertThat(request.getHeaders().getContentLength()).isEqualTo(body.length);
                    String[] parts = new String(body, StandardCharsets.UTF_8)
                            .split(Pattern.quote("\r\n--" + boundary), -1);
                    assertThat(parts).hasSize(3);
                    String metadataHeader = "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n";
                    assertThat(parts[0]).startsWith(metadataHeader);
                    var metadata = new ObjectMapper().readTree(parts[0].substring(metadataHeader.length()));
                    assertThat(metadata.path("name").asString()).isEqualTo(name);
                    assertThat(metadata.path("parents").size()).isEqualTo(1);
                    assertThat(metadata.path("parents").path(0).asString()).isEqualTo("folder-123");
                    assertThat(metadata.path("mimeType").asString()).isEqualTo("application/x-yaml");
                    assertThat(parts[1]).isEqualTo("\r\nContent-Type: application/x-yaml; charset=UTF-8\r\n\r\n" + yaml);
                    assertThat(parts[2]).isEqualTo("--\r\n");
                })
                .andRespond(withSuccess("{\"id\":\"file-123\"}", MediaType.APPLICATION_JSON));

        assertThat(drive.client().createFile(name, "folder-123", yaml, "test-token"))
                .isEqualTo(new GoogleDrive.Item("file-123", name));
        drive.verify();
    }

    @Test
    void testCreateFileWithoutFileIdFails() {
        HttpDrive drive = HttpDrive.create();
        drive.uploadServer().expect(requestTo(MULTIPART_UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> drive.client().createFile("test.yaml", "folder-123", "title: Test", "test-token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("missing file ID");
        drive.verify();
    }

    @Test
    void testCreateFileProviderErrorFailsWithNoOtherRequest() {
        HttpDrive drive = HttpDrive.create();
        drive.uploadServer().expect(requestTo(MULTIPART_UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> drive.client().createFile("test.yaml", "folder-123", "title: Test", "test-token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to create file");
        drive.verify();
    }

    @Test
    void testGetFileSearchesEmptyFirstPageAndPreservesOpaquePageToken() {
        HttpDrive drive = HttpDrive.create();
        String name = "Farmer's \\ café.yaml";
        drive.driveServer().expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/drive/v3/files");
                    assertThat(request.getURI().getQuery())
                            .contains("q=name='Farmer\\'s \\\\ café.yaml' and 'folder-123' in parents and trashed=false",
                                    "fields=files(id),nextPageToken,incompleteSearch")
                            .doesNotContain("pageToken=");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[],\"nextPageToken\":\"cursor+A/=\"}", MediaType.APPLICATION_JSON));
        drive.driveServer().expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("pageToken=cursor%2BA%2F%3D"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[{\"id\":\"file-123\"}]}", MediaType.APPLICATION_JSON));

        assertThat(drive.client().getFile(name, "folder-123", "test-token"))
                .contains(new GoogleDrive.Item("file-123", name));
        drive.verify();
    }

    @Test
    void testGetFileRejectsIncompleteSearch() {
        HttpDrive drive = HttpDrive.create();
        drive.driveServer().expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/drive/v3/files"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[],\"incompleteSearch\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> drive.client().getFile("test.yaml", "folder-123", "test-token"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Could not determine");
        drive.verify();
    }

    private record HttpDrive(GoogleDrive client, MockRestServiceServer driveServer, MockRestServiceServer uploadServer) {
        private static HttpDrive create() {
            var driveBuilder = RestClient.builder().baseUrl("https://drive.example.test/drive/v3");
            var uploadBuilder = RestClient.builder().baseUrl("https://drive.example.test/upload/drive/v3");
            var driveServer = MockRestServiceServer.bindTo(driveBuilder).build();
            var uploadServer = MockRestServiceServer.bindTo(uploadBuilder).build();
            return new HttpDrive(new GoogleDrive(driveBuilder.build(), uploadBuilder.build()), driveServer, uploadServer);
        }

        private void verify() {
            driveServer.verify();
            uploadServer.verify();
        }
    }

    @Test
    void testGetFolderFound() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "folder_id_123")));
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("TestFolder", "dummyToken");

        // Verify
        assertThat(folder).isPresent();
        assertThat(folder.get().id()).isEqualTo("folder_id_123");
        assertThat(folder.get().url()).isEqualTo("https://drive.google.com/file/d/folder_id_123/view");
    }

    @Test
    void testGetFolderNotFound() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", Collections.emptyList());
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("NonExistentFolder", "dummyToken");

        // Verify
        assertThat(folder).isEmpty();
    }

    @Test
    void testCreateFolderSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("id", "new_folder_123");
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        GoogleDrive.Item folder = googleDrive.createFolder("NewFolder", "dummyToken");

        // Verify
        assertThat(folder).isNotNull();
        assertThat(folder.id()).isEqualTo("new_folder_123");
        assertThat(folder.name()).isEqualTo("NewFolder");
    }

    @Test
    void testGetFileFound() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "file_456")));
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> file = googleDrive.getFile("TestFile", "folder_123", "dummyToken");

        // Verify
        assertThat(file).isPresent();
        assertThat(file.get().id()).isEqualTo("file_456");
    }

    @Test
    void testGetFileNotFound() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", Collections.emptyList());
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> file = googleDrive.getFile("NonExistentFile", "folder_123", "dummyToken");

        // Verify
        assertThat(file).isEmpty();
    }

    @Test
    void testCreateFolderError() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFolder("NewFolder", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Unexpected response");
    }

    @Test
    void testUpdateFileSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        RestClient mockUploadClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        ReflectionTestUtils.setField(googleDrive, "uploadClient", mockUploadClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        RestClient.RequestBodyUriSpec mockUploadBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockUploadReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockUploadHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockUploadResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.patch()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(new HashMap<>());

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);

        Map<String, Object> updateResponse = new HashMap<>();
        updateResponse.put("id", "file_123");
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(updateResponse);

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute
        GoogleDrive.Item result = googleDrive.updateFile(testFile, "content", "dummyToken");

        // Verify
        assertThat(result).isEqualTo(testFile);
    }

    @Test
    void testUpdateFileError() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        RestClient mockUploadClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        ReflectionTestUtils.setField(googleDrive, "uploadClient", mockUploadClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        RestClient.RequestBodyUriSpec mockUploadBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockUploadReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockUploadHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockUploadResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.patch()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(new HashMap<>());

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);

        Map<String, Object> updateResponse = new HashMap<>();
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(updateResponse);

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.updateFile(testFile, "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to update Drive file");
    }

    @Test
    void testItemUrl() {
        GoogleDrive.Item item = new GoogleDrive.Item("test_id_123", "test.yaml");
        assertThat(item.url()).isEqualTo("https://drive.google.com/file/d/test_id_123/view");
    }

    @Test
    void testListFilesSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        List<Map<String, Object>> files = List.of(
                Map.of("id", "file1", "name", "recipe1.yaml", "modifiedTime", "2023-01-01T00:00:00Z"),
                Map.of("id", "file2", "name", "recipe2.yaml", "modifiedTime", "2023-01-02T00:00:00Z")
        );
        fakeResponse.put("files", files);
        fakeResponse.put("nextPageToken", "next-page-token");
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        GoogleDrive.DriveFileListResult result = googleDrive.listFiles("dummyToken", "folder123", 10, null);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.files()).hasSize(2);
        assertThat(result.files().get(0).id()).isEqualTo("file1");
        assertThat(result.files().get(0).name()).isEqualTo("recipe1.yaml");
        assertThat(result.files().get(0).modifiedTime()).isEqualTo("2023-01-01T00:00:00Z");
        assertThat(result.nextPageToken()).isEqualTo("next-page-token");
    }

    @Test
    void testListFilesWithPageToken() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        List<Map<String, Object>> files = List.of(
                Map.of("id", "file3", "name", "recipe3.yaml", "modifiedTime", "2023-01-03T00:00:00Z")
        );
        fakeResponse.put("files", files);
        fakeResponse.put("nextPageToken", null);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        GoogleDrive.DriveFileListResult result = googleDrive.listFiles("dummyToken", "folder123", 10, "page-token-123");

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.files()).hasSize(1);
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    void testListFilesEmptyResult() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", null);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        GoogleDrive.DriveFileListResult result = googleDrive.listFiles("dummyToken", "folder123", 10, null);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.files()).isEmpty();
    }

    @Test
    void testListFilesNullResponse() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(null);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.listFiles("dummyToken", "folder123", 10, null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to list files from Drive");
    }

    @Test
    void testDownloadFileAsStringSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        String fileContent = "title: Test Recipe\nservings: 4";
        when(mockResponseSpec.body(byte[].class)).thenReturn(fileContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Execute
        String result = googleDrive.downloadFileAsString("dummyToken", "file123");

        // Verify
        assertThat(result).isEqualTo(fileContent);
    }

    @Test
    void testDownloadFileAsStringNullContent() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(byte[].class)).thenReturn(null);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.downloadFileAsString("dummyToken", "file123"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to download file from Drive: file123");
    }

    @Test
    void testDownloadFileAsStringWithUtf8() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        // Test with Russian text to verify UTF-8 encoding
        String russianContent = "title: Хачапури с сулугуни\nservings: 5";
        when(mockResponseSpec.body(byte[].class)).thenReturn(russianContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Execute
        String result = googleDrive.downloadFileAsString("dummyToken", "file123");

        // Verify - should preserve Cyrillic characters
        assertThat(result).isEqualTo(russianContent);
        assertThat(result).contains("Хачапури");
    }

    @Test
    void testDownloadFileAsBytesSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        byte[] fileContent = new byte[]{1, 2, 3, 4, 5};
        when(mockResponseSpec.body(byte[].class)).thenReturn(fileContent);

        // Execute
        byte[] result = googleDrive.downloadFileAsBytes("dummyToken", "file123");

        // Verify
        assertThat(result).isEqualTo(fileContent);
        assertThat(result).hasSize(5);
    }

    @Test
    void testDownloadFileAsBytesNullContent() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(byte[].class)).thenReturn(null);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.downloadFileAsBytes("dummyToken", "file123"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to download file from Drive: file123");
    }

    @Test
    void testGetFileMetadataSuccess() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> metadata = Map.of(
                "id", "file123",
                "name", "recipe.yaml",
                "mimeType", "application/x-yaml",
                "modifiedTime", "2023-01-01T00:00:00Z"
        );
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(metadata);

        // Execute
        GoogleDrive.DriveFileMetadata result = googleDrive.getFileMetadata("dummyToken", "file123");

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("file123");
        assertThat(result.name()).isEqualTo("recipe.yaml");
        assertThat(result.mimeType()).isEqualTo("application/x-yaml");
        assertThat(result.modifiedTime()).isEqualTo("2023-01-01T00:00:00Z");
    }

    @Test
    void testGetFileMetadataNullResponse() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(null);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.getFileMetadata("dummyToken", "file123"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to get file metadata from Drive: file123");
    }

    @Test
    void testGetFolderWithNullFiles() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", null);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("TestFolder", "dummyToken");

        // Verify
        assertThat(folder).isEmpty();
    }

    @Test
    void testGetFolderWithNullToken() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "folder_id_123")));
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute - test logging with null token
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("TestFolder", null);

        // Verify
        assertThat(folder).isPresent();
    }

    @Test
    void testGetFolderWithShortToken() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "folder_id_123")));
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute - test logging with short token
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("TestFolder", "short");

        // Verify
        assertThat(folder).isPresent();
    }

    @Test
    void testGetFileWithNullFiles() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", null);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenReturn(fakeResponse);

        // Execute
        Optional<GoogleDrive.Item> file = googleDrive.getFile("TestFile", "folder_123", "dummyToken");

        // Verify
        assertThat(file).isEmpty();
    }

    @Test
    void testCreateFolderGenericException() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new RuntimeException("Network error"));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFolder("NewFolder", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to create folder");
    }

    @Test
    void testUpdateFileGenericException() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.patch()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new RuntimeException("Network error"));

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.updateFile(testFile, "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to update file");
    }

    @Test
    void testListFilesUnauthorized() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 401 Unauthorized
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized"
        ));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.listFiles("invalidToken", "folder123", 10, null))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }

    @Test
    void testGetFileUnauthorized() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 403 Forbidden
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden"
        ));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.getFile("test.yaml", "folder_123", "invalidToken"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }

    @Test
    void testCreateFolderAuthenticationFailure() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 401 Unauthorized
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid token"
        ));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFolder("TestFolder", "invalidToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to create folder");
    }

    @Test
    void testUpdateFileAuthenticationFailure() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        RestClient.RequestBodyUriSpec mockBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec mockReqBodySpec = mock(RestClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.patch()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 401 Unauthorized
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid token"
        ));

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.updateFile(testFile, "content", "invalidToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to update file");
    }

    @Test
    void testDownloadFileAsBytesAuthenticationFailure() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 401 Unauthorized
        when(mockResponseSpec.body(byte[].class)).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid token"
        ));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.downloadFileAsBytes("invalidToken", "file123"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }

    @Test
    void testGetFileMetadataAuthenticationFailure() {
        // Setup
        RestClient driveWebClient = RestClient.builder().baseUrl("http://dummy-base").build();
        RestClient uploadWebClient = RestClient.builder().baseUrl("http://dummy-upload").build();
        GoogleDrive googleDrive = new GoogleDrive(driveWebClient, uploadWebClient);

        RestClient mockDriveClient = mock(RestClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);

        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec mockHeadersSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec mockSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);

        // Simulate 403 Forbidden
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {
        })).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden"
        ));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.getFileMetadata("invalidToken", "file123"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }
}
