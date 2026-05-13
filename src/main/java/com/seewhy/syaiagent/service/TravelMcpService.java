package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelMcpService {

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final AgentTraceService agentTraceService;

    public TravelMcpService(@Qualifier("travelChatClient") ChatClient chatClient,
                            ToolCallbackProvider toolCallbackProvider,
                            AgentTraceService agentTraceService) {
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
        this.agentTraceService = agentTraceService;
    }

    public String chatWithMcp(String message, String chatId) {
        log.info("Travel MCP chat [{}], message chars={}", chatId, message == null ? 0 : message.length());
        agentTraceService.record(chatId, AgentTraceStep.MCP_CALL, AgentTraceStatus.STARTED, "MCP travel service call started.");

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("Travel MCP response [{}], chars={}", chatId, content == null ? 0 : content.length());
        agentTraceService.record(chatId, AgentTraceStep.MCP_CALL, AgentTraceStatus.COMPLETED, "MCP travel service call completed.");
        return content;
    }
}
