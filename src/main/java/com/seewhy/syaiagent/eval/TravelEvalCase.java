package com.seewhy.syaiagent.eval;

import java.math.BigDecimal;
import java.util.List;

public record TravelEvalCase(
        String id,
        String name,
        String input,
        String expectedDestination,
        Integer expectedDays,
        Integer expectedTravelers,
        BigDecimal expectedBudgetTotal,
        String expectedCurrency,
        List<String> expectedSkills,
        boolean requiresClarifyingQuestion,
        List<String> disallowedTools
) {
    public TravelEvalCase {
        expectedCurrency = expectedCurrency == null || expectedCurrency.isBlank() ? "CNY" : expectedCurrency;
        expectedSkills = expectedSkills == null ? List.of() : List.copyOf(expectedSkills);
        disallowedTools = disallowedTools == null ? List.of() : List.copyOf(disallowedTools);
    }
}
