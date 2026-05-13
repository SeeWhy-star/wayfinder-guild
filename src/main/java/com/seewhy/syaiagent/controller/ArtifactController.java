package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.service.ArtifactDeliveryService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel/manus/artifacts")
public class ArtifactController {

    private final ArtifactDeliveryService artifactDeliveryService;

    public ArtifactController(ArtifactDeliveryService artifactDeliveryService) {
        this.artifactDeliveryService = artifactDeliveryService;
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<Resource> previewManusArtifact(@PathVariable String artifactId) {
        return artifactDeliveryService.deliver(artifactId, false);
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> downloadManusArtifact(@PathVariable String artifactId) {
        return artifactDeliveryService.deliver(artifactId, true);
    }
}
