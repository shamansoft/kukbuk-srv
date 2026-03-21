package net.shamansoft.cookbook.entitlement;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Appends entitlement quota headers to every successful response after an
 * {@link EntitlementAspect} check. Reads the {@code entitlementResult} request
 * attribute set by the aspect and writes:
 * <ul>
 *   <li>{@code X-Quota-Outcome} — always present when attribute is set</li>
 *   <li>{@code X-Quota-Remaining} — always present (−1 for paid/circuit-open)</li>
 *   <li>{@code X-Credits-Remaining} — omitted when {@code remainingCredits} is null (paid users)</li>
 *   <li>{@code X-Quota-Resets-At} — omitted when {@code resetsAt} is null</li>
 * </ul>
 * No-ops when the request attribute is absent (unannotated endpoints).
 */
@ControllerAdvice
public class EntitlementResponseAdvice implements ResponseBodyAdvice<Object> {

    static final String HEADER_QUOTA_OUTCOME = "X-Quota-Outcome";
    static final String HEADER_QUOTA_REMAINING = "X-Quota-Remaining";
    static final String HEADER_CREDITS_REMAINING = "X-Credits-Remaining";
    static final String HEADER_QUOTA_RESETS_AT = "X-Quota-Resets-At";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        RequestAttributes requestAttrs = RequestContextHolder.getRequestAttributes();
        if (requestAttrs == null) {
            return body;
        }

        EntitlementResult result = (EntitlementResult) requestAttrs.getAttribute(
                EntitlementAspect.ENTITLEMENT_RESULT_ATTR, RequestAttributes.SCOPE_REQUEST);
        if (result == null) {
            return body;
        }

        response.getHeaders().add(HEADER_QUOTA_OUTCOME, result.outcome().name());
        response.getHeaders().add(HEADER_QUOTA_REMAINING, String.valueOf(result.remainingQuota()));

        if (result.remainingCredits() != null) {
            response.getHeaders().add(HEADER_CREDITS_REMAINING, String.valueOf(result.remainingCredits()));
        }

        if (result.resetsAt() != null) {
            response.getHeaders().add(HEADER_QUOTA_RESETS_AT, result.resetsAt().toString());
        }

        return body;
    }
}
