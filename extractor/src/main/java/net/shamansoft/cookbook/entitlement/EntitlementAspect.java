package net.shamansoft.cookbook.entitlement;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class EntitlementAspect {

    static final String ENTITLEMENT_RESULT_ATTR = "entitlementResult";

    private final EntitlementService entitlementService;

    @Around("@annotation(entitlement)")
    public Object around(ProceedingJoinPoint pjp, CheckEntitlement entitlement) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new EntitlementAuthException(
                    "userId not set in request context — unauthenticated request reached protected method");
        }

        Object tierAttr = request.getAttribute("userTier");
        UserTier userTier = tierAttr instanceof UserTier t ? t : null;
        Operation operation = entitlement.value();

        EntitlementResult result = entitlementService.check(userId, userTier, operation);

        if (!result.allowed()) {
            throw new EntitlementException(result);
        }

        request.setAttribute(ENTITLEMENT_RESULT_ATTR, result);
        return pjp.proceed();
    }
}
