package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoArtifactService {

    public static final long DEFAULT_MAX_BYTES = 5L * 1024L * 1024L;
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "text/plain",
            "application/pdf"
    );

    private final Path allowedRoot;
    private final Duration ttl;
    private final long maxBytes;
    private final Clock clock;
    private final Map<String, ArtifactRecord> artifacts = new ConcurrentHashMap<>();

    public DemoArtifactService() {
        this(Path.of(FileConstant.FILE_SAVE_DIR), DEFAULT_TTL, DEFAULT_MAX_BYTES, Clock.systemUTC());
    }

    public DemoArtifactService(Path allowedRoot, Duration ttl, long maxBytes, Clock clock) {
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
        this.ttl = ttl;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    public DemoArtifactResponse register(Path filePath, String fileName, String mimeType) {
        ArtifactRecord record = validateNewArtifact(filePath, fileName, mimeType);
        artifacts.put(record.artifactId(), record);
        return toResponse(record);
    }

    public ArtifactResource resolve(String artifactId) {
        ArtifactRecord record = artifacts.get(artifactId);
        if (record == null) {
            throw new IllegalArgumentException("Artifact not found.");
        }
        if (Instant.now(clock).isAfter(record.expiresAt())) {
            artifacts.remove(artifactId);
            throw new IllegalArgumentException("Artifact has expired.");
        }
        validateExistingFile(record.path(), record.mimeType(), record.size());
        return new ArtifactResource(
                new PathResource(record.path()),
                record.fileName(),
                record.mimeType(),
                record.size()
        );
    }

    private ArtifactRecord validateNewArtifact(Path filePath, String fileName, String mimeType) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Artifact file name cannot be blank.");
        }
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Artifact MIME type is not allowed.");
        }
        Path normalized = filePath.toAbsolutePath().normalize();
        validateExistingFile(normalized, mimeType, -1);
        long size = sizeOf(normalized);
        return new ArtifactRecord(
                UUID.randomUUID().toString(),
                normalized,
                fileName,
                mimeType,
                size,
                Instant.now(clock).plus(ttl)
        );
    }

    private void validateExistingFile(Path filePath, String mimeType, long expectedSize) {
        Path normalized = filePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(allowedRoot)) {
            throw new SecurityException("Artifact path is outside the allowed tmp directory.");
        }
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Artifact MIME type is not allowed.");
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Artifact file does not exist.");
        }
        long size = sizeOf(normalized);
        if (size > maxBytes) {
            throw new IllegalArgumentException("Artifact exceeds the size limit.");
        }
        if (expectedSize >= 0 && size != expectedSize) {
            throw new IllegalArgumentException("Artifact file changed after registration.");
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read artifact size.", e);
        }
    }

    private DemoArtifactResponse toResponse(ArtifactRecord record) {
        String baseUrl = "/travel/manus/artifacts/" + record.artifactId();
        return new DemoArtifactResponse(
                record.artifactId(),
                record.fileName(),
                record.mimeType(),
                record.size(),
                baseUrl,
                baseUrl + "/download",
                record.expiresAt()
        );
    }

    private record ArtifactRecord(
            String artifactId,
            Path path,
            String fileName,
            String mimeType,
            long size,
            Instant expiresAt
    ) {
    }

    public record ArtifactResource(
            PathResource resource,
            String fileName,
            String mimeType,
            long size
    ) {
    }
}
