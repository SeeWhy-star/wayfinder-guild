package com.seewhy.syaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WayfinderWebMvcAsyncConfig implements WebMvcConfigurer {

    public static final String MVC_ASYNC_EXECUTOR_BEAN = "wayfinderMvcAsyncExecutor";
    private static final long DEFAULT_TIMEOUT_MILLIS = 120_000L;

    private final AsyncTaskExecutor mvcAsyncExecutor;

    public WayfinderWebMvcAsyncConfig(@Qualifier(MVC_ASYNC_EXECUTOR_BEAN) AsyncTaskExecutor mvcAsyncExecutor) {
        this.mvcAsyncExecutor = mvcAsyncExecutor;
    }

    @Bean(MVC_ASYNC_EXECUTOR_BEAN)
    public static ThreadPoolTaskExecutor wayfinderMvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("wayfinder-mvc-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor);
        configurer.setDefaultTimeout(DEFAULT_TIMEOUT_MILLIS);
    }
}
