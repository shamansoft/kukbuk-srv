package net.shamansoft.cookbook.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.entitlement.UserTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/hello",
            "/actuator",
            "/debug"  // Debug/test endpoints (only active in non-prod profiles)
    );
    // Registration saves the profile before the user can click the verification link,
    // and login needs it for routing. Everything else requires a verified email.
    private static final String UNVERIFIED_ALLOWED_PATH = "/v1/user/profile";
    private final FirebaseAuth firebaseAuth;
    // Test accounts: these addresses have no inbox, so they can never verify.
    // Lowercased, starts with "@"; null when not configured (no bypass)
    private final String testEmailSuffix;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth,
                              @Value("${cookbook.auth.test-email-suffix:}") String testEmailSuffix) {
        this.firebaseAuth = firebaseAuth;
        this.testEmailSuffix = normalizeSuffix(testEmailSuffix);
    }

    // Leading "@" is required so "@test.example.com" can't match "qa@nottest.example.com"
    private static String normalizeSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return null;
        }
        String s = suffix.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("@") ? s : "@" + s;
    }

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

    private boolean isTestEmail(String email) {
        return testEmailSuffix != null
                && email != null
                && email.toLowerCase(Locale.ROOT).endsWith(testEmailSuffix);
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

            // The claim is fixed when the token is issued, so clients must force a
            // token refresh after the user verifies, then retry
            if (!decodedToken.isEmailVerified()
                    && !isTestEmail(decodedToken.getEmail())
                    && !UNVERIFIED_ALLOWED_PATH.equals(path)) {
                log.warn("Rejected unverified email: userId={}, path={}", decodedToken.getUid(), path);
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"email_not_verified\"}");
                return;
            }

            // Store user info in request attributes
            request.setAttribute("userId", decodedToken.getUid());
            request.setAttribute("userEmail", decodedToken.getEmail());

            // Extract tier claim from JWT (optional)
            Map<String, Object> claims = decodedToken.getClaims();
            Object tierClaim = claims != null ? claims.get("tier") : null;
            if (tierClaim != null) {
                try {
                    UserTier userTier = UserTier.valueOf(tierClaim.toString());
                    request.setAttribute("userTier", userTier);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown tier claim '{}' for userId={}, ignoring",
                            tierClaim, decodedToken.getUid());
                }
            }

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
