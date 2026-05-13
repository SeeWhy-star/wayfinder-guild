package com.seewhy.syaiagent.model;

public record RagExplainRequest(
        String message,
        String chatId
) {
}
