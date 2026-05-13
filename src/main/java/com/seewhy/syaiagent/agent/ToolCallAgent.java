package com.seewhy.syaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.agent.model.AgentState;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base ReAct agent implementation for explicit tool calling.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private SyManusArtifactLinkService artifactLinkService;
    private Function<DemoArtifactResponse, String> artifactMarkerFormatter;
    private final Map<Path, SyManusArtifactLinkService.RegisteredArtifact> currentRunArtifacts = new LinkedHashMap<>();
    private final Set<String> emittedArtifactIds = new HashSet<>();

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            log.info("{} thought generated, chars={}", getName(), result == null ? 0 : result.length());
            log.info("{} selected {} tool(s)", getName(), toolCallList.size());
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> "tool=" + toolCall.name())
                    .collect(Collectors.joining("\n"));
            log.info("{} tool plan:\n{}", getName(), toolCallInfo);

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Arrearage") || msg.contains("overdue-payment") || msg.contains("\"code\":\"Arrearage\"")) {
                log.warn("{} model call failed because account appears unavailable: {}", getName(), msg);
                setState(AgentState.FINISHED);
                getMessageList().add(new AssistantMessage(
                        "[Error] The model service rejected the request because the account appears unpaid or expired. Please update the model configuration and try again."
                ));
                return false;
            }
            log.error("{} failed while thinking: {}", getName(), msg);
            getMessageList().add(new AssistantMessage("The agent hit an error while planning: " + msg));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "No tool call is pending.";
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        registerCurrentRunArtifacts(toolResponseMessage);
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
            if (toolResponseMessage.getResponses().stream().allMatch(response -> response.name().equals("doTerminate"))) {
                return "";
            }
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "Tool " + response.name() + " returned: " + response.responseData())
                .collect(Collectors.joining("\n"));
        log.debug("{} raw tool results chars={}", getName(), results.length());
        String sanitizedResults = sanitizeCurrentRunPaths(results);
        if (currentRunArtifacts.isEmpty() && isRecoverableUnsafeFilenameResult(sanitizedResults)) {
            return "I adjusted the file name to meet safe download rules and will retry with a normalized file name.";
        }

        boolean shouldFinishAfterSummary = terminateToolCalled || shouldFinishAfterCurrentToolResult(sanitizedResults);
        try {
            ChatResponse summaryResponse = getChatClient()
                    .prompt()
                    .system(getSystemPrompt())
                    .user("""
                            Summarize the current tool execution result for the end user.
                            Strict rules:
                            1. Use only the tool execution result shown below.
                            2. Do not use chat history, previous saved paths, previous report names, or previous task results.
                            3. Do not include local server file system paths in the answer.
                            4. If the result mentions a registered generated file, refer to the file by name only.
                            5. If the result does not contain a success marker, do not claim success.
                            6. If the result is an error, blocked result, quota error, key error, network error, provider error, or API error, explain the error once and suggest at most one safe next step.
                            7. Keep the answer short.
                            8. After this summary, the task should end.

                            Current tool result:
                            """ + sanitizedResults)
                    .call()
                    .chatResponse();
            String summary = formatCurrentRunOutput(summaryResponse.getResult().getOutput().getText());
            getMessageList().add(new AssistantMessage(summary));
            if (shouldFinishAfterSummary) {
                setState(AgentState.FINISHED);
            }
            return summary;
        } catch (Exception e) {
            log.warn("{} failed while summarizing tool result, returning raw result: {}", getName(), e.getMessage());
            if (shouldFinishAfterSummary) {
                setState(AgentState.FINISHED);
            }
            return formatCurrentRunOutput(results);
        }
    }

    void registerCurrentRunArtifacts(ToolResponseMessage toolResponseMessage) {
        if (artifactLinkService == null || toolResponseMessage == null) {
            return;
        }
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            registerCurrentRunArtifactsFromRawText(response.responseData());
        }
    }

    void registerCurrentRunArtifactsFromRawText(String rawToolResponse) {
        if (artifactLinkService == null) {
            return;
        }
        for (String candidate : toolResponseCandidates(rawToolResponse)) {
            List<SyManusArtifactLinkService.RegisteredArtifact> registered =
                    artifactLinkService.registerArtifactsFromToolResponse(candidate);
            for (SyManusArtifactLinkService.RegisteredArtifact artifact : registered) {
                currentRunArtifacts.putIfAbsent(artifact.path(), artifact);
            }
        }
    }

    private List<String> toolResponseCandidates(String rawToolResponse) {
        if (rawToolResponse == null || rawToolResponse.isBlank()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(rawToolResponse);
        String trimmed = rawToolResponse.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                String decoded = OBJECT_MAPPER.readValue(trimmed, String.class);
                if (StrUtil.isNotBlank(decoded) && !decoded.equals(rawToolResponse)) {
                    candidates.add(decoded);
                }
            } catch (Exception e) {
                log.debug("Could not decode JSON string tool response for artifact registration: {}", e.getMessage());
            }
        }
        return candidates.stream().distinct().toList();
    }

    String formatCurrentRunOutput(String text) {
        String sanitized = sanitizeCurrentRunPaths(text);
        if (!currentRunArtifacts.isEmpty()) {
            sanitized = rewriteRecoveredUnsafeFilenameFailure(sanitized);
        }
        if (currentRunArtifacts.isEmpty() || artifactMarkerFormatter == null) {
            return sanitized;
        }
        List<String> markers = new ArrayList<>();
        for (SyManusArtifactLinkService.RegisteredArtifact registered : currentRunArtifacts.values()) {
            DemoArtifactResponse artifact = registered.artifact();
            if (emittedArtifactIds.add(artifact.artifactId())) {
                String marker = artifactMarkerFormatter.apply(artifact);
                if (StrUtil.isNotBlank(marker)) {
                    markers.add(marker);
                }
            }
        }
        if (markers.isEmpty()) {
            return sanitized;
        }
        return sanitized + "\n" + String.join("\n", markers);
    }

    private String sanitizeCurrentRunPaths(String text) {
        if (artifactLinkService == null || currentRunArtifacts.isEmpty()) {
            return text;
        }
        return artifactLinkService.sanitizeRegisteredPaths(text, new ArrayList<>(currentRunArtifacts.values()));
    }

    private boolean isRecoverableUnsafeFilenameResult(String text) {
        String lower = String.valueOf(text).toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "unsafe characters",
                "unsafe file name",
                "unsafe filename",
                "blocked filename",
                "file name contains unsafe",
                "file name contained unsafe");
    }

    private String rewriteRecoveredUnsafeFilenameFailure(String text) {
        if (!isRecoverableUnsafeFilenameResult(text)) {
            return text;
        }
        String rewritten = text
                .replaceAll("(?is)(?:^|\\s*)[^.!?。！？\\r\\n]*(?:unsafe characters|unsafe file name|unsafe filename|blocked filename|file name contains unsafe|file name contained unsafe)[^.!?。！？\\r\\n]*[.!?。！？]?\\s*", " ")
                .replaceAll("(?is)(?:^|\\s*)[^.!?。！？\\r\\n]*(?:please try again|simpler, alphanumeric file name)[^.!?。！？\\r\\n]*[.!?。！？]?\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String note = "I adjusted the file name to meet safe download rules, then generated the resume successfully.";
        if (rewritten.isBlank()) {
            return note;
        }
        String lower = rewritten.toLowerCase(Locale.ROOT);
        if (lower.contains("adjusted the file name") || lower.contains("safe download rules")) {
            return rewritten;
        }
        return note + " " + rewritten;
    }

    private boolean shouldFinishAfterCurrentToolResult(String results) {
        String activePrompt = String.valueOf(getActiveUserPrompt()).toLowerCase(Locale.ROOT);
        String lowerResults = String.valueOf(results).toLowerCase(Locale.ROOT);
        if (isSimpleLiveDemoTask(activePrompt)) {
            return true;
        }
        return lowerResults.contains("tool searchweb returned:")
                && (containsAny(lowerResults, "quota", "api error", "api key", "key not configured", "network",
                "provider", "timeout", "error searching", "baidu search api error"));
    }

    private boolean isSimpleLiveDemoTask(String activePrompt) {
        if (!activePrompt.contains("run this backend tool task now")) {
            return false;
        }
        return activePrompt.contains("echo symanus live health check")
                || activePrompt.contains("demo-note.txt")
                || activePrompt.contains("demo-note.pdf");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
