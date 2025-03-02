package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookbookControllerTest {

    @Mock
    private RawContentService rawContentService;

    @Mock
    private Transformer transformer;

    @Mock
    private Compressor compressor;

    @InjectMocks
    private CookbookController controller;

    @Test
    void healthEndpointReturnsOk() {
        assertThat(controller.gcpHealth()).isEqualTo("OK");
    }

    @Test
    void helloEndpointReturnsGreetingWithName() {
        assertThat(controller.index("John")).isEqualTo("Hello, Cookbook user John!");
    }

    @Test
    void createRecipeFromHtml() throws IOException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        RecipeResponse response = controller.createRecipe(request, null, false);

        assertThat(response)
            .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::content)
            .containsExactly("Title", "http://example.com", "transformed content");
    }

    @Test
    void createRecipeFromUrl() throws IOException {
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        RecipeResponse response = controller.createRecipe(request, null, false);

        assertThat(response)
            .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::content)
            .containsExactly("Title", "http://example.com", "transformed content");
    }

    @Test
    void createRecipeWithDebugIncludesRawHtml() throws IOException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        RecipeResponse response = controller.createRecipe(request, null, true);

        assertThat(response.raw()).isEqualTo("raw html");
    }

    @Test
    void createRecipeWithNoCompressionSkipsDecompression() throws IOException {
        Request request = new Request("raw html", "Title", "http://example.com");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        RecipeResponse response = controller.createRecipe(request, "none", false);

        verify(compressor, never()).decompress(any());
        assertThat(response.content()).isEqualTo("transformed content");
    }

    @Test
    void ioExceptionFromDecompressionReturnsBadRequest() throws IOException {

        ResponseEntity<String> response = controller.handleIOException(new IOException("Decompression failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Decompression failed");
    }

    @Test
    void validationExceptionReturnsBadRequest() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = mock(FieldError.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        when(fieldError.getDefaultMessage()).thenReturn("Title is required");

        ResponseEntity<String> response = controller.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Validation error: Title is required");
    }
}