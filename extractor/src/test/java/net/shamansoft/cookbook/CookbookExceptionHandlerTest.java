package net.shamansoft.cookbook;

import jakarta.servlet.http.HttpServletRequest;
import net.shamansoft.cookbook.dto.ErrorResponse;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.GoogleDriveException;
import net.shamansoft.cookbook.exception.InvalidRecipeFormatException;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.exception.UrlFetchException;
import net.shamansoft.cookbook.exception.UserNotFoundException;
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

import javax.naming.AuthenticationException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CookbookExceptionHandlerTest {

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private CookbookExceptionHandler controller;

    @Test
    void handleIOExceptionReturnsProperResponse() {
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        IOException testException = new IOException("Test error message");

        var response = controller.handleIOException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 400);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "IO Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "Test error message");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/recipe");
    }

    @Test
    void handleGeneralExceptionReturnsInternalServerError() {
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        RuntimeException testException = new RuntimeException("Unexpected error");

        var response = controller.handleGeneralException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 500);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Internal Server Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/recipe");
    }

    @Test
    void handleClientExceptionReturns500() {
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        RuntimeException testException = new RuntimeException("Client error");

        var response = controller.handleGeneralException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 500);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Internal Server Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/recipe");
    }

    @Test
    void ioExceptionFromDecompressionReturnsBadRequest() throws IOException {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/recipe");

        ResponseEntity<Object> response = controller.handleIOException(
                new IOException("Decompression failed"), mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);

        ErrorResponse errorResponse = (ErrorResponse) body;
        assertThat(errorResponse.getMessage()).isEqualTo("Decompression failed");
        assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(errorResponse.getError()).isEqualTo("IO Error");
        assertThat(errorResponse.getPath()).isEqualTo("/recipe");
    }

    @Test
    void validationExceptionReturnsBadRequest() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = mock(FieldError.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.Collections.singletonList(fieldError));
        when(fieldError.getDefaultMessage()).thenReturn("Title is required");
        when(fieldError.getField()).thenReturn("title");

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/recipe");

        ResponseEntity<Object> response = controller.handleValidationException(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);

        ErrorResponse errorResponse = (ErrorResponse) body;
        assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(errorResponse.getError()).isEqualTo("Validation Error");
        assertThat(errorResponse.getPath()).isEqualTo("/recipe");

        assertThat(errorResponse.getValidationErrors()).hasSize(1);
        ErrorResponse.ValidationError validationError = errorResponse.getValidationErrors().get(0);
        assertThat(validationError.getField()).isEqualTo("title");
        assertThat(validationError.getMessage()).isEqualTo("Title is required");
    }

    @Test
    void generalExceptionHandlerReturnsInternalServerError() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/recipe");

        RuntimeException exception = new RuntimeException("Something unexpected happened");

        ResponseEntity<Object> response = controller.handleGeneralException(exception, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);

        ErrorResponse errorResponse = (ErrorResponse) body;
        assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(errorResponse.getError()).isEqualTo("Internal Server Error");
        assertThat(errorResponse.getMessage()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(errorResponse.getPath()).isEqualTo("/recipe");
        assertThat(errorResponse.getMessage()).doesNotContain("Something unexpected happened");
    }

    @Test
    void handleStorageNotConnectedReturnsPreconditionRequired() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/status");

        StorageNotConnectedException testException =
                new StorageNotConnectedException("No storage configured");

        var response = controller.handleStorageNotConnected(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 428);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Storage Not Connected");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "No storage configured");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/status");
    }

    @Test
    void handleUserNotFoundReturnsNotFound() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/connect");

        UserNotFoundException testException =
                new UserNotFoundException("User profile not found: test-user-123");

        var response = controller.handleUserNotFound(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 404);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "User Not Found");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "User profile not found: test-user-123");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/connect");
    }

    @Test
    void handleDatabaseUnavailableReturnsServiceUnavailable() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/connect");

        DatabaseUnavailableException testException =
                new DatabaseUnavailableException("Firestore connection timeout");

        var response = controller.handleDatabaseUnavailable(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 503);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Service Unavailable");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/connect");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue(
                "message",
                "Database temporarily unavailable. Please try again later."
        );
    }

    @Test
    void handleUrlFetchExceptionReturnsUnprocessableEntity() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/recipes");

        UrlFetchException testException = new UrlFetchException("https://example.com", 403);

        var response = controller.handleUrlFetchException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 422);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "URL Not Accessible");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", testException.getMessage());
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/recipes");
    }

    @Test
    void handleAuthExceptionReturnsUnauthorized() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/recipes");

        AuthenticationException testException = new AuthenticationException("Invalid or expired token");

        var response = controller.handleAuthException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 401);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Authentication Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "Invalid or expired token");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/recipes");
    }

    @Test
    void handleRecipeNotFoundReturnsNotFound() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/recipes/file-abc-123");

        RecipeNotFoundException testException = new RecipeNotFoundException("Recipe not found: file-abc-123");

        var response = controller.handleRecipeNotFound(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 404);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Recipe Not Found");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "Recipe not found: file-abc-123");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/recipes/file-abc-123");
    }

    @Test
    void handleInvalidRecipeFormatReturnsUnprocessableEntity() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/recipes/file-abc-123");

        InvalidRecipeFormatException testException =
                new InvalidRecipeFormatException("Unexpected YAML token at line 5");

        var response = controller.handleInvalidRecipeFormat(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 422);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Invalid Recipe Format");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/recipes/file-abc-123");
        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) body).getMessage()).contains("Unexpected YAML token at line 5");
    }

    @Test
    void handleGoogleDriveExceptionReturnsBadGateway() {
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/recipes");

        GoogleDriveException testException = new GoogleDriveException("Drive API quota exceeded");

        var response = controller.handleGoogleDriveException(testException, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 502);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Google Drive Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/recipes");
        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) body).getMessage()).contains("Drive API quota exceeded");
    }
}