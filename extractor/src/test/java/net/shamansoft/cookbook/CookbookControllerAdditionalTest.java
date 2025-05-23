package net.shamansoft.cookbook;

import jakarta.servlet.http.HttpServletRequest;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookbookControllerAdditionalTest {

    @Mock
    private RawContentService rawContentService;
    @Mock
    private Transformer transformer;
    @Mock
    private Compressor compressor;
    @Mock
    private DriveService driveService;
    @Mock
    private TokenService tokenService;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private CookbookController controller;

    private static final String TITLE = "New Recipe";
    private static final String URL = "http://example.org";
    private static final String COMPRESSED_HTML = "dummy-compressed";
    private static final String RAW_HTML = "<html><body>Content</body></html>";
    private static final String TRANSFORMED = "transformed content";
    private static final String AUTH_TOKEN = "valid-token";

    @Test
    void createRecipeFallsBackToUrlWhenHtmlIsMissing() throws IOException, AuthenticationException {
        // Set up request with URL but no HTML content
        Request request = new Request(null, TITLE, URL);
        when(tokenService.getAuthToken(any(HttpHeaders.class))).thenReturn(AUTH_TOKEN);
        // Mock fetch from URL when HTML is missing
        when(rawContentService.fetch(URL)).thenReturn(RAW_HTML);
        when(transformer.transform(RAW_HTML)).thenReturn(new Transformer.Response(true, TRANSFORMED));

        // Mock token flow not used in this scenario
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", AUTH_TOKEN);
        when(driveService.getOrCreateFolder(AUTH_TOKEN)).thenReturn("folder123");
        when(driveService.generateFileName(TITLE)).thenReturn("file.yaml");
        when(driveService.uploadRecipeYaml(AUTH_TOKEN, "folder123", "file.yaml", TRANSFORMED))
                .thenReturn(new DriveService.UploadResult("file123", "http://drive.example.com/file123"));

        // Call controller method
        RecipeResponse response = controller.createRecipe(request, null, false, headers);

        // Verify response
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.driveFileId()).isEqualTo("file123");
        assertThat(response.driveFileUrl()).isEqualTo("http://drive.example.com/file123");

        // Verify interactions
        verify(rawContentService, times(1)).fetch(URL);
        verify(compressor, never()).decompress(any());
    }

    @Test
    void createRecipeUsesHtmlAndBypassesDecompressionWhenCompressionIsNone() throws IOException, AuthenticationException {
        // Set up request with HTML content and "none" compression
        Request request = new Request(RAW_HTML, TITLE, URL);
        when(tokenService.getAuthToken(any(HttpHeaders.class))).thenReturn(AUTH_TOKEN);
        // Mock transformation
        when(transformer.transform(RAW_HTML)).thenReturn(new Transformer.Response(true, TRANSFORMED));

        // Mock token processing
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", AUTH_TOKEN);
        when(driveService.getOrCreateFolder(AUTH_TOKEN)).thenReturn("folder123");
        when(driveService.generateFileName(TITLE)).thenReturn("file.yaml");
        when(driveService.uploadRecipeYaml(AUTH_TOKEN, "folder123", "file.yaml", TRANSFORMED))
                .thenReturn(new DriveService.UploadResult("file123", "http://drive.example.com/file123"));

        // Call controller method with "none" compression
        RecipeResponse response = controller.createRecipe(request, "none", false, headers);

        // Verify response
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.driveFileId()).isEqualTo("file123");

        // Verify that decompression was never called
        verify(compressor, never()).decompress(any());
        verify(rawContentService, never()).fetch(any());
    }

    @Test
    void createRecipePropagatesIOExceptionFromDecompression() throws IOException {
        // Set up request with compressed HTML
        Request request = new Request(COMPRESSED_HTML, TITLE, URL);

        // Mock decompression to throw exception
        when(compressor.decompress(COMPRESSED_HTML)).thenThrow(new IOException("Decompression error"));

        // Set up mock headers
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", AUTH_TOKEN);

        // Assert that exception is thrown when called
        assertThatThrownBy(() -> controller.createRecipe(request, null, false, headers))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompression error");

        // Verify no URL fetch was attempted
        verify(rawContentService, never()).fetch(any());
    }

    @Test
    void createRecipeThrowsUnauthorizedWhenTokenIsInvalid() throws AuthenticationException {
        // Set up request
        Request request = new Request(RAW_HTML, TITLE, URL);

        // Mock token verification to fail
        Map<String, String> headers = Map.of("X-S-AUTH-TOKEN", AUTH_TOKEN);
        when(tokenService.getAuthToken(any(HttpHeaders.class))).thenThrow(new AuthenticationException("Invalid auth token"));

        // Assert that unauthorized exception is thrown
        assertThatThrownBy(() -> controller.createRecipe(request, "none", false, headers))
                .isInstanceOf(AuthenticationException.class);

        // Verify token service was called but drive service was not
        verify(driveService, never()).getOrCreateFolder(any());
        verify(transformer, never()).transform(any());
    }

    @Test
    void testExtractHtmlWithCompression() throws Exception {
        // Get private method using reflection
        Method extractHtmlMethod = CookbookController.class.getDeclaredMethod(
                "extractHtml", Request.class, String.class);
        extractHtmlMethod.setAccessible(true);

        // Set up request with compressed HTML
        Request request = new Request(COMPRESSED_HTML, TITLE, URL);

        // Mock decompression to return raw HTML
        when(compressor.decompress(COMPRESSED_HTML)).thenReturn(RAW_HTML);

        // Call private method via reflection
        String html = (String) extractHtmlMethod.invoke(controller, request, null);

        // Verify result
        assertThat(html).isEqualTo(RAW_HTML);

        // Verify compressor was called with correct argument
        verify(compressor).decompress(COMPRESSED_HTML);
        verify(rawContentService, never()).fetch(any());
    }

    @Test
    void testExtractHtmlWithNoneCompression() throws Exception {
        // Get private method using reflection
        Method extractHtmlMethod = CookbookController.class.getDeclaredMethod(
                "extractHtml", Request.class, String.class);
        extractHtmlMethod.setAccessible(true);

        // Set up request with HTML
        Request request = new Request(RAW_HTML, TITLE, URL);

        // Call private method via reflection with "none" compression
        String html = (String) extractHtmlMethod.invoke(controller, request, "none");

        // Verify result
        assertThat(html).isEqualTo(RAW_HTML);

        // Verify compressor was never called
        verify(compressor, never()).decompress(any());
        verify(rawContentService, never()).fetch(any());
    }

    @Test
    void testExtractHtmlFallsBackToUrl() throws Exception {
        // Get private method using reflection
        Method extractHtmlMethod = CookbookController.class.getDeclaredMethod(
                "extractHtml", Request.class, String.class);
        extractHtmlMethod.setAccessible(true);

        // Set up request with only URL, no HTML
        Request request = new Request(null, TITLE, URL);

        // Mock fetch from URL
        when(rawContentService.fetch(URL)).thenReturn(RAW_HTML);

        // Call private method via reflection
        String html = (String) extractHtmlMethod.invoke(controller, request, null);

        // Verify result
        assertThat(html).isEqualTo(RAW_HTML);

        // Verify rawContentService was called with correct URL
        verify(rawContentService).fetch(URL);
        verify(compressor, never()).decompress(any());
    }
}