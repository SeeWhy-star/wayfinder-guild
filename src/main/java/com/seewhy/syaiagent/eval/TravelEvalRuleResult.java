package com.seewhy.syaiagent.eval;

public record TravelEvalRuleResult(
        String rule,
        boolean passed,
        int score,
        int maxScore,
        String message
) {
}
