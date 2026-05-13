package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelToolService {

    private final ChatClient chatClient;
    private final ToolCallback[] travelTools;
    private final AgentTraceService agentTraceService;

    public TravelToolService(@Qualifier("travelChatClient") ChatClient chatClient,
                             ToolCallback[] travelTools,
                             AgentTraceService agentTraceService) {
        this.chatClient = chatClient;
        this.travelTools = travelTools;
        this.agentTraceService = agentTraceService;
    }

    public String chatWithTools(String message, String chatId) {
        log.info("Travel tool chat [{}], message chars={}", chatId, message == null ? 0 : message.length());
        agentTraceService.record(chatId, AgentTraceStep.TOOL_CALL, AgentTraceStatus.STARTED, "Tool-enabled travel chat started.");

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(travelTools)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("Travel tool response [{}], chars={}", chatId, content == null ? 0 : content.length());
        agentTraceService.record(chatId, AgentTraceStep.TOOL_CALL, AgentTraceStatus.COMPLETED, "Tool-enabled travel chat completed.");
        return content;
    }
}
