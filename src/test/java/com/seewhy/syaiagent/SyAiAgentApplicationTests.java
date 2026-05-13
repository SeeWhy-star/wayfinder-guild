package com.seewhy.syaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "wayfinder.demo.enabled=true",
        "travel.rag.mode=demo",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "knife4j.enable=false"
})
class SyAiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
