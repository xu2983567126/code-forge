package com.xly.codeforge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 网关出站 HTTP 客户端配置。
 *
 * <h3>为什么必须显式指定（这是一个真实踩过的坑）</h3>
 * <p>网关 classpath 上有 {@code httpclient5}（由 {@code nacos-client} 传递进来，不能排除），
 * Spring Boot 的 {@code RestClient.Builder} 会自动选中
 * {@code HttpComponentsClientHttpRequestFactory}，而 <b>Apache HttpClient 5 默认开启
 * 进程级 CookieStore</b>：它会自动保存下游响应的 {@code Set-Cookie}，并在后续请求
 * 没有显式 Cookie 头时自动补上。</p>
 *
 * <p>Spring Cloud Gateway MVC 的 {@code GatewayServerMvcAutoConfiguration#restClientProxyExchange}
 * 用的正是这个 {@code RestClient.Builder}，于是出现极其危险的越权效果 ——
 * <b>甲登录后，任何不带 cookie 的匿名请求都会被网关自动带上甲的 JSESSIONID，
 * 直接以甲的身份通过鉴权</b>（实测：不带 cookie 调 {@code /api/user/get/login} 返回了 admin）。</p>
 *
 * <p>换掉这个越权坑：换成基于 {@code java.net.http.HttpClient} 的 {@code JdkClientHttpRequestFactory} 即可，
 * JDK HttpClient 的 CookieHandler 默认为 null，完全不碰 cookie。</p>
 *
 * <p>本类不能删：它是网关出站 {@code RestClient} 的兜底保护，防止将来新增出站调用时
 * 重新踩上这个越权坑。注意只要本模块存在 {@code ClientHttpRequestFactory} 类型的 Bean，
 * 网关的 {@code gatewayRestClientCustomizer} 就会把它应用到代理用的 RestClient 上。</p>
 *
 * @author xuxu
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        return new JdkClientHttpRequestFactory();
    }
}
