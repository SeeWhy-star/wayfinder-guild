package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.WayfinderDemoStatusResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CapabilityStatusService {

    private final WayfinderDemoService wayfinderDemoService;
    private final ToolCallback[] allTools;
    private final ChatModel chatModel;
    private final String modelApiKey;
    private final String searchProviderName;
    private final String tavilyApiKey;
    private final String pexelsApiKey;
    private final String ragMode;

    public CapabilityStatusService(WayfinderDemoService wayfinderDemoService,
                                   ToolCallback[] allTools,
                                   @Qualifier("openAiChatModel") ChatModel chatModel,
                                   @Value("${spring.ai.openai.api-key:demo-disabled}") String modelApiKey,
                                   @Value("${search.provider:disabled}") String searchProviderName,
                                   @Value("${tavily.api-key:}") String tavilyApiKey,
                                   @Value("${pexels.api-key:}") String pexelsApiKey,
                                   @Value("${travel.rag.mode:${rag.mode:demo}}") String ragMode) {
        this.wayfinderDemoService = wayfinderDemoService;
        this.allTools = allTools;
        this.chatModel = chatModel;
        this.modelApiKey = modelApiKey;
        this.searchProviderName = searchProviderName;
        this.tavilyApiKey = tavilyApiKey;
        this.pexelsApiKey = pexelsApiKey;
        this.ragMode = normalizeRagMode(ragMode);
    }

    public WayfinderDemoStatusResponse currentStatus() {
        boolean demoMode = wayfinderDemoService.isEnabled();
        boolean liveManusAvailable = !demoMode
                && chatModel != null
                && allTools != null
                && allTools.length > 0
                && isConfiguredSecret(modelApiKey);
        boolean searchAvailable = !demoMode
                && "tavily".equalsIgnoreCase(blankToEmpty(searchProviderName))
                && isConfiguredSecret(tavilyApiKey);
        boolean imageSearchAvailable = !demoMode && isConfiguredSecret(pexelsApiKey);
        return new WayfinderDemoStatusResponse(
                demoMode,
                ragMode,
                liveManusAvailable,
                searchAvailable,
                imageSearchAvailable
        );
    }

    private String normalizeRagMode(String value) {
        String normalized = blankToEmpty(value).toLowerCase();
        if ("demo".equals(normalized) || "lightweight".equals(normalized) || "pgvector".equals(normalized)) {
            return normalized;
        }
        return "demo";
    }

    private boolean isConfiguredSecret(String value) {
        String normalized = blankToEmpty(value).toLowerCase();
        return !normalized.isBlank()
                && !"demo-disabled".equals(normalized)
                && !normalized.startsWith("your-");
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
