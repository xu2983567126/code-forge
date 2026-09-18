package com.xly.codeforge.question.aop;

import com.xly.codeforge.common.annotation.AuthCheck;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.RoleEnum;
import com.xly.codeforge.client.service.UserFeignClient;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP
 *
 * <p>question-service 版：本服务没有 UserService，登录用户走
 * {@link UserFeignClient#getLoginUser(HttpServletRequest)} 获取 ——
 * 该方法直读 Sa-Token 的 Redis token，匿名调用抛未登录错误。</p>
 *
 * @author xuxu
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserFeignClient userFeignClient;

    /**
     * 执行拦截
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userFeignClient.getLoginUser(request);
        RoleEnum mustRoleEnum = RoleEnum.getEnumByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有该权限才通过
        RoleEnum roleEnum = RoleEnum.getEnumByValue(loginUser.getRole());
        if (roleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 如果被封号，直接拒绝
        if (RoleEnum.BAN.equals(roleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 必须有管理员权限
        if (RoleEnum.ADMIN.equals(mustRoleEnum) && !RoleEnum.ADMIN.equals(roleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
