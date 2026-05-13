package com.seewhy.syaiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOriginPatterns;

    public CorsConfig(@Value("${wayfinder.cors.allowed-origin-patterns:http://localhost:5173}") String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(parseAllowedOriginPatterns())
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Accept", "Authorization", "Cache-Control", "Content-Type", "Last-Event-ID", "X-Wayfinder-Owner-Token")
                .allowCredentials(true)
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }

    private String[] parseAllowedOriginPatterns() {
        String[] patterns = Arrays.stream(String.valueOf(allowedOriginPatterns).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        return patterns.length == 0 ? new String[]{"http://localhost:5173"} : patterns;
    }
}
