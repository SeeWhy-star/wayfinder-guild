package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.WayfinderPromptConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Service
@Slf4j
public class TravelChatService {

    private final ChatClient chatClient;
    private final TravelDraftStateService travelDraftStateService;

    public TravelChatService(@Qualifier("travelChatClient") ChatClient chatClient,
                             TravelDraftStateService travelDraftStateService) {
        this.chatClient = chatClient;
        this.travelDraftStateService = travelDraftStateService;
    }

    public String chat(String message, String chatId) {
        log.info("Travel chat request [{}], message chars={}", chatId, message == null ? 0 : message.length());

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(WayfinderPromptConstant.TURN_INSTRUCTION)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = postProcessResponse(chatId, message, chatResponse.getResult().getOutput().getText());
        log.info("Travel chat response [{}], chars={}", chatId, content == null ? 0 : content.length());
        return content;
    }

    public Flux<String> streamChat(String message, String chatId) {
        log.info("Travel chat stream request [{}], message chars={}", chatId, message == null ? 0 : message.length());

        return chatClient
                .prompt()
                .system(WayfinderPromptConstant.TURN_INSTRUCTION)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doOnNext(chunk -> log.debug("流式回复[{}]: {}", chatId, chunk))
                .transform(content -> filterPlanDraftAndAppendTrustedDraft(content, chatId, message));
    }

    String postProcessResponse(String chatId, String message, String content) {
        return travelDraftStateService.postProcessResponse(chatId, message, content);
    }

    private Flux<String> filterPlanDraftAndAppendTrustedDraft(Flux<String> content, String chatId, String message) {
        return Flux.create(sink -> {
            StringBuilder rawContent = new StringBuilder();
            StringBuilder lineBuffer = new StringBuilder();
            content.subscribe(
                    chunk -> {
                        rawContent.append(chunk);
                        lineBuffer.append(chunk);
                        emitCompleteVisibleLines(sink, lineBuffer);
                    },
                    sink::error,
                    () -> {
                        emitVisibleTail(sink, lineBuffer);
                        String suffix = travelDraftStateService.streamCompletionSuffix(chatId, message, rawContent.toString());
                        if (!suffix.isBlank()) {
                            sink.next(suffix);
                        }
                        sink.complete();
                    }
            );
        });
    }

    private void emitCompleteVisibleLines(FluxSink<String> sink, StringBuilder lineBuffer) {
        int newlineIndex = indexOfNewline(lineBuffer);
        while (newlineIndex >= 0) {
            String line = lineBuffer.substring(0, newlineIndex + 1);
            lineBuffer.delete(0, newlineIndex + 1);
            if (!isPlanDraftLine(line)) {
                sink.next(line);
            }
            newlineIndex = indexOfNewline(lineBuffer);
        }
    }

    private void emitVisibleTail(FluxSink<String> sink, StringBuilder lineBuffer) {
        if (lineBuffer.isEmpty()) {
            return;
        }
        String tail = lineBuffer.toString();
        lineBuffer.setLength(0);
        if (!isPlanDraftLine(tail)) {
            sink.next(tail);
        }
    }

    private int indexOfNewline(StringBuilder builder) {
        for (int index = 0; index < builder.length(); index++) {
            if (builder.charAt(index) == '\n') {
                return index;
            }
        }
        return -1;
    }

    boolean isPlanDraftLine(String line) {
        return line != null && line.stripLeading().toUpperCase().startsWith("PLAN_DRAFT:");
    }
}
