package com.seewhy.syaiagent.trace;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AgentTraceService {

    private static final int MAX_EVENTS_PER_CHAT = 200;

    private final ConcurrentMap<String, Deque<AgentTraceEvent>> eventsByChatId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Sinks.Many<AgentTraceEvent>> sinksByChatId = new ConcurrentHashMap<>();

    public AgentTraceEvent record(String chatId,
                                  AgentTraceStep step,
                                  AgentTraceStatus status,
                                  String message) {
        return record(chatId, step, status, message, Map.of());
    }

    public AgentTraceEvent record(String chatId,
                                  AgentTraceStep step,
                                  AgentTraceStatus status,
                                  String message,
                                  Map<String, Object> metadata) {
        String normalizedChatId = normalizeChatId(chatId);
        AgentTraceEvent event = new AgentTraceEvent(
                UUID.randomUUID().toString(),
                normalizedChatId,
                step,
                status,
                message,
                enrichLiveMetadata(metadata),
                Instant.now()
        );
        Deque<AgentTraceEvent> events = eventsByChatId.computeIfAbsent(normalizedChatId, ignored -> new ArrayDeque<>());
        synchronized (events) {
            events.addLast(event);
            while (events.size() > MAX_EVENTS_PER_CHAT) {
                events.removeFirst();
            }
        }
        sinksByChatId.computeIfAbsent(normalizedChatId, ignored -> Sinks.many().multicast().onBackpressureBuffer())
                .tryEmitNext(event);
        return event;
    }

    public List<AgentTraceEvent> getEvents(String chatId) {
        Deque<AgentTraceEvent> events = eventsByChatId.get(normalizeChatId(chatId));
        if (events == null) {
            return List.of();
        }
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public Flux<AgentTraceEvent> stream(String chatId) {
        String normalizedChatId = normalizeChatId(chatId);
        Flux<AgentTraceEvent> history = Flux.fromIterable(getEvents(normalizedChatId));
        Flux<AgentTraceEvent> live = sinksByChatId
                .computeIfAbsent(normalizedChatId, ignored -> Sinks.many().multicast().onBackpressureBuffer())
                .asFlux();
        return Flux.concat(history, live);
    }

    public void clear(String chatId) {
        String normalizedChatId = normalizeChatId(chatId);
        Deque<AgentTraceEvent> removed = eventsByChatId.remove(normalizedChatId);
        if (removed != null) {
            synchronized (removed) {
                removed.clear();
            }
        }
    }

    public List<AgentTraceEvent> getAllEvents() {
        List<AgentTraceEvent> allEvents = new ArrayList<>();
        eventsByChatId.values().forEach(events -> {
            synchronized (events) {
                allEvents.addAll(events);
            }
        });
        return allEvents.stream()
                .sorted((left, right) -> left.timestamp().compareTo(right.timestamp()))
                .toList();
    }

    private String normalizeChatId(String chatId) {
        return chatId == null || chatId.isBlank() ? "default" : chatId.strip();
    }

    private Map<String, Object> enrichLiveMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        enriched.putIfAbsent("source", "live");
        enriched.putIfAbsent("mode", "live");
        return enriched;
    }
}
