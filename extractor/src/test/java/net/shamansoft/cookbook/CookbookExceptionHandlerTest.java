package net.shamansoft.cookbook;

import jakarta.servlet.http.HttpServletRequest;
import net.shamansoft.cookbook.dto.ErrorResponse;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
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
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        // Create test exception
        IOException testException = new IOException("Test error message");

        // Call exception handler
        var response = controller.handleIOException(testException, httpServletRequest);

        // Verify response status and content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 400);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "IO Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "Test error message");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/recipe");
    }

    @Test
    void handleGeneralExceptionReturnsInternalServerError() {
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        // Create test exception
        RuntimeException testException = new RuntimeException("Unexpected error");

        // Call exception handler
        var response = controller.handleGeneralException(testException, httpServletRequest);

        // Verify response status and content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 500);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Internal Server Error");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/recipe");
    }

    @Test
    void handleClientExceptionReturns500() {
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/recipe");

        // Create test exception
        RuntimeException testException = new RuntimeException("Client error");

        // Call exception handler
        var response = controller.handleGeneralException(testException, httpServletRequest);

        // Verify response status and content
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
        // Setup mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/recipe");

        // Create a runtime exception
        RuntimeException exception = new RuntimeException("Something unexpected happened");

        // Call the exception handler
        ResponseEntity<Object> response = controller.handleGeneralException(exception, mockRequest);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        Object body = response.getBody();
        assertThat(body).isInstanceOf(ErrorResponse.class);

        ErrorResponse errorResponse = (ErrorResponse) body;
        assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(errorResponse.getError()).isEqualTo("Internal Server Error");
        assertThat(errorResponse.getMessage()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(errorResponse.getPath()).isEqualTo("/recipe");
        // Error message should not expose internal details
        assertThat(errorResponse.getMessage()).doesNotContain("Something unexpected happened");
    }

    @Test
    void handleStorageNotConnectedReturnsPreconditionRequired() {
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/status");

        // Create test exception
        StorageNotConnectedException testException =
                new StorageNotConnectedException("No storage configured");

        // Call exception handler
        var response = controller.handleStorageNotConnected(testException, httpServletRequest);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 428);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Storage Not Connected");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "No storage configured");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/status");
    }

    @Test
    void handleUserNotFoundReturnsNotFound() {
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/connect");

        // Create test exception
        UserNotFoundException testException =
                new UserNotFoundException("User profile not found: test-user-123");

        // Call exception handler
        var response = controller.handleUserNotFound(testException, httpServletRequest);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 404);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "User Not Found");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("message", "User profile not found: test-user-123");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/connect");
    }

    @Test
    void handleDatabaseUnavailableReturnsServiceUnavailable() {
        // Mock HTTP request
        when(httpServletRequest.getRequestURI()).thenReturn("/v1/storage/google-drive/connect");

        // Create test exception
        DatabaseUnavailableException testException =
                new DatabaseUnavailableException("Firestore connection timeout");

        // Call exception handler
        var response = controller.handleDatabaseUnavailable(testException, httpServletRequest);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("status", 503);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("error", "Service Unavailable");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("path", "/v1/storage/google-drive/connect");
        // Error message should be generic to not expose internal details
        assertThat(response.getBody()).hasFieldOrPropertyWithValue(
                "message",
                "Database temporarily unavailable. Please try again later."
        );
    }
}
