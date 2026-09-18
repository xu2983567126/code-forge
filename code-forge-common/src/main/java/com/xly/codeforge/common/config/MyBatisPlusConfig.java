package com.xly.codeforge.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 通用配置 —— 分页拦截器
 *
 * <p>放在 common 里，由各业务服务的 {@code @ComponentScan("com.xly")} 自动扫到，
 * 不需要每个服务各写一份。</p>
 *
 * <p>用 {@code @ConditionalOnClass} 做保护：judge-service 不连数据库、没有引入
 * mybatis-plus 依赖，条件不满足时本配置类整体跳过，不会因类找不到而启动失败。</p>
 *
 * @author xuxu
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MyBatisPlusConfig {

    /**
     * 分页插件
     *
     * <p>注意：MyBatis Plus 3.5.9 起分页能力依赖独立的 {@code mybatis-plus-jsqlparser} 构件，
     * 各连库服务都已在 pom 中显式声明。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
