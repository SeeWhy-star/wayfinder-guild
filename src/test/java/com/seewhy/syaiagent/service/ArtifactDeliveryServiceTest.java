package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.DemoArtifactResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactDeliveryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deliverAddsSafePreviewHeaders() throws Exception {
        Path file = tempDir.resolve("demo.md");
        Files.writeString(file, "# demo");
        DemoArtifactService artifactService = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 1024, Clock.systemUTC());
        DemoArtifactResponse artifact = artifactService.register(file, "demo.md", "text/plain");
        ArtifactDeliveryService deliveryService = new ArtifactDeliveryService(artifactService);

        ResponseEntity<?> response = deliveryService.deliver(artifact.artifactId(), false);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getContentType().toString().contains("charset=UTF-8"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void deliverMapsMissingArtifactToNotFound() {
        DemoArtifactService artifactService = new DemoArtifactService(tempDir, Duration.ofMinutes(30), 1024, Clock.systemUTC());
        ArtifactDeliveryService deliveryService = new ArtifactDeliveryService(artifactService);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> deliveryService.deliver("missing", false));

        assertEquals(404, error.getStatusCode().value());
    }
}
