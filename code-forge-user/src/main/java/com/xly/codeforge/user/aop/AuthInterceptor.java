package com.xly.codeforge.user.aop;

import com.xly.codeforge.common.annotation.AuthCheck;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.RoleEnum;
import com.xly.codeforge.user.service.UserService;
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
 * <p>原生单体里叫 AuthInterceptor，拆分时整个 aop 包没有跟过来，导致
 * user / question 两个 Controller 上的 7 处 {@code @AuthCheck} 全部形同摆设
 * （/api/user/delete、/update、/list/page 等管理端接口处于裸奔状态）。</p>
 *
 * <p>user-service 版直接注入本服务的 {@link UserService}（自己就是用户服务，
 * 不需要绕一圈 Feign）。</p>
 *
 * @author xuxu
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userService.getLoginUser(request);
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
