package com.seewhy.syaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "wayfinder.demo.enabled=true",
        "travel.rag.mode=demo",
})
class SyAiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
