package net.shamansoft.cookbook.controller;

import net.shamansoft.cookbook.dto.admin.UpdateTierRequest;
import net.shamansoft.cookbook.dto.admin.UpdateTierResponse;
import net.shamansoft.cookbook.entitlement.UserTier;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.security.AdminAccessException;
import net.shamansoft.cookbook.security.AdminAccessAspect;
import net.shamansoft.cookbook.service.AdminUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private static final String ADMIN_USER_ID = "admin-123";
    private static final String TARGET_USER_ID = "user-456";

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminController controller;

    private MockHttpServletRequest mockRequest;
    private AdminAccessAspect aspect;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        aspect = new AdminAccessAspect();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void updateUserTier_validAdminRequest_returns200WithBody() {
        UpdateTierRequest request = new UpdateTierRequest(UserTier.PRO);
        UpdateTierResponse expectedResponse = new UpdateTierResponse(TARGET_USER_ID, UserTier.FREE, UserTier.PRO);
        when(adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO)).thenReturn(expectedResponse);

        ResponseEntity<UpdateTierResponse> response = controller.updateUserTier(TARGET_USER_ID, request, ADMIN_USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(TARGET_USER_ID);
        assertThat(response.getBody().oldTier()).isEqualTo(UserTier.FREE);
        assertThat(response.getBody().newTier()).isEqualTo(UserTier.PRO);
        verify(adminUserService).updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO);
    }

    @Test
    void updateUserTier_unknownUserId_throwsUserNotFoundException() {
        UpdateTierRequest request = new UpdateTierRequest(UserTier.PRO);
        when(adminUserService.updateUserTier(any(), eq(TARGET_USER_ID), any()))
                .thenThrow(new UserNotFoundException("User not found: " + TARGET_USER_ID));

        assertThatThrownBy(() -> controller.updateUserTier(TARGET_USER_ID, request, ADMIN_USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(TARGET_USER_ID);
    }

    @Test
    void updateUserTier_allTiers_delegatesToService() {
        for (UserTier tier : UserTier.values()) {
            UpdateTierRequest request = new UpdateTierRequest(tier);
            UpdateTierResponse expectedResponse = new UpdateTierResponse(TARGET_USER_ID, UserTier.FREE, tier);
            when(adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, tier)).thenReturn(expectedResponse);

            ResponseEntity<UpdateTierResponse> response = controller.updateUserTier(TARGET_USER_ID, request, ADMIN_USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().newTier()).isEqualTo(tier);
        }
    }

    // AdminAccessAspect enforces the @RequireAdmin annotation — no isAdmin attribute → 403
    @Test
    void adminAccessAspect_missingIsAdminAttribute_throwsAdminAccessException() {
        // isAdmin attribute not set (simulates non-admin request)
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(AdminAccessException.class);
        // CookbookExceptionHandler maps AdminAccessException → HTTP 403 Forbidden
    }

    @Test
    void adminAccessAspect_isAdminFalse_throwsAdminAccessException() {
        mockRequest.setAttribute("isAdmin", false);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(AdminAccessException.class);
    }
}
