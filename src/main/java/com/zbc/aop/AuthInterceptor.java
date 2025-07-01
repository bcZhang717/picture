package com.zbc.aop;

import com.zbc.annotations.AuthCheck;
import com.zbc.domain.pojo.User;
import com.zbc.enums.UserRoleEnum;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Component
@Aspect
public class AuthInterceptor {
    @Resource
    private UserService userService;

    /**
     * 拦截校验AOP
     *
     * @param joinPoint 切入点
     * @param check     校验注解
     * @return Object
     * @throws Throwable 抛出异常
     */
    @Around("@annotation(check)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck check) throws Throwable {
        String mustRole = check.mustRole();
        // 获取当前请求对象
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        // 转换
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        // 获取当前登录用户
        User currentUser = userService.getCurrentUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 如果不需要权限, 直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 校验权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(currentUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
