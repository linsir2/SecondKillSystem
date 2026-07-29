package com.seckill.config.web;

import com.seckill.module.gateway.filter.GatewayCheckFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GatewayCheckFilter gatewayCheckFilter;
    private final String[] allowedOrigins;

    public WebConfig(GatewayCheckFilter gatewayCheckFilter,
                     @Value("${cors.allowed-origins:*}") String allowedOrigins) {
        this.gatewayCheckFilter = gatewayCheckFilter;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        for (String origin : allowedOrigins) {
            String trimmed = origin.trim();
            if ("*".equals(trimmed)) {
                config.addAllowedOriginPattern("*");
            } else {
                config.addAllowedOrigin(trimmed);
            }
        }

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gatewayCheckFilter)
                .addPathPatterns("/api/v1/seckill/**");
    }
}
