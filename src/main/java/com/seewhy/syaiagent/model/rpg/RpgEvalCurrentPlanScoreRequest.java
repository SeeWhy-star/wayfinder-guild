package com.seewhy.syaiagent.model.rpg;

import com.seewhy.syaiagent.model.TravelPlan;

import java.util.List;

public record RpgEvalCurrentPlanScoreRequest(
        String input,
        String chatId,
        TravelPlan plan,
        List<String> observedToolCalls
) {
    public RpgEvalCurrentPlanScoreRequest {
        observedToolCalls = observedToolCalls == null ? List.of() : List.copyOf(observedToolCalls);
    }
}
