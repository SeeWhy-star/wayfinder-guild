package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.eval.TravelEvalHarness;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.rpg.RpgEvalCurrentPlanScoreRequest;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunResponse;
import com.seewhy.syaiagent.model.rpg.RpgEvalScoreRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpgEvalServiceTest {

    private final TravelEvalHarness harness = new TravelEvalHarness(new ObjectMapper());

    @Test
    void runEvalReturnsCasePlanAndRuleResults() {
        WayfinderTravelFacade facade = mock(WayfinderTravelFacade.class);
        when(facade.doStructuredPlan(anyString(), anyString())).thenReturn(strongJapanPlan());
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), facade);

        RpgEvalRunResponse response = service.runEval("jp-family-relaxed-budget", null);

        assertEquals("jp-family-relaxed-budget", response.evalCase().id());
        assertEquals("我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点", response.input());
        assertNotNull(response.plan());
        assertNotNull(response.result());
        assertEquals("jp-family-relaxed-budget", response.result().caseId());
        assertContainsRule(response, "case-alignment");
        assertContainsRule(response, "clarifying-question");
        assertContainsRule(response, "structured-itinerary");
        assertContainsRule(response, "budget-reasonableness");
        assertContainsRule(response, "risk-reminders");
        assertContainsRule(response, "unsafe-claims");
        assertContainsRule(response, "disallowed-tools");
        assertContainsRule(response, "expected-skills");
    }

    @Test
    void runEvalSupportsMissingCoreInfoCase() {
        WayfinderTravelFacade facade = mock(WayfinderTravelFacade.class);
        when(facade.doStructuredPlan(anyString(), anyString())).thenReturn(clarifyingPlan());
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), facade);

        RpgEvalRunResponse response = service.runEval("missing-core-info", null);

        assertEquals("missing-core-info", response.evalCase().id());
        assertEquals("帮我安排一次旅行", response.input());
        assertTrue(response.result().rules().stream()
                .anyMatch(rule -> rule.rule().equals("clarifying-question") && rule.passed()));
    }

    @Test
    void runEvalThrowsForUnknownCase() {
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), mock(WayfinderTravelFacade.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.runEval("missing-case", null));

        assertTrue(error.getMessage().contains("Eval case not found"));
    }

    @Test
    void scorePlanReturnsRuleResultsForExistingPlanWithoutRegenerating() {
        WayfinderTravelFacade facade = mock(WayfinderTravelFacade.class);
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), facade);

        RpgEvalRunResponse response = service.scorePlan(new RpgEvalScoreRequest(
                "jp-family-relaxed-budget",
                "chat-test",
                strongJapanPlan(),
                List.of()
        ));

        assertEquals("jp-family-relaxed-budget", response.evalCase().id());
        assertNotNull(response.plan());
        assertNotNull(response.result());
        assertContainsRule(response, "case-alignment");
        assertContainsRule(response, "clarifying-question");
        assertContainsRule(response, "structured-itinerary");
        assertContainsRule(response, "budget-reasonableness");
        assertContainsRule(response, "risk-reminders");
        assertContainsRule(response, "unsafe-claims");
        assertContainsRule(response, "disallowed-tools");
        assertContainsRule(response, "expected-skills");
        verify(facade, never()).doStructuredPlan(anyString(), anyString());
    }

    @Test
    void getRulesIncludesCaseAlignmentAndTotalsOneHundred() {
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), mock(WayfinderTravelFacade.class));

        assertTrue(service.getRules().stream().anyMatch(rule -> rule.id().equals("case-alignment")));
        assertEquals(100, service.getRules().stream().mapToInt(rule -> rule.maxScore()).sum());
    }

    @Test
    void scorePlanThrowsForUnknownCaseWithoutRegenerating() {
        WayfinderTravelFacade facade = mock(WayfinderTravelFacade.class);
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), facade);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.scorePlan(new RpgEvalScoreRequest("missing-case", "chat-test", strongJapanPlan(), List.of())));

        assertTrue(error.getMessage().contains("Eval case not found"));
        verify(facade, never()).doStructuredPlan(anyString(), anyString());
    }

    @Test
    void scoreCurrentPlanBuildsAdHocCaseWithoutRegenerating() {
        WayfinderTravelFacade facade = mock(WayfinderTravelFacade.class);
        RpgEvalService service = new RpgEvalService(harness, new WayfinderDemoService(false), facade);

        RpgEvalRunResponse response = service.scoreCurrentPlan(new RpgEvalCurrentPlanScoreRequest(
                "Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget.",
                "chat-current",
                currentKyotoPlan(),
                List.of()
        ));

        assertEquals("current-plan", response.evalCase().id());
        assertEquals("Kyoto", response.evalCase().expectedDestination());
        assertEquals(5, response.evalCase().expectedDays());
        assertEquals(BigDecimal.valueOf(15000), response.evalCase().expectedBudgetTotal());
        assertEquals(null, response.evalCase().expectedTravelers());
        assertContainsRule(response, "request-coverage");
        assertFalse(response.result().rules().stream().anyMatch(rule -> rule.rule().equals("case-alignment")));
        assertTrue(response.result().rules().stream()
                .anyMatch(rule -> rule.rule().equals("request-coverage") && rule.passed()));
        assertFalse(response.result().rules().stream()
                .anyMatch(rule -> String.valueOf(rule.message()).toLowerCase().contains("destination mismatch")));
        verify(facade, never()).doStructuredPlan(anyString(), anyString());
    }

    private void assertContainsRule(RpgEvalRunResponse response, String ruleId) {
        assertTrue(response.result().rules().stream().anyMatch(rule -> rule.rule().equals(ruleId)),
                "Expected rule " + ruleId);
    }

    private TravelPlan strongJapanPlan() {
        return new TravelPlan(
                "三位家庭成员 6 月日本 7 天轻松行程。",
                "日本",
                "上海",
                7,
                3,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(20000),
                        "CNY",
                        List.of(
                                new TravelPlan.BudgetItem("交通", BigDecimal.valueOf(8000), "机票和当地交通估算"),
                                new TravelPlan.BudgetItem("住宿", BigDecimal.valueOf(7000), "舒适型酒店估算")
                        ),
                        "价格仅为规划估算，请以实时查询为准。"
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "抵达东京休整", List.of(), List.of("简餐"), "东京", "机场线", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "东京经典轻松游", List.of(), List.of("寿司"), "东京", "地铁", "relaxed", List.of())
                ),
                List.of("城市间优先新干线或少换乘路线。"),
                List.of("选择车站附近、减少换酒店次数。"),
                List.of("6 月天气和航班价格可能变化，签证政策需以官方信息为准。"),
                List.of("若父母体力一般，可减少大阪或京都日程。"),
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel")
        );
    }

    private TravelPlan clarifyingPlan() {
        return new TravelPlan(
                "需要确认目的地、天数、人数和预算后再细化。",
                null,
                null,
                null,
                null,
                null,
                List.of(new TravelPlan.ItineraryDay(1, "待定", List.of(), List.of(), null, null, null, List.of("请补充旅行城市"))),
                List.of(),
                List.of(),
                List.of("请补充预算、天数和出发地。"),
                List.of("可先提供通用目的地候选。"),
                List.of()
        );
    }

    private TravelPlan currentKyotoPlan() {
        return new TravelPlan(
                "A relaxed 5-day Kyoto plan. Budget is estimated for 2 people.",
                "京都",
                "Shanghai",
                5,
                null,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(15000),
                        "CNY",
                        List.of(
                                new TravelPlan.BudgetItem("Flights", BigDecimal.valueOf(6000), "estimated for 2 people"),
                                new TravelPlan.BudgetItem("Hotel", BigDecimal.valueOf(4000), "estimated for 2 people")
                        ),
                        "Estimated for 2 people; actual cost changes with travelers."
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "Arrival", List.of(), List.of(), "Kyoto", "train", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "Higashiyama", List.of(), List.of(), "Kyoto", "bus", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(3, "Arashiyama", List.of(), List.of(), "Kyoto", "train", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(4, "Kinkakuji", List.of(), List.of(), "Kyoto", "bus", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(5, "Return", List.of(), List.of(), null, "train", "relaxed", List.of())
                ),
                List.of("JR and bus"),
                List.of("Kyoto Station hotel"),
                List.of("Prices and traveler count need confirmation."),
                List.of("Add Nara if days increase."),
                List.of("family-trip-planning", "japan-travel", "relaxed-travel", "budget-travel")
        );
    }
}
