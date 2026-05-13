package com.seewhy.syaiagent.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTraceServiceTest {

    @Test
    void recordStoresEventsByChatId() {
        AgentTraceService service = new AgentTraceService();

        service.record("chat-a", AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.STARTED, "start");
        service.record("chat-b", AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.STARTED, "rag");
        service.record("chat-a", AgentTraceStep.SKILL_LOADING, AgentTraceStatus.COMPLETED, "skills", Map.of("count", 2));

        assertEquals(2, service.getEvents("chat-a").size());
        assertEquals(1, service.getEvents("chat-b").size());
        assertEquals(AgentTraceStep.SKILL_LOADING, service.getEvents("chat-a").get(1).step());
        assertEquals(2, service.getEvents("chat-a").get(1).metadata().get("count"));
    }

    @Test
    void recordKeepsRecentEventsOnly() {
        AgentTraceService service = new AgentTraceService();

        for (int i = 0; i < 205; i++) {
            service.record("chat", AgentTraceStep.RISK_CHECK, AgentTraceStatus.COMPLETED, "event-" + i);
        }

        assertEquals(200, service.getEvents("chat").size());
        assertEquals("event-5", service.getEvents("chat").getFirst().message());
    }

    @Test
    void streamStartsWithHistory() {
        AgentTraceService service = new AgentTraceService();
        service.record("chat", AgentTraceStep.BUDGET_CHECK, AgentTraceStatus.COMPLETED, "budget");

        AgentTraceEvent first = service.stream("chat").blockFirst();

        assertEquals(AgentTraceStep.BUDGET_CHECK, first.step());
        assertEquals("budget", first.message());
    }

    @Test
    void clearRemovesHistory() {
        AgentTraceService service = new AgentTraceService();
        service.record("chat", AgentTraceStep.REPORT_GENERATION, AgentTraceStatus.STARTED, "report");

        assertFalse(service.getEvents("chat").isEmpty());
        service.clear("chat");

        assertTrue(service.getEvents("chat").isEmpty());
    }
}
