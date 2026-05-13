package com.seewhy.syaiagent.agent;

import com.seewhy.syaiagent.service.DemoArtifactService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallAgentArtifactFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void rawToolResultRegistersArtifactAndSummaryIsSanitizedWithOneMarker() throws Exception {
        Path file = tempDir.resolve("pdf").resolve("backend-java-resume.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pdf");

        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        DemoArtifactService artifactService = new DemoArtifactService(
                tempDir,
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        agent.setArtifactLinkService(new SyManusArtifactLinkService(artifactService, tempDir));
        agent.setArtifactMarkerFormatter(artifact -> "[ARTIFACT]{\"artifactId\":\""
                + artifact.artifactId() + "\",\"fileName\":\"" + artifact.fileName() + "\"}[/ARTIFACT]");

        agent.registerCurrentRunArtifactsFromRawText("PDF generated successfully to: " + file);
        String firstChunk = agent.formatCurrentRunOutput("""
                The PDF was generated successfully.
                Saved to: %s
                Success path: %s
                File location: %s
                """.formatted(file, file, file));
        String secondChunk = agent.formatCurrentRunOutput("Saved to: " + file);

        assertFalse(firstChunk.contains(file.toString()));
        assertFalse(secondChunk.contains(file.toString()));
        assertTrue(firstChunk.contains("backend-java-resume.pdf (secure artifact link registered)"));
        assertEquals(1, count(firstChunk + secondChunk, "[ARTIFACT]"));
    }

    @Test
    void rawToolResultWithMdAndPdfRegistersTwoMarkersAndHidesPaths() throws Exception {
        Path md = tempDir.resolve("file").resolve("backend-java-resume.md");
        Path pdf = tempDir.resolve("pdf").resolve("backend-java-resume.pdf");
        Files.createDirectories(md.getParent());
        Files.createDirectories(pdf.getParent());
        Files.writeString(md, "# Backend Java Resume");
        Files.writeString(pdf, "pdf");

        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        DemoArtifactService artifactService = new DemoArtifactService(
                tempDir,
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        agent.setArtifactLinkService(new SyManusArtifactLinkService(artifactService, tempDir));
        agent.setArtifactMarkerFormatter(artifact -> "[ARTIFACT]{\"artifactId\":\""
                + artifact.artifactId() + "\",\"fileName\":\"" + artifact.fileName()
                + "\",\"mimeType\":\"" + artifact.mimeType() + "\"}[/ARTIFACT]");

        agent.registerCurrentRunArtifactsFromRawText("""
                File written successfully to: %s
                PDF generated successfully to: %s
                """.formatted(md, pdf));
        String chunk = agent.formatCurrentRunOutput("""
                Generated backend-java-resume.md and backend-java-resume.pdf.
                Markdown path: %s
                PDF path: %s
                """.formatted(md, pdf));

        assertFalse(chunk.contains(md.toString()));
        assertFalse(chunk.contains(pdf.toString()));
        assertTrue(chunk.contains("backend-java-resume.md (secure artifact link registered)"));
        assertTrue(chunk.contains("backend-java-resume.pdf (secure artifact link registered)"));
        assertEquals(2, count(chunk, "[ARTIFACT]"));
        assertEquals(1, count(chunk, "\"fileName\":\"backend-java-resume.md\""));
        assertEquals(1, count(chunk, "\"fileName\":\"backend-java-resume.pdf\""));
    }

    @Test
    void jsonStringToolResponseRegistersArtifactAndSanitizesEscapedPath() throws Exception {
        Path pdf = tempDir.resolve("pdf").resolve("Java后端简历.pdf");
        Files.createDirectories(pdf.getParent());
        Files.writeString(pdf, "pdf");

        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        DemoArtifactService artifactService = new DemoArtifactService(
                tempDir,
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        agent.setArtifactLinkService(new SyManusArtifactLinkService(artifactService, tempDir));
        agent.setArtifactMarkerFormatter(artifact -> "[ARTIFACT]{\"artifactId\":\""
                + artifact.artifactId() + "\",\"fileName\":\"" + artifact.fileName() + "\"}[/ARTIFACT]");

        String decodedToolResult = "PDF generated successfully to: " + pdf;
        String jsonStringToolResult = objectMapper.writeValueAsString(decodedToolResult);
        agent.registerCurrentRunArtifactsFromRawText(jsonStringToolResult);

        String chunk = agent.formatCurrentRunOutput("Tool generatePDF returned: " + jsonStringToolResult);

        assertFalse(chunk.contains(pdf.toString()));
        assertFalse(chunk.contains(pdf.toString().replace("\\", "\\\\")));
        assertTrue(chunk.contains("Java后端简历.pdf (secure artifact link registered)"));
        assertEquals(1, count(chunk, "[ARTIFACT]"));
        assertEquals(1, count(chunk, "\"fileName\":\"Java后端简历.pdf\""));
    }

    @Test
    void recoveredUnsafeFilenameFailureIsProductizedWhenArtifactExists() throws Exception {
        Path pdf = tempDir.resolve("pdf").resolve("CppBackendResume.pdf");
        Files.createDirectories(pdf.getParent());
        Files.writeString(pdf, "pdf");

        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        DemoArtifactService artifactService = new DemoArtifactService(
                tempDir,
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        agent.setArtifactLinkService(new SyManusArtifactLinkService(artifactService, tempDir));
        agent.setArtifactMarkerFormatter(artifact -> "[ARTIFACT]{\"artifactId\":\""
                + artifact.artifactId() + "\",\"fileName\":\"" + artifact.fileName() + "\"}[/ARTIFACT]");
        agent.registerCurrentRunArtifactsFromRawText("PDF generated successfully to: " + pdf);

        String chunk = agent.formatCurrentRunOutput("""
                The tool failed to generate the PDF because the file name contained unsafe characters. Please try again with a simpler, alphanumeric file name.
                The PDF CppBackendResume.pdf was generated successfully.
                Saved to: %s
                """.formatted(pdf));

        assertFalse(chunk.toLowerCase().contains("tool failed"));
        assertFalse(chunk.toLowerCase().contains("unsafe characters"));
        assertTrue(chunk.contains("I adjusted the file name to meet safe download rules"));
        assertTrue(chunk.contains("CppBackendResume.pdf"));
        assertEquals(1, count(chunk, "[ARTIFACT]"));
    }

    private int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
