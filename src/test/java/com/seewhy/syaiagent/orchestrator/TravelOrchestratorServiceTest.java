package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelOrchestratorServiceTest {

    @Test
    void generatePlanRoutesThroughExpertServices() {
        RequirementCollectorService requirementCollector = mock(RequirementCollectorService.class);
        ItineraryPlannerService itineraryPlanner = mock(ItineraryPlannerService.class);
        BudgetEstimatorService budgetEstimator = mock(BudgetEstimatorService.class);
        RiskAdvisorService riskAdvisor = mock(RiskAdvisorService.class);
        ReportComposerService reportComposer = mock(ReportComposerService.class);
        AgentTraceService traceService = new AgentTraceService();
        TravelOrchestratorService orchestrator = new TravelOrchestratorService(
                requirementCollector,
                itineraryPlanner,
                budgetEstimator,
                riskAdvisor,
                reportComposer,
                traceService
        );

        TravelRequirement requirement = new TravelRequirement(
                "上海出发去日本 7 天旅行，3 人，预算 2 万",
                true,
                List.of(),
                TravelTaskType.STRUCTURED_PLAN,
                "上海",
                "日本",
                7,
                3,
                BigDecimal.valueOf(20_000),
                "CNY"
        );
        TravelPlan planned = plan("planned");
        TravelPlan budgeted = plan("budgeted");
        TravelPlan risked = plan("risked");
        TravelPlan composed = plan("composed");

        when(requirementCollector.collect("日本 7 天旅行")).thenReturn(requirement);
        when(itineraryPlanner.plan(requirement, "chat-1")).thenReturn(planned);
        when(budgetEstimator.estimate(planned)).thenReturn(budgeted);
        when(riskAdvisor.advise(budgeted, requirement)).thenReturn(risked);
        when(reportComposer.compose(risked, requirement)).thenReturn(composed);

        TravelPlan result = orchestrator.generatePlan("日本 7 天旅行", "chat-1");

        assertEquals("composed", result.summary());
        assertFalse(traceService.getEvents("chat-1").isEmpty());
        var intentEvent = traceService.getEvents("chat-1").stream()
                .filter(event -> event.step() == AgentTraceStep.USER_INTENT_RECOGNITION)
                .filter(event -> event.status() == AgentTraceStatus.COMPLETED)
                .findFirst()
                .orElseThrow();
        assertEquals("live", intentEvent.metadata().get("source"));
        assertEquals("上海", intentEvent.metadata().get("departure"));
        assertEquals("日本", intentEvent.metadata().get("destination"));
        assertEquals(7, intentEvent.metadata().get("days"));
        assertEquals(BigDecimal.valueOf(20_000), intentEvent.metadata().get("budgetTotal"));
        verify(requirementCollector).collect("日本 7 天旅行");
        verify(itineraryPlanner).plan(requirement, "chat-1");
        verify(budgetEstimator).estimate(planned);
        verify(riskAdvisor).advise(budgeted, requirement);
        verify(reportComposer).compose(risked, requirement);
    }

    private TravelPlan plan(String summary) {
        return new TravelPlan(
                summary,
                "日本",
                null,
                7,
                3,
                new TravelPlan.Budget(BigDecimal.valueOf(20000), "CNY", List.of(), "估算"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
