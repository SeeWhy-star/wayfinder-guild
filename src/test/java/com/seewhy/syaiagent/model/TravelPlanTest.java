package com.seewhy.syaiagent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelPlanTest {

    @Test
    void compactConstructorNormalizesNullableLists() {
        TravelPlan plan = new TravelPlan(
                "summary",
                "日本",
                "6月",
                7,
                3,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertTrue(plan.itineraryDays().isEmpty());
        assertTrue(plan.transportation().isEmpty());
        assertTrue(plan.accommodation().isEmpty());
        assertTrue(plan.risks().isEmpty());
        assertTrue(plan.alternatives().isEmpty());
        assertTrue(plan.loadedSkills().isEmpty());
    }

    @Test
    void budgetDefaultsCurrencyAndItems() {
        TravelPlan.Budget budget = new TravelPlan.Budget(null, null, null, "estimate");

        assertEquals("CNY", budget.currency());
        assertTrue(budget.items().isEmpty());
    }
}
