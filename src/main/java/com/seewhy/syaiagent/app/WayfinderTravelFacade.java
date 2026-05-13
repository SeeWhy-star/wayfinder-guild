package com.seewhy.syaiagent.app;

import com.seewhy.syaiagent.constant.WayfinderPromptConstant;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.TravelReport;
import com.seewhy.syaiagent.orchestrator.TravelOrchestratorService;
import com.seewhy.syaiagent.service.TravelChatService;
import com.seewhy.syaiagent.service.TravelMcpService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.TravelReportService;
import com.seewhy.syaiagent.service.TravelToolService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class WayfinderTravelFacade {

    private final TravelChatService travelChatService;
    private final TravelReportService travelReportService;
    private final TravelRagService travelRagService;
    private final TravelToolService travelToolService;
    private final TravelMcpService travelMcpService;
    private final TravelOrchestratorService travelOrchestratorService;

    public WayfinderTravelFacade(TravelChatService travelChatService,
                                 TravelReportService travelReportService,
                                 TravelRagService travelRagService,
                                 TravelToolService travelToolService,
                                 TravelMcpService travelMcpService,
                                 TravelOrchestratorService travelOrchestratorService) {
        this.travelChatService = travelChatService;
        this.travelReportService = travelReportService;
        this.travelRagService = travelRagService;
        this.travelToolService = travelToolService;
        this.travelMcpService = travelMcpService;
        this.travelOrchestratorService = travelOrchestratorService;
    }

    public String doChat(String message, String chatId) {
        return travelChatService.chat(message, chatId);
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return travelChatService.streamChat(message, chatId);
    }

    public TravelReport doChatWithReport(String message, String chatId) {
        return travelReportService.generateReport(message, chatId);
    }

    public TravelPlan doStructuredPlan(String message, String chatId) {
        return travelOrchestratorService.generatePlan(message, chatId);
    }

    public String doChatWithRag(String message, String chatId) {
        return travelRagService.chatWithRag(message, chatId);
    }

    public String doChatWithTools(String message, String chatId) {
        return travelToolService.chatWithTools(message, chatId);
    }

    public String doChatWithMcp(String message, String chatId) {
        return travelMcpService.chatWithMcp(message, chatId);
    }

    public String quickTravelConsult(String message) {
        return doChat(message, "quick-" + System.currentTimeMillis());
    }

    public String getSystemInfo() {
        return "Wayfinder Travel Agent v1.0\n" +
                "功能：旅行规划、行程建议、预算管理、知识库问答\n" +
                "状态：运行正常\n" +
                "提示词：" + WayfinderPromptConstant.SYSTEM_PROMPT.substring(0, Math.min(100, WayfinderPromptConstant.SYSTEM_PROMPT.length())) + "...";
    }
}
