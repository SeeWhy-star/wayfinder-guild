package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAdvisorServiceTest {

    private final RiskAdvisorService service = new RiskAdvisorService();

    @Test
    void adviseAddsMissingFieldRiskWithoutRepeatingOutputGuardrail() {
        TravelPlan plan = new TravelPlan("已完成结构化旅行规划", "日本", null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        TravelRequirement requirement = new TravelRequirement("去日本旅行", true, List.of("days", "budget"), TravelTaskType.STRUCTURED_PLAN);

        TravelPlan result = service.advise(plan, requirement);

        assertEquals("已完成结构化旅行规划", result.summary());
        assertTrue(result.risks().stream().anyMatch(risk -> risk.contains("days") && risk.contains("budget")));
    }
}
