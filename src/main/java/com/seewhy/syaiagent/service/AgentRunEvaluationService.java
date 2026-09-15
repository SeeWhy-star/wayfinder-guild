package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.eval.TravelEvalCase;
import com.seewhy.syaiagent.eval.TravelEvalHarness;
import com.seewhy.syaiagent.eval.TravelEvalResult;
import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Selects a deterministic regression case for a completed Travel Agent run. */
@Service
public class AgentRunEvaluationService {

    private final TravelEvalHarness evalHarness;

    public AgentRunEvaluationService(TravelEvalHarness evalHarness) {
        this.evalHarness = evalHarness;
    }

    public TravelEvalResult evaluateTravelRun(String input, TravelPlan plan) {
        TravelEvalCase evalCase = selectCase(input);
        return evalHarness.evaluate(evalCase, plan, List.of());
    }

    private TravelEvalCase selectCase(String input) {
        String normalized = String.valueOf(input).toLowerCase(Locale.ROOT);
        return evalHarness.loadDefaultCases().stream()
                .filter(candidate -> containsDestination(normalized, candidate.expectedDestination()))
                .findFirst()
                .orElseGet(() -> evalHarness.loadDefaultCases().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No travel eval cases configured.")));
    }

    private boolean containsDestination(String input, String destination) {
        return destination != null && !destination.isBlank()
                && input.contains(destination.toLowerCase(Locale.ROOT));
    }
}
