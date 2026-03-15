package net.shamansoft.cookbook.entitlement;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementAspectTest {

    @Mock
    EntitlementService entitlementService;

    @Mock
    ProceedingJoinPoint pjp;

    private EntitlementAspect aspect;
    private MockHttpServletRequest mockRequest;
    private CheckEntitlement annotation;

    // Helper to retrieve a real @CheckEntitlement annotation instance
    static class AnnotatedHelper {
        @CheckEntitlement(Operation.RECIPE_EXTRACTION)
        public void annotatedMethod() {
        }
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new EntitlementAspect(entitlementService);
        mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        annotation = AnnotatedHelper.class.getMethod("annotatedMethod").getAnnotation(CheckEntitlement.class);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void around_userIdNull_throwsIllegalStateException() {
        // userId not set — simulates unauthenticated request slipping through
        assertThatThrownBy(() -> aspect.around(pjp, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId not set");
    }

    @Test
    void around_entitlementDenied_throwsEntitlementException() {
        mockRequest.setAttribute("userId", "user123");
        EntitlementResult denied = new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, 0, null);
        when(entitlementService.check("user123", null, Operation.RECIPE_EXTRACTION)).thenReturn(denied);

        assertThatThrownBy(() -> aspect.around(pjp, annotation))
                .isInstanceOf(EntitlementException.class)
                .satisfies(e -> {
                    EntitlementResult r = ((EntitlementException) e).getResult();
                    assertThat(r.outcome()).isEqualTo(EntitlementOutcome.DENIED_QUOTA);
                    assertThat(r.allowed()).isFalse();
                });
    }

    @Test
    void around_entitlementAllowed_proceedsAndStoresResult() throws Throwable {
        mockRequest.setAttribute("userId", "user123");
        EntitlementResult allowed = new EntitlementResult(true, EntitlementOutcome.ALLOWED_FREE_QUOTA, 4, null, null);
        when(entitlementService.check("user123", null, Operation.RECIPE_EXTRACTION)).thenReturn(allowed);
        when(pjp.proceed()).thenReturn("proceed-result");

        Object result = aspect.around(pjp, annotation);

        verify(pjp).proceed();
        assertThat(result).isEqualTo("proceed-result");
        assertThat(mockRequest.getAttribute(EntitlementAspect.ENTITLEMENT_RESULT_ATTR)).isEqualTo(allowed);
    }

    @Test
    void around_userTierFromRequest_passedToService() throws Throwable {
        mockRequest.setAttribute("userId", "user123");
        mockRequest.setAttribute("userTier", UserTier.PRO);
        EntitlementResult allowed = EntitlementResult.paid();
        when(entitlementService.check("user123", UserTier.PRO, Operation.RECIPE_EXTRACTION)).thenReturn(allowed);
        when(pjp.proceed()).thenReturn(null);

        aspect.around(pjp, annotation);

        verify(entitlementService).check("user123", UserTier.PRO, Operation.RECIPE_EXTRACTION);
    }
}
