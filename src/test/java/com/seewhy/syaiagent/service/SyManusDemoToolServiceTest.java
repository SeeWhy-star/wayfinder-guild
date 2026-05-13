package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.DemoToolResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyManusDemoToolServiceTest {

    private final DemoArtifactService artifactService = new DemoArtifactService(
            Path.of(FileConstant.FILE_SAVE_DIR),
            Duration.ofMinutes(30),
            DemoArtifactService.DEFAULT_MAX_BYTES,
            Clock.systemUTC()
    );
    private final StubCommandRunner commandRunner = new StubCommandRunner();
    private final SyManusDemoToolService service = new SyManusDemoToolService(
            artifactService,
            new GuardrailService(),
            new StubDoctorRunner(true),
            commandRunner
    );

    @Test
    void doctorDemoRunsFixedWayfinderDoctorWithoutArtifact() {
        DemoToolResponse response = service.runDemo("doctor");

        assertEquals("doctor", response.type());
        assertEquals("success", response.status());
        assertTrue(response.message().contains("Wayfinder Doctor completed"));
        assertTrue(response.terminalOutput().contains("[OK] skills"));
        assertEquals("Skills", response.summaryItems().get(0).label());
        assertEquals("5 OK", response.summaryItems().get(0).value());
        assertTrue(response.summaryItems().stream().anyMatch(item -> item.label().equals("Resource summary")));
        assertNull(response.artifact());
    }

    @Test
    void doctorFailureReturnsClearErrorWithoutArtifact() {
        SyManusDemoToolService failingService = new SyManusDemoToolService(
                artifactService,
                new GuardrailService(),
                new StubDoctorRunner(false),
                commandRunner
        );

        DemoToolResponse response = failingService.runDemo("doctor");

        assertEquals("doctor", response.type());
        assertEquals("error", response.status());
        assertTrue(response.message().contains("Rust/Cargo is unavailable"));
        assertNull(response.artifact());
    }

    @Test
    void backendTargetedTestsRunFixedMavenQualityGateWithoutArtifact() {
        DemoToolResponse response = service.runDemo("backend-tests");

        assertEquals("backend-tests", response.type());
        assertEquals("success", response.status());
        assertTrue(response.message().contains("mvn -Dtest"));
        assertTrue(response.terminalOutput().contains("BUILD SUCCESS"));
        assertTrue(response.summaryItems().stream().anyMatch(item -> item.label().equals("Build")
                && item.value().equals("BUILD SUCCESS")));
        assertTrue(response.summaryItems().stream().anyMatch(item -> item.label().equals("Tests")
                && item.value().equals("55 passed")));
        assertNull(response.artifact());
    }

    @Test
    void javaRuntimeCheckRunsFixedAllowlistedRuntimeCommand() {
        DemoToolResponse response = service.runDemo("java-runtime");

        assertEquals("java-runtime", response.type());
        assertEquals("success", response.status());
        assertTrue(response.message().contains("java -version completed"));
        assertTrue(response.terminalOutput().contains("openjdk version"));
        assertEquals("Runtime", response.summaryItems().get(0).label());
        assertEquals("21", response.summaryItems().get(0).value());
        assertNull(response.artifact());
    }

    @Test
    void mavenVersionCheckRunsFixedMavenCommand() {
        DemoToolResponse response = service.runDemo("maven-version");

        assertEquals("maven-version", response.type());
        assertEquals("success", response.status());
        assertTrue(response.message().contains("mvn -version completed"));
        assertTrue(response.terminalOutput().contains("Apache Maven"));
        assertNull(response.artifact());
    }

    @Test
    void portfolioBriefPackReturnsMarkdownAndPdfArtifactMetadata() {
        DemoToolResponse response = service.runDemo("portfolio-brief-pack");

        assertEquals("portfolio-brief-pack", response.type());
        assertEquals("success", response.status());
        assertNotNull(response.artifact());
        assertEquals(2, response.artifacts().size());
        assertEquals("wayfinder-guild-brief.md", response.artifacts().get(0).fileName());
        assertEquals("text/plain", response.artifacts().get(0).mimeType());
        assertTrue(response.artifacts().get(0).previewUrl().startsWith("/travel/manus/artifacts/"));
        assertTrue(response.artifacts().get(0).downloadUrl().endsWith("/download"));
        assertEquals("wayfinder-guild-interview-brief.pdf", response.artifacts().get(1).fileName());
        assertEquals("application/pdf", response.artifacts().get(1).mimeType());
    }

    @Test
    void resumePackAliasIsRoutedToPortfolioBriefPackInsteadOfToyResume() {
        DemoToolResponse response = service.runDemo("resume-pack");

        assertEquals("portfolio-brief-pack", response.type());
        assertEquals("wayfinder-guild-brief.md", response.artifacts().get(0).fileName());
    }

    @Test
    void traceCardImageReturnsWayfinderImageArtifactMetadata() {
        DemoToolResponse response = service.runDemo("trace-card-image");

        assertEquals("trace-card-image", response.type());
        assertNotNull(response.artifact());
        assertEquals("wayfinder-trace-card.png", response.artifact().fileName());
        assertEquals("image/png", response.artifact().mimeType());
    }

    @Test
    void imageAliasReturnsWayfinderTraceCardInsteadOfOceanImage() {
        DemoToolResponse response = service.runDemo("image");

        assertEquals("trace-card-image", response.type());
        assertEquals("wayfinder-trace-card.png", response.artifact().fileName());
    }

    @Test
    void legacyFileDemoStillReturnsUtf8TextArtifactMetadata() {
        DemoToolResponse response = service.runDemo("file");

        assertEquals("file", response.type());
        assertNotNull(response.artifact());
        assertEquals("demo-note.txt", response.artifact().fileName());
        assertEquals("text/plain", response.artifact().mimeType());
        assertTrue(response.artifact().downloadUrl().endsWith("/download"));
    }

    @Test
    void unsupportedDemoTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.runDemo("search"));
    }

    private static class StubDoctorRunner extends WayfinderDoctorRunner {
        private final boolean success;

        StubDoctorRunner(boolean success) {
            this.success = success;
        }

        @Override
        public DoctorResult runDoctor() {
            if (success) {
                return new DoctorResult(true, "Wayfinder Doctor completed.", """
                        Wayfinder CLI checks
                        =====================
                        [OK] skills: checked 5, 0 warning(s), 0 error(s)
                        [OK] rpg: checked 5, 0 warning(s), 0 error(s)
                        [OK] evals: checked 3, 0 warning(s), 0 error(s)
                        [OK] prompts: checked 5, 0 warning(s), 0 error(s)
                        [OK] rag docs: checked 10, 0 warning(s), 0 error(s)
                        [OK] naming: checked 220, 0 warning(s), 0 error(s)

                        Wayfinder resource summary
                        ==========================
                        skills: 5
                        rpg areas: 10
                        rag docs: 10
                        """);
            }
            return new DoctorResult(false, "Wayfinder Doctor could not start because Rust/Cargo is unavailable.", "");
        }
    }

    private static class StubCommandRunner extends WayfinderFixedCommandRunner {
        @Override
        public CommandResult runBackendTargetedTests() {
            return new CommandResult(true, "mvn -Dtest=WayfinderTravelControllerDemoToolTest,... test completed.", """
                    [INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
                    [INFO] ------------------------------------------------------------------------
                    [INFO] BUILD SUCCESS
                    [INFO] ------------------------------------------------------------------------
                    [INFO] Total time:  7.232 s
                    """);
        }

        @Override
        public CommandResult runJavaVersion() {
            return new CommandResult(true, "java -version completed.", "openjdk version \"21\"");
        }

        @Override
        public CommandResult runMavenVersion() {
            return new CommandResult(true, "mvn -version completed.", "Apache Maven 3.9.11");
        }
    }
}
