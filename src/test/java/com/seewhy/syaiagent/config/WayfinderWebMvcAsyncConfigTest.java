package com.seewhy.syaiagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
class WayfinderWebMvcAsyncConfigTest {

    @Test
    void mvcAsyncExecutorUsesBoundedPortfolioThreadPool() {
        ThreadPoolTaskExecutor executor = WayfinderWebMvcAsyncConfig.wayfinderMvcAsyncExecutor();
        try {
            assertEquals("wayfinder-mvc-", executor.getThreadNamePrefix());
            assertEquals(4, executor.getCorePoolSize());
            assertEquals(12, executor.getMaxPoolSize());
            assertEquals(100, executor.getQueueCapacity());
        } finally {
            executor.shutdown();
        }
    }
}
