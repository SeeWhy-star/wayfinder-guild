package com.seewhy.syaiagent.model;

import com.seewhy.syaiagent.trace.AgentTraceEvent;
import com.seewhy.syaiagent.eval.TravelEvalResult;

import java.util.List;

/**
 * Small application-level envelope for an Agent execution.
 * The HTTP contract can continue returning the contained result while
 * Trace and Eval gradually move onto the same run model.
 */
public record AgentRun<T>(
        String runId,
        String status,
        String mode,
        T result,
        List<AgentTraceEvent> events,
        TravelEvalResult evaluation
) {
    public AgentRun {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static <T> AgentRun<T> completed(String runId, String mode, T result) {
        return new AgentRun<>(runId, "COMPLETED", mode, result, List.of(), null);
    }

    public static <T> AgentRun<T> completed(String runId,
                                            String mode,
                                            T result,
                                            List<AgentTraceEvent> events,
                                            TravelEvalResult evaluation) {
        return new AgentRun<>(runId, "COMPLETED", mode, result, events, evaluation);
    }
}
