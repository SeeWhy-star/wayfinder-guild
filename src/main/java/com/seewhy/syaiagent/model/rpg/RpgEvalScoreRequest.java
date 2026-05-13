package com.seewhy.syaiagent.model.rpg;

import com.seewhy.syaiagent.model.TravelPlan;

import java.util.List;

public record RpgEvalScoreRequest(
        String caseId,
        String chatId,
        TravelPlan plan,
        List<String> observedToolCalls
) {
    public RpgEvalScoreRequest {
        observedToolCalls = observedToolCalls == null ? List.of() : List.copyOf(observedToolCalls);
    }
}
