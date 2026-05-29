package net.shamansoft.cookbook.security;

import jakarta.servlet.http.HttpServletRequest;
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
public class AdminAccessAspect {

    @Around("@annotation(net.shamansoft.cookbook.security.RequireAdmin)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        Object isAdmin = request.getAttribute("isAdmin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            log.warn("Admin access denied for request: {}", request.getRequestURI());
            throw new AdminAccessException("Admin access required");
        }

        return pjp.proceed();
    }
}
