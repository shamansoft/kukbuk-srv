package net.shamansoft.cookbook.security;

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
class AdminAccessAspectTest {

    @Mock
    ProceedingJoinPoint pjp;

    private AdminAccessAspect aspect;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        aspect = new AdminAccessAspect();
        mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void around_noIsAdminAttribute_throwsAdminAccessException() {
        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(AdminAccessException.class)
                .hasMessageContaining("Admin access required");
    }

    @Test
    void around_isAdminFalse_throwsAdminAccessException() {
        mockRequest.setAttribute("isAdmin", false);

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(AdminAccessException.class)
                .hasMessageContaining("Admin access required");
    }

    @Test
    void around_isAdminTrue_proceeds() throws Throwable {
        mockRequest.setAttribute("isAdmin", true);
        when(pjp.proceed()).thenReturn("result");

        Object result = aspect.around(pjp);

        verify(pjp).proceed();
        assertThat(result).isEqualTo("result");
    }

    @Test
    void around_isAdminWrongType_throwsAdminAccessException() {
        mockRequest.setAttribute("isAdmin", "true"); // string, not boolean

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(AdminAccessException.class);
    }
}
