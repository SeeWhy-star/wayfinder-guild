package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.rag.QueryRewriter;
import com.seewhy.syaiagent.rag.TravelDocumentLoader;
import com.seewhy.syaiagent.trace.AgentTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TravelRagServiceTest {

    @Test
    void explainRagReturnsGracefulFallbackWhenVectorStoreUnavailable() {
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        AgentTraceService traceService = new AgentTraceService();
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        when(queryRewriter.doQueryRewrite("Japan travel tips")).thenReturn("rewritten Japan travel tips");
        TravelDocumentLoader documentLoader = mock(TravelDocumentLoader.class);
        when(documentLoader.loadMarkdowns()).thenReturn(java.util.List.of());

        TravelRagService service = new TravelRagService(chatClient, vectorStoreProvider, queryRewriter, traceService, documentLoader, "pgvector");

        RagExplainResponse response = service.explainRag("Japan travel tips", "rag-test");

        assertEquals("rag-test", response.chatId());
        assertEquals("lightweight-fallback", response.mode());
        assertEquals("Japan travel tips", response.originalQuery());
        assertEquals("rewritten Japan travel tips", response.rewrittenQuery());
        assertTrue(response.degraded());
        assertTrue(response.documents().isEmpty());
        assertTrue(traceService.getEvents("rag-test").size() >= 2);
    }

    @Test
    void chatWithRagFallsBackWhenPgVectorUnavailable() {
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        AgentTraceService traceService = new AgentTraceService();
        TravelDocumentLoader documentLoader = mock(TravelDocumentLoader.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        when(documentLoader.loadMarkdowns()).thenReturn(java.util.List.of());

        TravelRagService service = new TravelRagService(chatClient, vectorStoreProvider, queryRewriter, traceService, documentLoader, "pgvector");

        String response = service.chatWithRag("Japan travel tips", "rag-chat-test");

        assertTrue(response.contains("Lightweight RAG"));
        assertTrue(traceService.getEvents("rag-chat-test").size() >= 2);
    }

    @Test
    void lightweightExplainRanksMarkdownMetadataForChineseTravelQuestions() {
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        AgentTraceService traceService = new AgentTraceService();
        TravelDocumentLoader documentLoader = mock(TravelDocumentLoader.class);
        when(documentLoader.loadMarkdowns()).thenReturn(List.of(
                new Document("下雨天可以准备室内活动、商场、博物馆和轻松餐饮作为备选方案。", Map.of(
                        "id", "rainy-day-backup-plan",
                        "title", "雨天旅行备选方案设计",
                        "tags", List.of("雨天", "备选方案", "天气"),
                        "updated", "2026-05-10",
                        "source_type", "curated-demo",
                        "source", "classpath:document/rainy-day-backup-plan.md"
                )),
                new Document("预算规划需要拆分住宿、交通、餐饮和应急金。", Map.of(
                        "id", "budget-travel-planning",
                        "title", "低预算旅行规划与成本控制",
                        "tags", List.of("预算", "交通", "餐饮"),
                        "updated", "2026-05-10",
                        "source_type", "curated-demo",
                        "source", "classpath:document/budget-travel-planning.md"
                ))
        ));

        TravelRagService service = new TravelRagService(chatClient, vectorStoreProvider, queryRewriter, traceService, documentLoader, "lightweight");

        RagExplainResponse response = service.explainRag("日本旅行遇到下雨天，有什么备选方案？", "rag-lightweight-test");

        assertEquals("lightweight", response.mode());
        assertEquals("rainy-day-backup-plan", response.documents().getFirst().documentId());
        assertEquals(List.of("雨天", "备选方案", "天气"), response.documents().getFirst().tags());
        assertEquals("curated-demo", response.documents().getFirst().sourceType());
    }
}
