package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleDriveRestServiceTest {
    private GoogleDriveRestService service;
    private WebClient mockDriveClient;
    private WebClient mockUploadClient;

    @BeforeEach
    void setUp() {
        mockDriveClient = mock(WebClient.class);
        mockUploadClient = mock(WebClient.class);
        service = new GoogleDriveRestService("testfolder", mockDriveClient, mockUploadClient);
    }

    @Test
    void generateFileName_various() {
        assertThat(service.generateFileName("My Recipe Title")).isEqualTo("my-recipe-title.yaml");
        assertThat(service.generateFileName("   Spicy! & Sweet  ")).isEqualTo("spicy-sweet.yaml");
        assertThat(service.generateFileName("")).startsWith("recipe-").endsWith(".yaml");
        assertThat(service.generateFileName(null)).startsWith("recipe-").endsWith(".yaml");
    }

    @Test
    void getOrCreateFolder_existing() {
        // Mock drive files list with existing folder
        @SuppressWarnings("rawtypes") WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes") WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("rawtypes") WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(mockDriveClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
        when(headersSpec.header(eq("Authorization"), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        Map<String, Object> listBody = Map.of("files", List.of(Map.of("id", "folder123")));
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(listBody));

        String folderId = service.getOrCreateFolder("token");
        assertThat(folderId).isEqualTo("folder123");
    }

    @Test
    void uploadRecipeYaml_createNew() {
        // Mock drive list with no existing files
        @SuppressWarnings("rawtypes") WebClient.RequestHeadersUriSpec listUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes") WebClient.RequestHeadersSpec listHeaders = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("rawtypes") WebClient.ResponseSpec listResponse = mock(WebClient.ResponseSpec.class);
        when(mockDriveClient.get()).thenReturn(listUriSpec);
        when(listUriSpec.uri(any(Function.class))).thenReturn(listHeaders);
        when(listHeaders.header(eq("Authorization"), anyString())).thenReturn(listHeaders);
        when(listHeaders.retrieve()).thenReturn(listResponse);
        when(listResponse.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("files", List.of())));

        // Mock upload client for create
        @SuppressWarnings("rawtypes") WebClient.RequestBodyUriSpec uploadUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes") WebClient.RequestBodySpec uploadBodySpec = mock(WebClient.RequestBodySpec.class);
        @SuppressWarnings("rawtypes") WebClient.RequestHeadersSpec uploadHeaders = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("rawtypes") WebClient.ResponseSpec uploadResponse = mock(WebClient.ResponseSpec.class);
        when(mockUploadClient.post()).thenReturn(uploadUriSpec);
        when(uploadUriSpec.uri(any(Function.class))).thenReturn(uploadBodySpec);
        when(uploadBodySpec.header(eq("Authorization"), anyString())).thenReturn(uploadBodySpec);
        when(uploadBodySpec.contentType(any())).thenReturn(uploadBodySpec);
        when(uploadBodySpec.bodyValue(anyString())).thenReturn(uploadHeaders);
        when(uploadHeaders.retrieve()).thenReturn(uploadResponse);
        when(uploadResponse.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("id", "file456")));

        DriveService.UploadResult result = service.uploadRecipeYaml("token", "folder123", "file.yaml", "content");
        assertThat(result.fileId()).isEqualTo("file456");
    }
}