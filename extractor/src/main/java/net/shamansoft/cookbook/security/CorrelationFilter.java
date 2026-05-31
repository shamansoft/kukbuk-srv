package net.shamansoft.cookbook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String SESSION_ID = "sessionId";
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    // ThreadLocal for in-process access in services (MDC is no-op with slf4j-simple)
    public static final ThreadLocal<String> SESSION = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String sessionId = request.getHeader(SESSION_ID_HEADER);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        SESSION.set(sessionId);
        MDC.put(SESSION_ID, sessionId);
        request.setAttribute(SESSION_ID, sessionId);
        try {
            chain.doFilter(request, response);
        } finally {
            SESSION.remove();
            MDC.remove(SESSION_ID);
        }
    }
}
