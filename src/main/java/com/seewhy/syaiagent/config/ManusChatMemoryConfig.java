package com.seewhy.syaiagent.config;

import com.seewhy.syaiagent.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SyManus 智能体对话记忆配置（按 chatId 持久化多轮上下文）
 */
@Configuration
public class ManusChatMemoryConfig {

    private static final String MANUS_CHAT_DIR = "data/manus-chat";

    @Bean(name = "manusChatMemory")
    public ChatMemory manusChatMemory() {
        return new FileBasedChatMemory(MANUS_CHAT_DIR);
    }
}
