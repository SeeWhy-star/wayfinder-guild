package com.seewhy.syaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.seewhy.syaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;
    private String activeUserPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /** 会话 ID，与 chatMemory 一起使用时用于多轮对话 */
    private String conversationChatId;
    /** 对话记忆，与 conversationChatId 一起使用时加载/保存多轮上下文 */
    private ChatMemory conversationChatMemory;
    private Function<String, String> streamChunkFormatter;
    private static final int MAX_HISTORY_LOAD = 20;
    private static final int MAX_HISTORY_SAVE = 30;

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
        public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        this.activeUserPrompt = userPrompt;
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 若有对话记忆则加载近期历史，再追加当前用户消息
        if (conversationChatMemory != null && StrUtil.isNotBlank(conversationChatId)) {
            List<Message> history = conversationChatMemory.get(conversationChatId);
            if (history != null && !history.isEmpty()) {
                if (history.size() > MAX_HISTORY_LOAD) {
                    history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_LOAD, history.size()));
                }
                messageList = sanitizeMessagesForApi(new ArrayList<>(history));
                log.debug("Loaded {} history messages for chatId {}", messageList.size(), conversationChatId);
            }
        }
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                // 仅在有实际可读内容时才记录和推送给前端，避免把内部“思考完成”等中间状态暴露给用户
                if (StrUtil.isNotBlank(stepResult)) {
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                }
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (IOException ioe) {
                log.debug("SSE client disconnected during initial send: {}", ioe.getMessage());
                try {
                    sseEmitter.complete();
                } catch (Exception ignore) {
                }
                return;
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 若有对话记忆则加载近期历史，再追加当前用户消息
            if (conversationChatMemory != null && StrUtil.isNotBlank(conversationChatId)) {
                List<Message> history = conversationChatMemory.get(conversationChatId);
                if (history != null && !history.isEmpty()) {
                    if (history.size() > MAX_HISTORY_LOAD) {
                        history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_LOAD, history.size()));
                    }
                    messageList = sanitizeMessagesForApi(new ArrayList<>(history));
                    log.debug("Loaded {} history messages for chatId {}", messageList.size(), conversationChatId);
                }
            }
            this.activeUserPrompt = userPrompt;
            messageList.add(new UserMessage(userPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环：每一步一旦有可读内容就立刻流式推送给前端，提升响应速度
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    // 仅在有用户可读内容时才输出，避免把纯“思考完成”等内部状态暴露出去
                    if (StrUtil.isNotBlank(stepResult)) {
                        String formattedStepResult = formatStreamChunk(stepResult);
                        results.add(formattedStepResult);
                        try {
                            sseEmitter.send(formattedStepResult);
                        } catch (IOException ioe) {
                            log.debug("SSE client disconnected while sending: {}", ioe.getMessage());
                            break;
                        }
                    }
                }
                // 若达到最大步骤则附加一句简短说明
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    String tip = "本次任务已达到预设的最大思考步数，如需更深入处理可以换一种说法再试一次。";
                    results.add(tip);
                    try {
                        sseEmitter.send(tip);
                    } catch (IOException ioe) {
                        log.debug("SSE client disconnected while sending tip: {}", ioe.getMessage());
                    }
                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    log.debug("SSE send error during error handling: {}", ex.getMessage());
                    sseEmitter.complete();
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    private String formatStreamChunk(String chunk) {
        if (streamChunkFormatter == null) {
            return chunk;
        }
        try {
            String formatted = streamChunkFormatter.apply(chunk);
            return formatted == null ? chunk : formatted;
        } catch (Exception e) {
            log.warn("Failed to format stream chunk: {}", e.getMessage());
            return chunk;
        }
    }

    /**
     * 规整消息列表，保证发往 API 时满足：每个 role=tool 的消息前必须有带 tool_calls 的 assistant 消息。
     * 避免因历史截断导致出现“孤立的 tool 消息”而触发 DashScope 的 InvalidParameter 错误。
     */
    protected List<Message> sanitizeMessagesForApi(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<Message> result = new ArrayList<>();
        for (Message m : messages) {
            if (m instanceof ToolResponseMessage) {
                if (!result.isEmpty()) {
                    Message last = result.get(result.size() - 1);
                    if (last instanceof AssistantMessage) {
                        List<AssistantMessage.ToolCall> toolCalls = ((AssistantMessage) last).getToolCalls();
                        if (toolCalls != null && !toolCalls.isEmpty()) {
                            result.add(m);
                        }
                    }
                }
            } else {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * 清理资源：若有对话记忆则把本轮消息写入记忆（按 chatId 持久化）
     */
    protected void cleanup() {
        if (conversationChatMemory != null && StrUtil.isNotBlank(conversationChatId) && !messageList.isEmpty()) {
            try {
                List<Message> toSave = getMessageList();
                if (toSave.size() > MAX_HISTORY_SAVE) {
                    toSave = new ArrayList<>(toSave.subList(toSave.size() - MAX_HISTORY_SAVE, toSave.size()));
                }
                toSave = sanitizeMessagesForApi(toSave);
                conversationChatMemory.clear(conversationChatId);
                conversationChatMemory.add(conversationChatId, toSave);
                log.debug("Saved {} messages for chatId {}", toSave.size(), conversationChatId);
            } catch (Exception e) {
                log.warn("Failed to save conversation for chatId {}: {}", conversationChatId, e.getMessage());
            }
        }
    }
}
