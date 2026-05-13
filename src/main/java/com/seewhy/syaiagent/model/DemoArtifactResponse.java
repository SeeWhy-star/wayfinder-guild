package com.seewhy.syaiagent.model;

import java.time.Instant;

public record DemoArtifactResponse(
        String artifactId,
        String fileName,
        String mimeType,
        long size,
        String previewUrl,
        String downloadUrl,
        Instant expiresAt,
        String source,
        String storageMode
) {
    public DemoArtifactResponse(String artifactId,
                                String fileName,
                                String mimeType,
                                long size,
                                String previewUrl,
                                String downloadUrl,
                                Instant expiresAt) {
        this(artifactId, fileName, mimeType, size, previewUrl, downloadUrl, expiresAt, "demo", "in-memory");
    }
}
