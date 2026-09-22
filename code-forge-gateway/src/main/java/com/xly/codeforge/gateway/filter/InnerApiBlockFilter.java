package com.xly.codeforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关边界安全：拦截「内部接口」从外部（网关 8101）直连访问。
 *
 * <p>各服务的 {@code /api/{svc}/inner/**} 是服务间调用契约，只应通过
 * {@code http://code-forge-{svc}}（Nacos 服务名）直连各服务实例端口，
 * 不应经网关转发。网关路由表 {@code /api/{svc}/**} 整体转发（无 StripPrefix），
 * 不拦的话外部可直接打 {@code /api/submission/inner/...} 这类内部接口。</p>
 *
 * <p>本过滤器在路由转发之前短路（order = HIGHEST_PRECEDENCE），命中即返回 403，
 * 不调用 {@code chain}。服务间调用走直连、不经过网关，因此零影响；前端也不调用
 * inner 接口（路径无 inner 段），合法流量不受影响。</p>
 *
 * @author xuxu
 */
@Component
public class InnerApiBlockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InnerApiBlockFilter.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String INNER_PATTERN = "/api/*/inner/**";
    private static final byte[] FORBIDDEN_BODY =
            "{\"code\":40300,\"message\":\"内部接口禁止从网关访问\"}".getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (PATH_MATCHER.match(INNER_PATTERN, path)) {
            log.warn("拒绝从网关访问内部接口: {} {} <- {}",
                    request.getMethod(), path, request.getRemoteAddress());
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(FORBIDDEN_BODY)));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
