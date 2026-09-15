package com.seewhy.syaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
@Tag("integration")
class WayfinderTravelFacadeTest {

    @Resource
    private WayfinderTravelFacade wayfinderTravelFacade;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮：用户开场
        String message = "你好，我想规划一次旅行。";
        String answer = wayfinderTravelFacade.doChat(message, chatId);
        // 第二轮：用户提出模糊需求
        message = "我下个月有假期，大概7天左右。";
        answer = wayfinderTravelFacade.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮：测试AI的记忆和引导能力
        message = "我更喜欢自然风光，刚才说的预算范围是人均8000元以内。";
        answer = wayfinderTravelFacade.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    /** 依赖 PostgreSQL + PgVector，无可用数据库时跳过；需跑此用例时请去掉 @Disabled 并配置好数据源 */
    @Test
    @Disabled("Requires PostgreSQL and PgVector; enable when DB is available")
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        // 测试对旅行知识库的查询
        String message = "前往新西兰需要提前多久申请签证？有哪些必去的自然景点？";
        String answer = wayfinderTravelFacade.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

}
