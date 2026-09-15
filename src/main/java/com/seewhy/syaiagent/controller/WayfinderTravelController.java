package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.model.ChatRequest;
import com.seewhy.syaiagent.model.ChatResponse;
import com.seewhy.syaiagent.model.AgentRun;
import com.seewhy.syaiagent.model.HealthResponse;
import com.seewhy.syaiagent.model.RagExplainRequest;
import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.TravelPlanRequest;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.security.OwnerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/travel")
@Slf4j
public class WayfinderTravelController {

    private final WayfinderTravelFacade wayfinderTravelFacade;
    private final TravelRagService travelRagService;
    private final WayfinderDemoService wayfinderDemoService;
    private final OwnerAccessService ownerAccessService;

    public WayfinderTravelController(WayfinderTravelFacade wayfinderTravelFacade,
                                     TravelRagService travelRagService,
                                     WayfinderDemoService wayfinderDemoService,
                                     OwnerAccessService ownerAccessService) {
        this.wayfinderTravelFacade = wayfinderTravelFacade;
        this.travelRagService = travelRagService;
        this.wayfinderDemoService = wayfinderDemoService;
        this.ownerAccessService = ownerAccessService;
    }

    /**
     * 基础旅行对话
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = wayfinderTravelFacade.doChat(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    /**
     * 流式旅行对话（基于 Reactor Flux，直接作为 SSE 输出）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message,
                                   @RequestParam(required = false) String chatId,
                                   @RequestParam(defaultValue = "false") boolean liveMode,
                                   HttpServletRequest httpRequest) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        if (!liveMode) {
            return wayfinderDemoService.demoChatStream(message, id)
                    .doOnCancel(() -> log.info("Demo SSE stream cancelled for {}", id));
        }
        if (isPublicDemoRequest(httpRequest)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Owner token required for live Travel chat streaming."
            );
        }
        return wayfinderTravelFacade.doChatByStream(message, id)
                .doOnCancel(() -> log.info("SSE stream cancelled for {}", id))
                .onErrorContinue((err, obj) ->
                        log.debug("SSE stream error ignored for {}: {}", id, err.toString()));
    }

    /**
     * 结构化旅行规划
     */
    @PostMapping("/plan")
    public TravelPlan generatePlan(@Valid @RequestBody TravelPlanRequest request,
                                   HttpServletRequest httpRequest) {
        String chatId = normalizeChatId(request.chatId());
        if (Boolean.TRUE.equals(request.liveMode())) {
            if (ownerAccessService.hasOwnerAccess(httpRequest)) {
                return wayfinderTravelFacade.doStructuredPlan(request.message(), chatId);
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Owner token required for live TravelPlan generation."
            );
        }
        return wayfinderDemoService.demoTravelPlan();
    }

    @PostMapping("/plan/run")
    public AgentRun<TravelPlan> generatePlanRun(@Valid @RequestBody TravelPlanRequest request,
                                                HttpServletRequest httpRequest) {
        String chatId = normalizeChatId(request.chatId());
        if (!Boolean.TRUE.equals(request.liveMode())) {
            return wayfinderDemoService.demoTravelRun(chatId);
        }
        if (!ownerAccessService.hasOwnerAccess(httpRequest)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Owner token required for live TravelPlan generation."
            );
        }
        return wayfinderTravelFacade.runStructuredPlan(request.message(), chatId);
    }

    /**
     * 旅行知识库问答
     */
    @PostMapping("/rag")
    public ChatResponse ragChat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = wayfinderTravelFacade.doChatWithRag(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    @PostMapping("/rag/explain")
    public RagExplainResponse explainRag(@RequestBody RagExplainRequest request,
                                         HttpServletRequest httpRequest) {
        String chatId = normalizeChatId(request == null ? null : request.chatId());
        String message = request == null ? null : request.message();
        validateMessage(message);
        if (isPublicDemoRequest(httpRequest)) {
            return wayfinderDemoService.demoRagExplain(message, chatId);
        }
        return travelRagService.explainRag(message, chatId);
    }

    /**
     * 获取系统信息
     */
    @GetMapping("/system/info")
    public String getSystemInfo() {
        return wayfinderTravelFacade.getSystemInfo();
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/health")
    public HealthResponse healthCheck() {
        return new HealthResponse("ok", "Wayfinder Travel Agent is healthy");
    }

    private String generateChatId() {
        return "travel-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeChatId(String chatId) {
        return chatId != null && !chatId.isBlank() ? chatId : generateChatId();
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

    private boolean isPublicDemoRequest(HttpServletRequest request) {
        return !ownerAccessService.hasOwnerAccess(request);
    }

}
