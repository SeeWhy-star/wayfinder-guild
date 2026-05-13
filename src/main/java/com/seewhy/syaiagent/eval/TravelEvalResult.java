package com.seewhy.syaiagent.eval;

import java.util.List;

public record TravelEvalResult(
        String caseId,
        String caseName,
        int score,
        int maxScore,
        boolean passed,
        List<TravelEvalRuleResult> rules
) {
    public TravelEvalResult {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
