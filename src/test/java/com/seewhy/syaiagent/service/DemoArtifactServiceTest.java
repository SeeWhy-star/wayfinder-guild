package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.DemoArtifactResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void registerReturnsSafeArtifactMetadata() throws Exception {
        Path file = tempDir.resolve("demo-note.txt");
        Files.writeString(file, "hello");
        DemoArtifactService service = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 1024, Clock.systemUTC());

        DemoArtifactResponse response = service.register(file, "demo-note.txt", "text/plain");

        assertNotNull(response.artifactId());
        assertEquals("demo-note.txt", response.fileName());
        assertEquals("text/plain", response.mimeType());
        assertEquals(5, response.size());
        assertTrue(response.previewUrl().startsWith("/travel/manus/artifacts/"));
        assertTrue(response.downloadUrl().endsWith("/download"));
        assertEquals("demo", response.source());
        assertEquals("in-memory", response.storageMode());
        assertNotNull(service.resolve(response.artifactId()).resource());
    }

    @Test
    void expiredArtifactCannotBeResolved() throws Exception {
        Path file = tempDir.resolve("demo-note.txt");
        Files.writeString(file, "hello");
        DemoArtifactService service = new DemoArtifactService(tempDir, Duration.ofMillis(-1), 1024, Clock.systemUTC());

        DemoArtifactResponse response = service.register(file, "demo-note.txt", "text/plain");

        assertThrows(IllegalArgumentException.class, () -> service.resolve(response.artifactId()));
    }

    @Test
    void pathOutsideTmpRootIsRejected() throws Exception {
        Path outside = Files.createTempFile("outside-artifact", ".txt");
        Files.writeString(outside, "hello");
        DemoArtifactService service = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 1024, Clock.systemUTC());

        try {
            assertThrows(SecurityException.class, () -> service.register(outside, "outside.txt", "text/plain"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void oversizedArtifactIsRejected() throws Exception {
        Path file = tempDir.resolve("large.txt");
        Files.writeString(file, "too large");
        DemoArtifactService service = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 3, Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.register(file, "large.txt", "text/plain"));
    }

    @Test
    void disallowedMimeTypeIsRejected() throws Exception {
        Path file = tempDir.resolve("demo.html");
        Files.writeString(file, "<script>alert(1)</script>");
        DemoArtifactService service = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 1024, Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.register(file, "demo.html", "text/html"));
    }
}
