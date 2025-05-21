package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleDriveTest {

    @Test
    void testGetFolderFound() {
        // Setup
        GoogleDrive googleDrive = new GoogleDrive("http://dummy-upload", "http://dummy-base");
        
        WebClient mockDriveClient = mock(WebClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        
        WebClient.RequestHeadersUriSpec mockHeadersSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "folder_id_123")));
        when(mockResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(fakeResponse));

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
        GoogleDrive googleDrive = new GoogleDrive("http://dummy-upload", "http://dummy-base");
        
        WebClient mockDriveClient = mock(WebClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        
        WebClient.RequestHeadersUriSpec mockHeadersSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", Collections.emptyList());
        when(mockResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(fakeResponse));

        // Execute
        Optional<GoogleDrive.Item> folder = googleDrive.getFolder("NonExistentFolder", "dummyToken");
        
        // Verify
        assertThat(folder).isEmpty();
    }

    @Test
    void testCreateFolderSuccess() {
        // Setup
        GoogleDrive googleDrive = new GoogleDrive("http://dummy-upload", "http://dummy-base");
        
        WebClient mockDriveClient = mock(WebClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        
        WebClient.RequestBodyUriSpec mockBodySpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec mockReqBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(mockDriveClient.post()).thenReturn(mockBodySpec);
        when(mockBodySpec.uri(any(Function.class))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.header(eq("Authorization"), anyString())).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(mockReqBodySpec);
        when(mockReqBodySpec.bodyValue(any())).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("id", "new_folder_123");
        when(mockResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(fakeResponse));

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
        GoogleDrive googleDrive = new GoogleDrive("http://dummy-upload", "http://dummy-base");
        
        WebClient mockDriveClient = mock(WebClient.class);
        ReflectionTestUtils.setField(googleDrive, "driveClient", mockDriveClient);
        
        WebClient.RequestHeadersUriSpec mockHeadersSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);
        
        when(mockDriveClient.get()).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.uri(any(Function.class))).thenReturn(mockSpec);
        when(mockSpec.header(eq("Authorization"), anyString())).thenReturn(mockSpec);
        when(mockSpec.retrieve()).thenReturn(mockResponseSpec);
        
        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("files", List.of(Map.of("id", "file_456")));
        when(mockResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(fakeResponse));

        // Execute
        Optional<GoogleDrive.Item> file = googleDrive.getFile("TestFile", "folder_123", "dummyToken");
        
        // Verify
        assertThat(file).isPresent();
        assertThat(file.get().id()).isEqualTo("file_456");
    }
}