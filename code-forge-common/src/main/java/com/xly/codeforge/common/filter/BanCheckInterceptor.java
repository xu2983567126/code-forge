package com.xly.codeforge.common.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.UserConstant;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 封禁用户拦截器（集中式）
 *
 * <p>被封号用户（{@code user.role = "ban"}）不应再访问任何业务接口。本类在
 * <b>一处</b>统一拦截，而不是在各个 Controller 里散点写 {@code if (role == BAN)} ——
 * 散点判断的典型缺陷是漏写：新增接口时没人记得补，于是封禁形同虚设。</p>
 *
 * <p><b>角色来源</b>：直接从 Sa-Token 的 token session 读取，登录时由
 * {@code UserServiceImpl#userLogin} 写入。登录态存 Redis、中心化，
 * 任何接入 Sa-Token 的服务都能读到同一份。</p>
 *
 * <p><b>未接入 Sa-Token 的服务</b>（如 judge-service，不面向用户）：{@link #resolveRole()}
 * 读取失败时返回 {@code null}，即跳过封禁判断，不会因 {@code SaManager} 未初始化而崩溃。</p>
 *
 * <p><b>为什么不直接用 {@code RoleEnum.BAN}</b>：{@code RoleEnum} 在 {@code code-forge-model}
 * 模块，而本模块（{@code common}）<b>刻意不依赖 model</b> —— model 里的实体带着 MyBatis-Plus 注解，
 * 一旦 common 依赖它，连<b>不连库的 judge-service</b> 也会被动引入 ORM 依赖
 * （见 {@code common/pom.xml} 中 MP 依赖刻意声明为 {@code provided} 的注释）。
 * 故这里用 common 自己的 {@link UserConstant#BAN_ROLE}，保持依赖方向干净。</p>
 *
 * <p><b>白名单</b>：登录 / 注册 / 注销 / 内部调用必须放行 ——
 * 被封用户至少得能登录进来看提示，否则界面上永远是「账号密码错误」，
 * 用户根本不知道自己被封了。内部接口（{@code /inner/}）走的是服务间调用，
 * 不带用户角色，也不该被这里拦。</p>
 *
 * @author xuxu
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class BanCheckInterceptor implements Filter {

    /**
     * 放行路径（前缀匹配即可，因为这些路径都在 context-path 之后）
     *
     * <p>注意：这里的路径<b>不含</b>各服务的 context-path（如 {@code /api/user}），
     * 因为 {@code HttpServletRequest#getRequestURI()} 在 Spring Boot 3 里
     * 由 Tomcat 提供时是否含 context-path 取决于配置，故本类用
     * {@code getServletPath()} 之外的宽松判断：只要 URI <b>包含</b>这些片段就放行。</p>
     */
    private static final List<String> WHITELIST = List.of(
            "/login",
            "/register",
            "/logout",
            "/inner/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        if (isWhitelisted(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String role = resolveRole();
        if (UserConstant.BAN_ROLE.equals(role)) {
            log.warn("被封禁用户尝试访问受保护接口，uri={}", uri);
            writeForbidden(httpResponse);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 判断是否命中白名单
     *
     * @param uri 请求 URI（含 context-path）
     * @return true 表示放行
     */
    private boolean isWhitelisted(String uri) {
        if (uri == null) {
            return false;
        }
        for (String path : WHITELIST) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析当前登录用户的角色，供封禁判断。
     *
     * <p>从 Sa-Token 的 token session 读取（登录时由 {@code UserServiceImpl#userLogin}
     * 写入），Redis 中心化、免网关注入。</p>
     *
     * <p><b>容错</b>：未接入 Sa-Token 的服务（如 judge-service，{@code SaManager}
     * 未初始化，{@code StpUtil} 调用会抛 {@code SaTokenException}）返回 {@code null}，
     * 即跳过封禁判断 —— 这类服务不面向用户，无需封禁。</p>
     *
     * @return 角色字符串；未登录 / 读取不到时返回 {@code null}
     */
    private String resolveRole() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            Object roleObj = StpUtil.getSession().get("role");
            return roleObj == null ? null : roleObj.toString();
        } catch (Exception e) {
            // 本服务未接入 Sa-Token（SaManager 未初始化）—— 不做封禁判断，避免崩溃
            return null;
        }
    }

    /**
     * 直接写出 40300 响应体，与 {@code GlobalExceptionHandler} 的格式保持一致，
     * 保证前端读取 {@code code} 字段的逻辑对所有错误一视同仁。
     *
     * <p>这里不抛 {@code BusinessException}：本类是 Filter，在 DispatcherServlet
     * <b>之前</b>执行，此时 {@code @RestControllerAdvice} 还没接管，抛异常会变成
     * Tomcat 的默认 500 页面，前端拿不到结构化错误码。</p>
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":" + ErrorCode.FORBIDDEN_ERROR.getCode()
                        + ",\"message\":\"账号已被封禁，请联系管理员\",\"data\":null}");
    }
}
