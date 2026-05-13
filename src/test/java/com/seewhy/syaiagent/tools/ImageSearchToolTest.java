package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.service.DemoArtifactService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchToolTest {

    @Test
    void downloadsProviderReturnedImageAndRegistersArtifact() {
        AtomicReference<URI> downloaded = new AtomicReference<>();
        ImageSearchTool tool = new ImageSearchTool(
                new GuardrailService(),
                new ImageSearchTool.PexelsImageProvider() {
                    @Override
                    List<String> searchMediumImages(String apiKey, String query) {
                        return List.of("https://images.pexels.com/photos/123/grassland.jpg");
                    }
                },
                (uri, filePath) -> {
                    downloaded.set(uri);
                    try {
                        Files.writeString(filePath, "jpg");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
        );
        tool.setApiKeyForTest("test-key");

        String result = tool.searchImage("grassland image");

        assertTrue(result.startsWith("Image downloaded successfully to: "));
        assertEquals("https://images.pexels.com/photos/123/grassland.jpg", downloaded.get().toString());

        DemoArtifactService artifactService = new DemoArtifactService(
                Path.of(FileConstant.FILE_SAVE_DIR),
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        SyManusArtifactLinkService linkService = new SyManusArtifactLinkService(
                artifactService,
                Path.of(FileConstant.FILE_SAVE_DIR)
        );
        SyManusArtifactLinkService.ArtifactLinkResult linkResult = linkService.linkArtifacts(result);

        assertEquals(1, linkResult.artifacts().size());
        assertEquals("image/jpeg", linkResult.artifacts().getFirst().mimeType());
        assertFalse(linkResult.text().contains(Path.of(FileConstant.FILE_SAVE_DIR).toString()));
    }

    @Test
    void missingApiKeyDoesNotFabricateImage() {
        ImageSearchTool tool = new ImageSearchTool(
                new GuardrailService(),
                new ImageSearchTool.PexelsImageProvider() {
                    @Override
                    List<String> searchMediumImages(String apiKey, String query) {
                        throw new AssertionError("Provider should not be called without API key.");
                    }
                },
                (uri, filePath) -> {
                    throw new AssertionError("Downloader should not be called without API key.");
                }
        );

        String result = tool.searchImage("草原");

        assertTrue(result.contains("Pexels image search is not configured"));
        assertTrue(result.contains("Do not fabricate image results"));
    }
}
