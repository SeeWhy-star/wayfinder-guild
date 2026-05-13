package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayfinderDemoServiceTest {

    private final WayfinderDemoService service = new WayfinderDemoService(true);

    @Test
    void demoTravelPlanUsesFrozenLiveKyotoFixture() {
        TravelPlan plan = service.demoTravelPlan();

        assertEquals("Kyoto", plan.destination());
        assertEquals("Shanghai", plan.departure());
        assertEquals(5, plan.days());
        assertEquals(BigDecimal.valueOf(15000), plan.budget().total());
        assertEquals("CNY", plan.budget().currency());
        assertEquals(5, plan.itineraryDays().size());
        assertTrue(plan.risks().stream().anyMatch(risk -> risk.contains("travelers")));
        assertTrue(plan.loadedSkills().contains("family-trip-planning"));
        assertTrue(plan.loadedSkills().contains("japan-travel"));
    }

    @Test
    void demoTraceUsesFrozenLiveTraceWithRequestedChatId() {
        var events = service.demoTrace("demo-visible-chat");

        assertTrue(events.size() >= 10);
        assertTrue(events.stream().allMatch(event -> event.chatId().equals("demo-visible-chat")));
        assertTrue(events.stream().anyMatch(event -> event.step() == AgentTraceStep.USER_INTENT_RECOGNITION));
        assertTrue(events.stream().anyMatch(event -> event.step() == AgentTraceStep.SKILL_LOADING));
        assertTrue(events.stream().anyMatch(event -> event.step() == AgentTraceStep.ITINERARY_GENERATION));
        assertTrue(events.stream().anyMatch(event -> event.metadata().containsKey("missingFields")));
        assertTrue(events.stream().allMatch(event -> event.metadata().get("source").equals("fixture")));
        assertTrue(events.stream().allMatch(event -> event.metadata().get("mode").equals("demo")));
    }

    @Test
    void demoEvalResultsUseFrozenLiveScoreFixture() {
        var results = service.demoEvalResults();

        assertEquals(8, results.size());
        assertTrue(results.stream().anyMatch(result ->
                result.rule().equals("case-alignment")
                        && result.passed()
                        && result.score() == 20
                        && result.message().contains("explicit destination")));
        assertTrue(results.stream().anyMatch(result ->
                result.rule().equals("clarifying-question")
                        && result.passed()
                        && result.message().contains("traveler count")));
    }

    @Test
    void demoRagExplainVariesAcrossDemoQuestions() {
        List<RagExplainResponse> responses = List.of(
                service.demoRagExplain("我和父母 3 个人 6 月去日本 7 天，预算 2 万，想轻松一点，怎么安排？", "family"),
                service.demoRagExplain("日本旅行交通券怎么选，JR Pass 一定划算吗？", "pass"),
                service.demoRagExplain("日本旅行遇到下雨天，有什么备选方案？", "rain"),
                service.demoRagExplain("低预算旅行怎么控制住宿、交通和餐饮？", "budget"),
                service.demoRagExplain("带老人小孩旅行有哪些风险要提前考虑？", "risk")
        );

        Set<String> rewrittenQueries = responses.stream()
                .map(RagExplainResponse::rewrittenQuery)
                .collect(Collectors.toSet());
        Set<String> topDocuments = responses.stream()
                .map(response -> response.documents().getFirst().documentId())
                .collect(Collectors.toSet());
        Set<String> answers = responses.stream()
                .map(RagExplainResponse::answer)
                .collect(Collectors.toSet());

        assertEquals(5, rewrittenQueries.size());
        assertEquals(5, topDocuments.size());
        assertEquals(5, answers.size());
        responses.forEach(response -> assertTrue(response.documents().size() >= 3));
    }

    @Test
    void demoRagExplainKeepsJapanFamilyAndRelevantDocuments() {
        RagExplainResponse response = service.demoRagExplain(
                "我和父母 3 个人 6 月去日本 7 天，预算 2 万，想轻松一点，怎么安排？",
                "rag-demo-family"
        );

        assertTrue(response.rewrittenQuery().contains("日本"));
        assertTrue(response.rewrittenQuery().contains("家庭") || response.rewrittenQuery().contains("轻松"));
        assertTrue(response.rewrittenQuery().contains("预算"));
        assertDocumentOrder(response, "japan-family-trip", "budget-travel-planning", "family-travel-risk-checklist");
        assertTrue(response.answer().contains("减少换酒店"));
        assertTrue(response.answer().contains("预算"));
    }

    @Test
    void demoRagExplainReturnsTransportPassCase() {
        RagExplainResponse response = service.demoRagExplain("日本旅行交通券怎么选，JR Pass 一定划算吗？", "rag-demo-pass");

        assertTrue(response.rewrittenQuery().contains("JR Pass"));
        assertTrue(response.rewrittenQuery().contains("IC卡"));
        assertEquals("japan-transport-pass", response.documents().getFirst().documentId());
        assertTrue(response.answer().contains("先看路线"));
    }

    @Test
    void demoRagExplainReturnsRainyDayCase() {
        RagExplainResponse response = service.demoRagExplain("日本旅行遇到下雨天，有什么备选方案？", "rag-demo-rain");

        assertTrue(response.rewrittenQuery().contains("下雨天"));
        assertTrue(response.rewrittenQuery().contains("室内活动"));
        assertEquals("rainy-day-backup-plan", response.documents().getFirst().documentId());
        assertTrue(response.answer().contains("室内文化场馆"));
    }

    @Test
    void demoRagExplainReturnsBudgetCase() {
        RagExplainResponse response = service.demoRagExplain("低预算旅行怎么控制住宿、交通和餐饮？", "rag-demo-budget");

        assertTrue(response.rewrittenQuery().contains("低预算"));
        assertTrue(response.rewrittenQuery().contains("成本拆分"));
        assertEquals("budget-travel-planning", response.documents().getFirst().documentId());
        assertTrue(response.answer().contains("预算上限"));
    }

    @Test
    void demoRagExplainRiskQuestionDoesNotInjectJapanTripSlots() {
        RagExplainResponse response = service.demoRagExplain("带老人小孩旅行有哪些风险要提前考虑？", "rag-demo-risk");

        assertTrue(response.rewrittenQuery().contains("老人"));
        assertTrue(response.rewrittenQuery().contains("小孩"));
        assertTrue(response.rewrittenQuery().contains("风险"));
        assertFalse(response.rewrittenQuery().contains("日本"));
        assertEquals("family-travel-risk-checklist", response.documents().getFirst().documentId());
        assertTrue(response.answer().contains("健康"));
        assertTrue(response.answer().contains("证件"));
    }

    @Test
    void demoRagExplainAnswersEnglishQueryInEnglish() {
        RagExplainResponse response = service.demoRagExplain(
                "How should I decide whether a JR Pass is worth it for a Japan trip?",
                "rag-demo-en"
        );

        assertTrue(response.rewrittenQuery().contains("Japan JR Pass"));
        assertEquals("japan-transport-pass", response.documents().getFirst().documentId());
        assertTrue(response.answer().startsWith("Choose transport passes from the route"));
        assertFalse(response.answer().contains("交通券先看路线"));
    }

    private void assertDocumentOrder(RagExplainResponse response, String first, String second, String third) {
        assertEquals(first, response.documents().get(0).documentId());
        assertEquals(second, response.documents().get(1).documentId());
        assertEquals(third, response.documents().get(2).documentId());
        assertNotEquals(response.documents().get(0).score(), response.documents().get(1).score());
    }
}
