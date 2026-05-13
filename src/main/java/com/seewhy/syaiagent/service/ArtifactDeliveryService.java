package com.seewhy.syaiagent.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class ArtifactDeliveryService {

    private final DemoArtifactService demoArtifactService;

    public ArtifactDeliveryService(DemoArtifactService demoArtifactService) {
        this.demoArtifactService = demoArtifactService;
    }

    public ResponseEntity<Resource> deliver(String artifactId, boolean attachment) {
        try {
            DemoArtifactService.ArtifactResource artifact = demoArtifactService.resolve(artifactId);
            ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                    .filename(artifact.fileName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .contentType(artifactMediaType(artifact))
                    .contentLength(artifact.size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(artifact.resource());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private MediaType artifactMediaType(DemoArtifactService.ArtifactResource artifact) {
        MediaType mediaType = MediaType.parseMediaType(artifact.mimeType());
        if (MediaType.TEXT_PLAIN.includes(mediaType)) {
            return new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
        }
        return mediaType;
    }
}
