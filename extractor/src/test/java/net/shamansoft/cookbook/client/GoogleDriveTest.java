package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class GoogleDriveTest {

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(new HashMap<>());

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);
        
        Map<String, Object> updateResponse = new HashMap<>();
        updateResponse.put("id", "file_123");
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(updateResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(new HashMap<>());

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);
        
        Map<String, Object> updateResponse = new HashMap<>();
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(updateResponse);

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.updateFile(testFile, "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to update Drive file");
    }

    @Test
    void testCreateFileSuccess() {
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
        
        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> createResponse = new HashMap<>();
        createResponse.put("id", "new_file_123");
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(createResponse);

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);
        
        Map<String, Object> uploadResponse = new HashMap<>();
        uploadResponse.put("id", "new_file_123");
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(uploadResponse);

        // Execute
        GoogleDrive.Item result = googleDrive.createFile("test.yaml", "folder_123", "content", "dummyToken");
        
        // Verify
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("new_file_123");
        assertThat(result.name()).isEqualTo("test.yaml");
    }

    @Test
    void testCreateFileErrorOnCreate() {
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
        
        Map<String, Object> createResponse = new HashMap<>();
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(createResponse);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFile("test.yaml", "folder_123", "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to create file");
    }

    @Test
    void testCreateFileErrorOnUpload() {
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
        
        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        doReturn(mockReqBodySpec).when(mockReqBodySpec).body(any(Object.class));
        when(mockReqBodySpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> createResponse = new HashMap<>();
        createResponse.put("id", "new_file_123");
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(createResponse);

        when(mockUploadClient.patch()).thenReturn(mockUploadBodySpec);
        when(mockUploadBodySpec.uri(any(Function.class))).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockUploadReqBodySpec);
        when(mockUploadReqBodySpec.contentType(any(MediaType.class))).thenReturn(mockUploadReqBodySpec);
        doReturn(mockUploadReqBodySpec).when(mockUploadReqBodySpec).body(any(String.class));
        when(mockUploadReqBodySpec.retrieve()).thenReturn(mockUploadResponseSpec);
        
        Map<String, Object> uploadResponse = new HashMap<>();
        when(mockUploadResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(uploadResponse);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFile("test.yaml", "folder_123", "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to upload content to Drive file");
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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(null);

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
        when(mockResponseSpec.body(String.class)).thenReturn(fileContent);

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
        when(mockResponseSpec.body(String.class)).thenReturn(null);

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.downloadFileAsString("dummyToken", "file123"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to download file from Drive: file123");
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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(metadata);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(null);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenReturn(fakeResponse);

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenThrow(new RuntimeException("Network error"));

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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenThrow(new RuntimeException("Network error"));

        GoogleDrive.Item testFile = new GoogleDrive.Item("file_123", "test.yaml");

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.updateFile(testFile, "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to update file");
    }

    @Test
    void testCreateFileGenericException() {
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
        when(mockResponseSpec.body(new ParameterizedTypeReference<Map<String, Object>>() {})).thenThrow(new RuntimeException("Network error"));

        // Execute & Verify
        assertThatThrownBy(() -> googleDrive.createFile("test.yaml", "folder_123", "content", "dummyToken"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Failed to create file");
    }
}