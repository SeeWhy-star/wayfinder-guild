package com.seewhy.syaiagent.trace;

import java.time.Instant;
import java.util.Map;

public record AgentTraceEvent(
        String traceId,
        String chatId,
        AgentTraceStep step,
        AgentTraceStatus status,
        String message,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public AgentTraceEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
