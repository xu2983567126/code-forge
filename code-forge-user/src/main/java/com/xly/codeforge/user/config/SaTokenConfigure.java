package com.xly.codeforge.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 登录态拦截器
 *
 * <p>登录态以 Redis 中的 token 为中心，本拦截器在每个请求上直读 Redis 校验，
 * 天然跨服务 / 跨实例（分布式）。</p>
 *
 * <p>拦截器路径匹配的是 <b>servlet path</b>（即 context-path {@code /api/user} 之后的部分），
 * 因此这里写 {@code /login} 而非 {@code /api/user/login}。其余服务接入时按各自 context-path 调整。</p>
 *
 * <p><b>白名单原则：默认全拦，只显式放行真正的公开路径。</b>
 * 加接口时先问「未登录用户该不该访问到」，该放行的必须同步加进来；
 * 并且要逐条对照 controller 的类/方法注释核对 —— <b>不要照抄其它服务的清单</b>：
 * user-service 只有榜单这一处公开读接口，而 question / submission 的公开接口多得多。</p>
 *
 * <p><b>文档端点必须放行</b>：前端 SDK 由 {@code code-forge-web/scripts/merge-openapi.mjs}
 * 匿名抓取各服务文档生成，拦住它会让 {@code npm run generate:api:force} 直接失败。</p>
 *
 * @author xuxu
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",         // 登录
                        "/register",      // 注册
                        "/logout",        // 注销（内部自行判空并返回 40100）
                        "/get/login",     // 探测当前登录态：匿名应返回 40100 而非「系统错误」
                        "/list/page/vo",  // 公开用户列表（榜单、题解作者列表等，见 UserController 注释）
                        "/inner/**",      // 服务间调用
                        "/v3/api-docs",   // springdoc 文档（SDK 生成脚本匿名抓取）
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error");
    }
}
