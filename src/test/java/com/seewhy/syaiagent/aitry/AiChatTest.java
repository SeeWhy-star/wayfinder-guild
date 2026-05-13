package com.seewhy.syaiagent.aitry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("integration")
public class AiChatTest {

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Test
    public void testSimpleChat() {
        System.out.println("=== 开始测试 AI 对话 ===");
        String response = chatModel.call(new Prompt("你好，我是sy"))
                .getResult()
                .getOutput()
                .getText();
        System.out.println("AI 回复: " + response);
        assertNotNull(response);
        System.out.println("=== 测试完成 ===");
    }
}
