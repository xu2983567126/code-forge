package com.xly.codeforge.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Spring MVC JSON 全局配置 —— Long 转字符串
 *
 * <p>实体主键是 Long 雪花 id（19 位），JS 的 Number 只有 53 位精度，
 * 直接返回数字会在前端丢精度（末位变 0），因此统一序列化为字符串。</p>
 *
 * <p>本工程用 Jackson 3（{@code tools.jackson.databind}）。注册 {@link Module} Bean 即可，
 * 由 Spring Boot 的 Jackson 自动配置统一装配到主 {@code ObjectMapper} 上。
 * <b>不要</b>改用 Spring 的 {@code Jackson2ObjectMapperBuilder} —— 它属 Jackson 2 兼容体系，
 * 在 Jackson 3 类路径下会出现类型注解无法解析的问题。</p>
 *
 * @author xuxu
 */
@Configuration
public class JsonConfig {

    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
