package com.exam.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RequireRoleAspect {

    @Around("@annotation(com.exam.common.RequireRole) || @within(com.exam.common.RequireRole)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RequireRole ann = method.getAnnotation(RequireRole.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(RequireRole.class);
        }
        if (ann != null) {
            String role = UserContext.role();
            if (role == null) throw new BizException(401, "未登录");
            boolean ok = Arrays.stream(ann.value()).anyMatch(r -> r.name().equals(role));
            if (!ok) throw new BizException(403, "权限不足");
        }
        return pjp.proceed();
    }
}
