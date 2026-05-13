package com.seewhy.syaiagent.model.rpg;

public record RpgEvalSampleResult(
        String rule,
        boolean passed,
        int score,
        int maxScore,
        String message
) {
}
