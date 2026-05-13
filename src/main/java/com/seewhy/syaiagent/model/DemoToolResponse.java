package com.seewhy.syaiagent.model;

import java.util.List;

public record DemoToolResponse(
        String type,
        String status,
        String message,
        String terminalOutput,
        DemoArtifactResponse artifact,
        List<DemoArtifactResponse> artifacts,
        List<DemoToolSummaryItem> summaryItems
) {
    public DemoToolResponse(String type,
                            String status,
                            String message,
                            String terminalOutput,
                            DemoArtifactResponse artifact) {
        this(type, status, message, terminalOutput, artifact, artifact == null ? List.of() : List.of(artifact), List.of());
    }

    public DemoToolResponse(String type,
                            String status,
                            String message,
                            String terminalOutput,
                            DemoArtifactResponse artifact,
                            List<DemoArtifactResponse> artifacts) {
        this(type, status, message, terminalOutput, artifact, artifacts, List.of());
    }
}
