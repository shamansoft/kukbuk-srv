package net.shamansoft.cookbook.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of("/", "/hello", "/actuator");

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> {
            if ("/actuator".equals(publicPath)) {
                return "/actuator".equals(path) || path.startsWith("/actuator/");
            }
            if ("/".equals(publicPath)) {
                return "/".equals(path);
            }
            return path.equals(publicPath) || path.startsWith(publicPath + "/");
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip auth for public endpoints
        if (isPublicPath(path)) {
            log.debug("Public endpoint, skipping auth");
            filterChain.doFilter(request, response);
            return;
        }

        // Fail securely if Firebase is not configured
        if (firebaseAuth == null) {
            log.error("FirebaseAuth not configured - rejecting request to protected endpoint: {}", path);
            response.setStatus(503); // Service Unavailable
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication service unavailable\"}");
            return;
        }

        // Extract Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No Authorization token for: {}", path);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"No authorization token\"}");
            return;
        }

        String idToken = authHeader.substring(7);

        try {
            // Verify Firebase ID token
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);

            // Store user info in request attributes
            request.setAttribute("userId", decodedToken.getUid());
            request.setAttribute("userEmail", decodedToken.getEmail());

            log.debug("Authenticated: {} ({})", decodedToken.getEmail(), decodedToken.getUid());

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (FirebaseAuthException e) {
            log.error("Token validation failed: {}", e.getMessage());
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
        }
    }
}
