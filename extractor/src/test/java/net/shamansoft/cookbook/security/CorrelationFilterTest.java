package net.shamansoft.cookbook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CorrelationFilterTest {

    @Mock
    private FilterChain filterChain;

    private CorrelationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationFilter();
        CorrelationFilter.SESSION.remove();
    }

    @Test
    void usesSessionIdFromHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationFilter.SESSION_ID_HEADER, "client-provided-id");

        AtomicReference<String> sessionDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            sessionDuringChain.set(CorrelationFilter.SESSION.get());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(sessionDuringChain.get()).isEqualTo("client-provided-id");
        assertThat(request.getAttribute(CorrelationFilter.SESSION_ID)).isEqualTo("client-provided-id");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void generatesUuidWhenHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AtomicReference<String> sessionDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            sessionDuringChain.set(CorrelationFilter.SESSION.get());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        String generated = sessionDuringChain.get();
        assertThat(generated).isNotNull().isNotBlank();
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(request.getAttribute(CorrelationFilter.SESSION_ID)).isEqualTo(generated);
    }

    @Test
    void generatesUuidWhenHeaderIsBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationFilter.SESSION_ID_HEADER, "   ");

        AtomicReference<String> sessionDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            sessionDuringChain.set(CorrelationFilter.SESSION.get());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(UUID.fromString(sessionDuringChain.get())).isNotNull();
    }

    @Test
    void clearsSessionAfterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationFilter.SESSION_ID_HEADER, "some-id");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(CorrelationFilter.SESSION.get()).isNull();
    }

    @Test
    void clearsSessionEvenWhenChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        doAnswer(inv -> { throw new ServletException("boom"); })
                .when(filterChain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        } catch (ServletException ignored) {
        }

        assertThat(CorrelationFilter.SESSION.get()).isNull();
    }

    @Test
    void requestAttributeMatchesSessionThreadLocal() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AtomicReference<String> sessionDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            sessionDuringChain.set(CorrelationFilter.SESSION.get());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(request.getAttribute(CorrelationFilter.SESSION_ID))
                .isEqualTo(sessionDuringChain.get());
    }
}
