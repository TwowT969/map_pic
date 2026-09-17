package org.lxp.mapalbum.framework.web.config;

import org.lxp.mapalbum.framework.web.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.io.File;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.Collections;

/**
 * Web 配置（CORS + 本地上传文件静态资源 + 登录拦截器注册，KSHG 规范 §14.1）。
 *
 * <p>上传目录由 {@code app.upload.path} 配置（环境变量注入，禁止硬编码提交，
 * KSHG 规范 §14.2）；通过 /uploads/** 对外提供只读访问。
 *
 * @author lxp
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Resource
    private AuthInterceptor authInterceptor;

    /**
     * 过滤器级 CORS（最高优先级）：预检请求（OPTIONS）在进入拦截器链之前
     * 即被处理并短路返回，避免登录拦截器拦截无令牌预检、导致浏览器/WebView
     * 阻止真实请求（表现为登录后 /spots/mine、/photos/mine 加载失败）。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传文件通过 /uploads/** 访问，映射到本地 uploads 目录
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir.getAbsolutePath() + File.separator);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录/登录外路径在拦截器内部按方法+路径细分；登录接口与静态资源直接排除
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login", "/user/register", "/uploads/**");
    }
}
