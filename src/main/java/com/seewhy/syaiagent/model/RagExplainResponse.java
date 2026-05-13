package com.seewhy.syaiagent.model;

import java.util.List;

public record RagExplainResponse(
        String chatId,
        String mode,
        String originalQuery,
        String rewrittenQuery,
        List<RagRetrievedDocument> documents,
        String answer,
        boolean degraded,
        String degradationReason
) {
    public RagExplainResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
