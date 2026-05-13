package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.WayfinderPromptConstant;
import com.seewhy.syaiagent.model.TravelReport;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelReportService {

    private final ChatClient chatClient;
    private final AgentTraceService agentTraceService;

    public TravelReportService(@Qualifier("travelChatClient") ChatClient chatClient,
                               AgentTraceService agentTraceService) {
        this.chatClient = chatClient;
        this.agentTraceService = agentTraceService;
    }

    public TravelReport generateReport(String message, String chatId) {
        log.info("Generating travel report [{}], message chars={}", chatId, message == null ? 0 : message.length());
        agentTraceService.record(chatId, AgentTraceStep.REPORT_GENERATION, AgentTraceStatus.STARTED, "Generating travel report.");

        TravelReport travelReport = chatClient
                .prompt()
                .system(WayfinderPromptConstant.REPORT_PROMPT)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);

        log.info("Travel report generated [{}], title chars={}", chatId,
                travelReport.title() == null ? 0 : travelReport.title().length());
        agentTraceService.record(chatId, AgentTraceStep.REPORT_GENERATION, AgentTraceStatus.COMPLETED, "Travel report generated.");
        return travelReport;
    }
}
