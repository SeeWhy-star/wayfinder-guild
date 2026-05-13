package com.seewhy.syaiagent.app;

import jakarta.annotation.Resource;
import com.seewhy.syaiagent.model.TravelReport;
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

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        // 提供一个具体的旅行规划请求，以生成报告
        String message = "我计划下个月去日本东京玩5天，预算1万元，主要想体验美食和城市文化，请帮我生成一份规划报告。";
        TravelReport travelReport = wayfinderTravelFacade.doChatWithReport(message, chatId);
        Assertions.assertNotNull(travelReport);
        // 可以增加更多断言，如报告标题不为空等
        Assertions.assertNotNull(travelReport.title());
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

    @Test
    void doChatWithTools() {
        // 测试联网搜索：目的地信息
        testMessage("帮我搜索一下瑞士因特拉肯近期（未来两周）的天气情况和徒步路线推荐？");
        // 测试网页抓取：旅行攻略
        testMessage("我想了解最近在马蜂窝等网站上，大家对泰国清迈自由行的最新评价和避坑指南？");
        // 测试资源下载：旅行图片
        testMessage("直接下载一张高清的挪威极光风景图作为我的行程灵感墙纸。");
        // 测试终端操作：行程数据处理
        testMessage("我有一个CSV格式的景点清单，请执行一个Python脚本，帮我按评分从高到低排序并输出。");
        // 测试文件操作：保存行程规划
        testMessage("将刚才我们讨论好的‘京都5日赏枫之旅’详细行程保存为一份Markdown文件。");
        // 测试 PDF 生成：行程单
        testMessage("为我刚才定制的‘厦门4天3晚美食之旅’生成一份包含每日行程、酒店信息、预算明细和注意事项的PDF旅行手册。");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = wayfinderTravelFacade.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP (根据您的注释，可能暂未启用)
//         String message = "我计划去上海静安区，请帮我找到 5 公里内合适的旅游地点";
//         String answer =  wayfinderTravelFacade.doChatWithMcp(message, chatId);
//         Assertions.assertNotNull(answer);

        // 测试图片搜索 MCP：改为旅行相关主题
        String message = "帮我搜索一些日本北海道夏季富良野花田的风景图片。";
        String answer =  wayfinderTravelFacade.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
