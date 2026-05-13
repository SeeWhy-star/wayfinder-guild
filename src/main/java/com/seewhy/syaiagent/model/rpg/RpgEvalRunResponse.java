package com.seewhy.syaiagent.model.rpg;

import com.seewhy.syaiagent.eval.TravelEvalCase;
import com.seewhy.syaiagent.eval.TravelEvalResult;
import com.seewhy.syaiagent.model.TravelPlan;

import java.util.List;

public record RpgEvalRunResponse(
        TravelEvalCase evalCase,
        String input,
        TravelPlan plan,
        TravelEvalResult result,
        List<String> observedToolCalls
) {
    public RpgEvalRunResponse {
        observedToolCalls = observedToolCalls == null ? List.of() : List.copyOf(observedToolCalls);
    }
}
