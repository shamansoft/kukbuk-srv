package net.shamansoft.cookbook.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthFilterTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FirebaseToken firebaseToken;

    private FirebaseAuthFilter filter;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() {
        filter = new FirebaseAuthFilter(firebaseAuth);
        responseWriter = new StringWriter();
    }

    private void setupResponseWriter() throws IOException {
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void testExtendsOncePerRequestFilter() {
        // Verify that FirebaseAuthFilter extends OncePerRequestFilter
        assertTrue(filter instanceof org.springframework.web.filter.OncePerRequestFilter,
                "FirebaseAuthFilter should extend OncePerRequestFilter");
    }

    @Test
    void testPublicPath_RootPath_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: request to root path
        when(request.getRequestURI()).thenReturn("/");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through without auth check
        verify(filterChain).doFilter(request, response);
        verify(firebaseAuth, never()).verifyIdToken(anyString());
    }

    @Test
    void testPublicPath_ActuatorPath_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: request to actuator path
        when(request.getRequestURI()).thenReturn("/actuator");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through without auth check
        verify(filterChain).doFilter(request, response);
        verify(firebaseAuth, never()).verifyIdToken(anyString());
    }

    @Test
    void testPublicPath_ActuatorHealthPath_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: request to actuator health endpoint
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through without auth check
        verify(filterChain).doFilter(request, response);
        verify(firebaseAuth, never()).verifyIdToken(anyString());
    }

    @Test
    void testPublicPath_ActuatorInfoPath_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: request to actuator info endpoint
        when(request.getRequestURI()).thenReturn("/actuator/info");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through without auth check
        verify(filterChain).doFilter(request, response);
        verify(firebaseAuth, never()).verifyIdToken(anyString());
    }

    @Test
    void testPublicPath_HelloPath_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: request to hello path
        when(request.getRequestURI()).thenReturn("/hello");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through without auth check
        verify(filterChain).doFilter(request, response);
        verify(firebaseAuth, never()).verifyIdToken(anyString());
    }

    @Test
    void testProtectedPath_NoAuthHeader_Returns401() throws ServletException, IOException {
        // Given: protected path without auth header
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn(null);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("No authorization token"));
    }

    @Test
    void testProtectedPath_InvalidAuthHeader_Returns401() throws ServletException, IOException {
        // Given: protected path with invalid auth header (not Bearer)
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("No authorization token"));
    }

    @Test
    void testProtectedPath_ValidToken_AllowsAccess() throws ServletException, IOException, FirebaseAuthException {
        // Given: protected path with valid token
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-123");
        when(firebaseToken.getUid()).thenReturn("user-123");
        when(firebaseToken.getEmail()).thenReturn("user@example.com");
        when(firebaseAuth.verifyIdToken("valid-token-123")).thenReturn(firebaseToken);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request passes through and user info is set
        verify(firebaseAuth).verifyIdToken("valid-token-123");
        verify(request).setAttribute("userId", "user-123");
        verify(request).setAttribute("userEmail", "user@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testProtectedPath_InvalidToken_Returns401() throws ServletException, IOException, FirebaseAuthException {
        // Given: protected path with invalid token
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        FirebaseAuthException exception = mock(FirebaseAuthException.class);
        when(exception.getMessage()).thenReturn("Invalid token");
        when(firebaseAuth.verifyIdToken("invalid-token")).thenThrow(exception);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("Invalid token"));
    }

    @Test
    void testProtectedPath_ExpiredToken_Returns401() throws ServletException, IOException, FirebaseAuthException {
        // Given: protected path with expired token
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        FirebaseAuthException exception = mock(FirebaseAuthException.class);
        when(exception.getMessage()).thenReturn("Token expired");
        when(firebaseAuth.verifyIdToken("expired-token")).thenThrow(exception);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("Invalid token"));
    }

    @Test
    void testProtectedPath_FirebaseAuthNotConfigured_Returns503() throws ServletException, IOException {
        // Given: FirebaseAuth is null (not configured)
        setupResponseWriter();
        filter = new FirebaseAuthFilter(null);
        when(request.getRequestURI()).thenReturn("/v1/recipes");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 503 Service Unavailable
        verify(response).setStatus(503);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("Authentication service unavailable"));
    }

    @Test
    void testProtectedPath_MultipleRequests_EachValidated() throws ServletException, IOException, FirebaseAuthException {
        // Given: multiple requests to protected path
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes/123");
        when(request.getHeader("Authorization")).thenReturn("Bearer token-1");
        when(firebaseToken.getUid()).thenReturn("user-1");
        when(firebaseToken.getEmail()).thenReturn("user1@example.com");
        when(firebaseAuth.verifyIdToken("token-1")).thenReturn(firebaseToken);

        // When: first request
        filter.doFilterInternal(request, response, filterChain);

        // Then: first request passes
        verify(firebaseAuth).verifyIdToken("token-1");
        verify(filterChain).doFilter(request, response);

        // Given: second request with different token
        reset(request, response, filterChain, firebaseAuth, firebaseToken);
        when(request.getRequestURI()).thenReturn("/v1/recipes/456");
        when(request.getHeader("Authorization")).thenReturn("Bearer token-2");
        when(firebaseToken.getUid()).thenReturn("user-2");
        when(firebaseToken.getEmail()).thenReturn("user2@example.com");
        when(firebaseAuth.verifyIdToken("token-2")).thenReturn(firebaseToken);

        // When: second request
        filter.doFilterInternal(request, response, filterChain);

        // Then: second request also passes with its own token
        verify(firebaseAuth).verifyIdToken("token-2");
        verify(request).setAttribute("userId", "user-2");
        verify(request).setAttribute("userEmail", "user2@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testProtectedPath_TokenWithoutBearerPrefix_Returns401() throws ServletException, IOException {
        // Given: auth header without "Bearer " prefix
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("token-without-bearer");

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("No authorization token"));
    }

    @Test
    void testProtectedPath_EmptyBearerToken_Returns401() throws ServletException, IOException, FirebaseAuthException {
        // Given: empty Bearer token
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        FirebaseAuthException exception = mock(FirebaseAuthException.class);
        when(exception.getMessage()).thenReturn("Empty token");
        when(firebaseAuth.verifyIdToken("")).thenThrow(exception);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: returns 401 Unauthorized
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(responseWriter.toString().contains("Invalid token"));
    }

    @Test
    void testProtectedPath_ValidTokenWithUserInfo_StoresAttributes() throws ServletException, IOException, FirebaseAuthException {
        // Given: valid token with user information
        when(request.getRequestURI()).thenReturn("/v1/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(firebaseToken.getUid()).thenReturn("abc123");
        when(firebaseToken.getEmail()).thenReturn("test@example.com");
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(firebaseToken);

        // When: filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: user attributes are stored in request
        verify(request).setAttribute("userId", "abc123");
        verify(request).setAttribute("userEmail", "test@example.com");
        verify(filterChain).doFilter(request, response);
    }
}
