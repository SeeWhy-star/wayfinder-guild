package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BudgetEstimatorServiceTest {

    private final BudgetEstimatorService service = new BudgetEstimatorService();

    @Test
    void estimateAddsDefaultBudgetWhenMissing() {
        TravelPlan plan = minimalPlan(null);

        TravelPlan result = service.estimate(plan);

        assertNotNull(result.budget());
        assertEquals("CNY", result.budget().currency());
    }

    @Test
    void estimateSplitsKnownBudgetWhenItemsMissing() {
        TravelPlan plan = minimalPlan(new TravelPlan.Budget(BigDecimal.valueOf(20000), "CNY", List.of(), "估算"));

        TravelPlan result = service.estimate(plan);

        assertFalse(result.budget().items().isEmpty());
        assertEquals(new BigDecimal("7000.00"), result.budget().items().getFirst().amount());
    }

    private TravelPlan minimalPlan(TravelPlan.Budget budget) {
        return new TravelPlan("summary", "日本", null, 7, 3, budget, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
