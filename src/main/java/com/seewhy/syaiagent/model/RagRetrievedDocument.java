package com.seewhy.syaiagent.model;

import java.util.List;

public record RagRetrievedDocument(
        String title,
        String source,
        String snippet,
        Double score,
        String documentId,
        List<String> tags,
        String updated,
        String sourceType
) {
    public RagRetrievedDocument(String title, String source, String snippet, Double score) {
        this(title, source, snippet, score, null, List.of(), null, null);
    }

    public RagRetrievedDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
