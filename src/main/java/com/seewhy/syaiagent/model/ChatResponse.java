package com.seewhy.syaiagent.model;

public class ChatResponse {

    private final String chatId;
    private final String content;
    private final long timestamp;

    public ChatResponse(String chatId, String content) {
        this.chatId = chatId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getChatId() {
        return chatId;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
