package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportComposerServiceTest {

    private final ReportComposerService service = new ReportComposerService();

    @Test
    void composeAddsSummaryAndFollowUpAlternative() {
        TravelPlan plan = new TravelPlan("", "成都", null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        TravelRequirement requirement = new TravelRequirement("成都旅行", true, List.of("days"), TravelTaskType.STRUCTURED_PLAN);

        TravelPlan result = service.compose(plan, requirement);

        assertFalse(result.summary().isBlank());
        assertTrue(result.alternatives().stream().anyMatch(item -> item.contains("days")));
    }
}
