package com.seewhy.syaiagent.config;

import com.seewhy.syaiagent.security.OwnerAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OwnerAccessWebConfig implements WebMvcConfigurer {

    private final OwnerAccessInterceptor ownerAccessInterceptor;

    public OwnerAccessWebConfig(OwnerAccessInterceptor ownerAccessInterceptor) {
        this.ownerAccessInterceptor = ownerAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAccessInterceptor)
                .addPathPatterns("/travel/**", "/rpg/evals/run/**");
    }
}
