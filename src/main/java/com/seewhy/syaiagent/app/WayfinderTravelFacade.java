package com.seewhy.syaiagent.app;

import com.seewhy.syaiagent.constant.WayfinderPromptConstant;
import com.seewhy.syaiagent.model.AgentRun;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.orchestrator.TravelOrchestratorService;
import com.seewhy.syaiagent.service.TravelChatService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.AgentRunEvaluationService;
import com.seewhy.syaiagent.trace.AgentTraceService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class WayfinderTravelFacade {

    private final TravelChatService travelChatService;
    private final TravelRagService travelRagService;
    private final TravelOrchestratorService travelOrchestratorService;
    private final AgentTraceService agentTraceService;
    private final AgentRunEvaluationService agentRunEvaluationService;

    public WayfinderTravelFacade(TravelChatService travelChatService,
                                 TravelRagService travelRagService,
                                 TravelOrchestratorService travelOrchestratorService,
                                 AgentTraceService agentTraceService,
                                 AgentRunEvaluationService agentRunEvaluationService) {
        this.travelChatService = travelChatService;
        this.travelRagService = travelRagService;
        this.travelOrchestratorService = travelOrchestratorService;
        this.agentTraceService = agentTraceService;
        this.agentRunEvaluationService = agentRunEvaluationService;
    }

    public String doChat(String message, String chatId) {
        return travelChatService.chat(message, chatId);
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return travelChatService.streamChat(message, chatId);
    }

    public TravelPlan doStructuredPlan(String message, String chatId) {
        return travelOrchestratorService.generatePlan(message, chatId);
    }

    public AgentRun<TravelPlan> runStructuredPlan(String message, String chatId) {
        TravelPlan result = doStructuredPlan(message, chatId);
        return AgentRun.completed(chatId, "LIVE", result, agentTraceService.getEvents(chatId),
                agentRunEvaluationService.evaluateTravelRun(message, result));
    }

    public String doChatWithRag(String message, String chatId) {
        return travelRagService.chatWithRag(message, chatId);
    }

    public String getSystemInfo() {
        return "Wayfinder Travel Agent v1.0\n" +
                "功能：旅行规划、行程建议、预算管理、知识库问答\n" +
                "状态：运行正常\n" +
                "提示词：" + WayfinderPromptConstant.SYSTEM_PROMPT.substring(0, Math.min(100, WayfinderPromptConstant.SYSTEM_PROMPT.length())) + "...";
    }
}
