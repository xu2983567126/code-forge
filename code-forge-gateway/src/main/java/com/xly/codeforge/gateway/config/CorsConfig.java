package com.xly.codeforge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * 网关全局跨域配置（WebFlux / Reactive 栈）
 *
 * <p>跨域只在网关这一层处理：下游服务若也各自加 CORS 响应头，
 * 经网关转发后会出现重复的 {@code Access-Control-Allow-Origin}，浏览器同样会拒绝。</p>
 *
 * <p>本类是 WebFlux 形态：用 {@link CorsWebFilter}（reactive），
 * 而不是 Servlet 栈的 {@code CorsFilter}。注意 {@code UrlBasedCorsConfigurationSource}
 * 也必须用 {@code org.springframework.web.cors.reactive} 包下的那个。</p>
 *
 * <p>放行名单用<b>显式地址</b>而非通配符：{@code allowCredentials=true} 下 {@code *}
 * 存在边界问题，浏览器行为也不可预期。</p>
 *
 * <p><b>前端调试请固定 5173</b>：dev server 换端口后，带 {@code Origin} 的请求会被这里挡成 403
 * （curl 不带 Origin 仍返回 200，极易误判成后端故障）。</p>
 *
 * @author xuxu
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        // 设置本地前端项目地址（同源 proxy 与跨域直连都覆盖）
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://127.0.0.1:5173", "http://localhost:5173",
                "http://127.0.0.1:8080", "http://localhost:8080",
                "http://127.0.0.1:3000", "http://127.0.0.1:3000",
                "http://127.0.0.1:8101", "http://localhost:8101"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
