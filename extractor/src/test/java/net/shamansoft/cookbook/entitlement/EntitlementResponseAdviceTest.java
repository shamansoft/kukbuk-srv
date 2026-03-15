package net.shamansoft.cookbook.entitlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementResponseAdviceTest {

    private EntitlementResponseAdvice advice;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ServletServerHttpRequest serverRequest;
    private ServletServerHttpResponse serverResponse;

    private static final Instant RESET_AT = Instant.parse("2026-03-09T00:00:00Z");

    @BeforeEach
    void setUp() {
        advice = new EntitlementResponseAdvice();
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        serverRequest = new ServletServerHttpRequest(mockRequest);
        serverResponse = new ServletServerHttpResponse(mockResponse);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void supports_returnsTrue() {
        assertThat(advice.supports(null, null)).isTrue();
    }

    @Test
    void beforeBodyWrite_noAttribute_noHeadersAdded() {
        Object body = new Object();

        Object result = advice.beforeBodyWrite(body, null, null, null, serverRequest, serverResponse);

        assertThat(result).isSameAs(body);
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_OUTCOME)).isNull();
    }

    @Test
    void beforeBodyWrite_noRequestAttributes_returnsBodyUnmodified() {
        RequestContextHolder.resetRequestAttributes();
        Object body = new Object();

        Object result = advice.beforeBodyWrite(body, null, null, null, serverRequest, serverResponse);

        assertThat(result).isSameAs(body);
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_OUTCOME)).isNull();
    }

    @Test
    void beforeBodyWrite_allowedFreeQuota_addsAllHeaders() {
        EntitlementResult result = new EntitlementResult(
                true, EntitlementOutcome.ALLOWED_FREE_QUOTA, 3, null, RESET_AT);
        mockRequest.setAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR, result);

        advice.beforeBodyWrite(new Object(), null, null, null, serverRequest, serverResponse);

        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_OUTCOME))
                .isEqualTo("ALLOWED_FREE_QUOTA");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_REMAINING))
                .isEqualTo("3");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_RESETS_AT))
                .isEqualTo(RESET_AT.toString());
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_CREDITS_REMAINING))
                .isNull();
    }

    @Test
    void beforeBodyWrite_circuitOpen_includesQuotaRemainingMinus1() {
        EntitlementResult result = EntitlementResult.circuitOpen();
        mockRequest.setAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR, result);

        advice.beforeBodyWrite(new Object(), null, null, null, serverRequest, serverResponse);

        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_OUTCOME))
                .isEqualTo("CIRCUIT_OPEN");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_REMAINING))
                .isEqualTo("-1");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_RESETS_AT))
                .isNull();
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_CREDITS_REMAINING))
                .isNull();
    }

    @Test
    void beforeBodyWrite_paidUser_noCreditsHeader() {
        EntitlementResult result = EntitlementResult.paid();
        mockRequest.setAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR, result);

        advice.beforeBodyWrite(new Object(), null, null, null, serverRequest, serverResponse);

        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_OUTCOME))
                .isEqualTo("ALLOWED_PAID");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_CREDITS_REMAINING))
                .isNull();
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_RESETS_AT))
                .isNull();
    }

    @Test
    void beforeBodyWrite_allowedCredit_includesCreditsHeader() {
        EntitlementResult result = new EntitlementResult(
                true, EntitlementOutcome.ALLOWED_CREDIT, 0, 2, RESET_AT);
        mockRequest.setAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR, result);

        advice.beforeBodyWrite(new Object(), null, null, null, serverRequest, serverResponse);

        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_CREDITS_REMAINING))
                .isEqualTo("2");
        assertThat(serverResponse.getHeaders().getFirst(EntitlementResponseAdvice.HEADER_QUOTA_RESETS_AT))
                .isEqualTo(RESET_AT.toString());
    }

    @Test
    void beforeBodyWrite_returnsOriginalBody() {
        String body = "response-body";
        EntitlementResult result = EntitlementResult.paid();
        mockRequest.setAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR, result);

        Object returned = advice.beforeBodyWrite(body, null, null, null, serverRequest, serverResponse);

        assertThat(returned).isSameAs(body);
    }
}
