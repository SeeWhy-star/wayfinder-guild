package com.seewhy.syaiagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.agent.SyManus;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.model.DemoToolRequest;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.seewhy.syaiagent.service.SyManusDemoToolService;
import com.seewhy.syaiagent.service.SyManusRecordedDemoToolService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.security.OwnerAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/travel/manus")
@Slf4j
public class SyManusController {

    private final ToolCallback[] allTools;
    private final ChatModel chatModel;
    private final ChatMemory manusChatMemory;
    private final WayfinderDemoService wayfinderDemoService;
    private final SyManusDemoToolService syManusDemoToolService;
    private final SyManusRecordedDemoToolService syManusRecordedDemoToolService;
    private final SyManusArtifactLinkService syManusArtifactLinkService;
    private final ObjectMapper objectMapper;
    private final OwnerAccessService ownerAccessService;

    public SyManusController(ToolCallback[] allTools,
                             @Qualifier("openAiChatModel") ChatModel chatModel,
                             @Qualifier("manusChatMemory") ChatMemory manusChatMemory,
                             WayfinderDemoService wayfinderDemoService,
                             SyManusDemoToolService syManusDemoToolService,
                             SyManusRecordedDemoToolService syManusRecordedDemoToolService,
                             SyManusArtifactLinkService syManusArtifactLinkService,
                             ObjectMapper objectMapper,
                             OwnerAccessService ownerAccessService) {
        this.allTools = allTools;
        this.chatModel = chatModel;
        this.manusChatMemory = manusChatMemory;
        this.wayfinderDemoService = wayfinderDemoService;
        this.syManusDemoToolService = syManusDemoToolService;
        this.syManusRecordedDemoToolService = syManusRecordedDemoToolService;
        this.syManusArtifactLinkService = syManusArtifactLinkService;
        this.objectMapper = objectMapper;
        this.ownerAccessService = ownerAccessService;
    }

    /**
     * 调用 Manus 智能体（流式）。支持 chatId 多轮对话：同一 chatId 会带上近期历史，便于解析「他/她」等指代。
     */
    @GetMapping("/chat")
    public SseEmitter doChatWithManus(@RequestParam String message,
                                      @RequestParam(required = false) String chatId,
                                      @RequestParam(defaultValue = "false") boolean liveMode,
                                      HttpServletRequest httpRequest) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        if (!liveMode) {
            return demoManusBoundaryEmitter(message, true);
        }
        if (!ownerAccessService.hasOwnerAccess(httpRequest)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner token required for live SyManus tasks.");
        }
        if (chatModel == null || allTools == null || allTools.length == 0) {
            return demoManusBoundaryEmitter(message, false);
        }
        SyManus agent = new SyManus(allTools, chatModel, syManusArtifactLinkService);
        agent.setConversationChatId(id);
        agent.setConversationChatMemory(manusChatMemory);
        agent.setArtifactMarkerFormatter(this::formatManusArtifactMarker);
        return agent.runStream(message);
    }

    @PostMapping("/demo-tool")
    public DemoToolResponse runManusDemoTool(@RequestBody DemoToolRequest request) {
        if (request == null || request.type() == null || request.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo tool type is required.");
        }
        return syManusDemoToolService.runDemo(request.type());
    }

    @PostMapping("/recorded-demo-tool")
    public DemoToolResponse runRecordedManusDemoTool(@RequestBody DemoToolRequest request) {
        if (request == null || request.type() == null || request.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recorded demo tool type is required.");
        }
        return syManusRecordedDemoToolService.runRecordedDemo(request.type());
    }

    private SseEmitter demoManusBoundaryEmitter(String message, boolean publicDemoMode) {
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(liveToolBoundaryMessage(message, publicDemoMode));
                emitter.send("__DONE__");
                emitter.complete();
            } catch (IOException e) {
                log.debug("Could not send SyManus live boundary message: {}", e.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    private String liveToolBoundaryMessage(String message, boolean publicDemoMode) {
        StringBuilder text = new StringBuilder();
        if (publicDemoMode) {
            text.append("Current public demo mode keeps Live Tool Tasks behind a configuration boundary. ");
        } else {
            text.append("Live Tool Tasks are not configured in this backend session. ");
        }
        text.append("They use the real SyManus ReAct loop and need a configured model/API key and any required external services. ");
        text.append("Use Stable Engineering Demos above for local, repeatable project checks and artifacts.");
        if (looksLikeImageTask(message)) {
            text.append("\n\nImage search depends on Pexels API key and external network; unavailable in this demo environment.");
        }
        return text.toString();
    }

    private boolean looksLikeImageTask(String message) {
        String value = String.valueOf(message).toLowerCase();
        return value.contains("image") || value.contains("photo") || value.contains("picture")
                || value.contains("pexels") || value.contains("\u56fe\u7247") || value.contains("\u7167\u7247");
    }

    private String formatManusArtifactMarker(DemoArtifactResponse artifact) {
        try {
            return "[ARTIFACT]" + objectMapper.writeValueAsString(artifact) + "[/ARTIFACT]";
        } catch (JsonProcessingException e) {
            log.debug("Could not serialize artifact marker for {}: {}", artifact.fileName(), e.getMessage());
            return "";
        }
    }

    private String normalizeChatId(String chatId) {
        return chatId != null && !chatId.isBlank() ? chatId : "manus-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }
}
