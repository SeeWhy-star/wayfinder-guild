package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.model.DemoToolSummaryItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SyManusRecordedDemoToolService {

    public DemoToolResponse runRecordedDemo(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT).trim();
        return switch (type) {
            case "doctor", "wayfinder-doctor", "terminal" -> doctor();
            case "backend-tests", "targeted-tests", "maven-tests" -> backendTests();
            case "java-runtime", "java-version", "java" -> javaRuntime();
            case "portfolio-brief", "portfolio-brief-pack", "brief-pack", "resume", "java-resume", "resume-pack" ->
                    portfolioBriefPack();
            case "trace-card-image", "trace-card", "image" -> traceCardImage();
            default -> throw new IllegalArgumentException("Unsupported recorded demo tool type.");
        };
    }

    private DemoToolResponse doctor() {
        return new DemoToolResponse(
                "doctor",
                "success",
                "Wayfinder Doctor completed.",
                """
                        Finished dev profile [unoptimized + debuginfo] target(s) in 0.18s
                        Running target\\debug\\wayfinder-cli.exe doctor --workspace ..\\..

                        Wayfinder CLI checks
                        =====================
                        [OK] skills: checked 6, 0 warning(s), 0 error(s)
                        [OK] rpg: checked 5, 0 warning(s), 0 error(s)
                        [OK] evals: checked 3, 0 warning(s), 0 error(s)
                        [OK] prompts: checked 5, 0 warning(s), 0 error(s)
                        [OK] rag docs: checked 10, 0 warning(s), 0 error(s)
                        [OK] naming: checked 242, 0 warning(s), 0 error(s)

                        Wayfinder resource summary
                        ==========================
                        skills 6
                        rpg areas 10
                        rpg npcs 10
                        rpg projects 3
                        rpg skills 6
                        rpg modules 10
                        eval cases 3
                        prompt templates 5
                        rag docs 10
                        """,
                null,
                List.of(),
                List.of(
                        item("Skills", "6 OK", "0 warnings / 0 errors", "success"),
                        item("RPG", "5 OK", "0 warnings / 0 errors", "success"),
                        item("Evals", "3 OK", "0 warnings / 0 errors", "success"),
                        item("Prompts", "5 OK", "0 warnings / 0 errors", "success"),
                        item("RAG docs", "10 OK", "0 warnings / 0 errors", "success"),
                        item("Naming", "242 OK", "0 warnings / 0 errors", "success"),
                        item("Resource summary", "9 groups", "skills 6 / rpg areas 10 / rpg npcs 10 / rpg projects 3 / rpg skills 6 / rpg modules 10 / eval cases 3 / prompt templates 5 / rag docs 10", "info")
                )
        );
    }

    private DemoToolResponse backendTests() {
        return new DemoToolResponse(
                "backend-tests",
                "success",
                "mvn -Dtest=WayfinderTravelControllerDemoToolTest,TravelRagServiceTest,RpgEvalServiceTest,DemoArtifactServiceTest,SyManusArtifactLinkServiceTest,SyManusDemoToolServiceTest,ToolRegistrationTest test completed.",
                """
                        [INFO] Scanning for projects...
                        [INFO]
                        [INFO] ---------------------< com.SeeWhy:wayfinder-guild >---------------------
                        [INFO] Building Wayfinder Guild 0.0.1-SNAPSHOT
                        [INFO]   from pom.xml
                        [INFO] --------------------------------[ jar ]---------------------------------
                        [INFO]
                        [INFO] --- resources:3.3.1:resources (default-resources) @ wayfinder-guild ---
                        [INFO] Copying 42 resources from src\\main\\resources to target\\classes
                        [INFO]
                        [INFO] --- compiler:3.13.0:compile (default-compile) @ wayfinder-guild ---
                        [INFO] Nothing to compile - all classes are up to date.
                        [INFO]
                        [INFO] --- resources:3.3.1:testResources (default-testResources) @ wayfinder-guild ---
                        [INFO] skip non existing resourceDirectory <workspace>\\src\\test\\resources
                        [INFO]
                        [INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ wayfinder-guild ---
                        [INFO] Nothing to compile - all classes are up to date.
                        [INFO]
                        [INFO] --- surefire:3.5.2:test (default-test) @ wayfinder-guild ---
                        [INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
                        [INFO]
                        [INFO] -------------------------------------------------------
                        [INFO]  T E S T S
                        [INFO] -------------------------------------------------------
                        [INFO] Running com.seewhy.syaiagent.controller.WayfinderTravelControllerDemoToolTest
                        Java HotSpot(TM) 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
                        WARNING: A Java agent has been loaded dynamically (<user-home>\\.m2\\repository\\net\\bytebuddy\\byte-buddy-agent\\1.15.11\\byte-buddy-agent-1.15.11.jar)
                        WARNING: Dynamic loading of agents will be disallowed by default in a future release
                        [INFO] Running com.seewhy.syaiagent.service.TravelRagServiceTest
                        [INFO] Running com.seewhy.syaiagent.service.RpgEvalServiceTest
                        [INFO] Running com.seewhy.syaiagent.service.DemoArtifactServiceTest
                        [INFO] Running com.seewhy.syaiagent.service.SyManusArtifactLinkServiceTest
                        [INFO] Running com.seewhy.syaiagent.service.SyManusDemoToolServiceTest
                        [INFO] Running com.seewhy.syaiagent.tools.ToolRegistrationTest
                        23:58:41.143 [main] INFO org.springframework.mock.web.MockServletContext -- Initializing Spring TestDispatcherServlet ''
                        23:58:41.145 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Initializing Servlet ''
                        23:58:41.147 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Completed initialization in 2 ms
                        [INFO]
                        [INFO] Results:
                        [INFO]
                        [INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
                        [INFO]
                        [INFO] ------------------------------------------------------------------------
                        [INFO] BUILD SUCCESS
                        [INFO] ------------------------------------------------------------------------
                        [INFO] Total time:  6.969 s
                        [INFO] Finished at: 2026-05-14T00:02:19+08:00
                        [INFO] ------------------------------------------------------------------------
                        """,
                null,
                List.of(),
                List.of(
                        item("Build", "BUILD SUCCESS", "Fixed Maven test gate finished.", "success"),
                        item("Tests", "57 passed", "0 failures / 0 errors / 0 skipped", "success"),
                        item("Total time", "6.969 s", "Maven reported elapsed time.", "info"),
                        item("Test scope", "7 classes", "Travel demo, RAG, eval, artifacts, SyManus demo, tool registration.", "info")
                )
        );
    }

    private DemoToolResponse javaRuntime() {
        return new DemoToolResponse(
                "java-runtime",
                "success",
                "java -version completed.",
                """
                        java version "21.0.8" 2025-07-15 LTS
                        Java(TM) SE Runtime Environment (build 21.0.8+12-LTS-250)
                        Java HotSpot(TM) 64-Bit Server VM (build 21.0.8+12-LTS-250, mixed mode, sharing)
                        """,
                null,
                List.of(),
                List.of(
                        item("Runtime", "21.0.8", "Allowlisted java -version command.", "success"),
                        item("Boundary", "Local check", "No model, API key, or external network required.", "info")
                )
        );
    }

    private DemoToolResponse portfolioBriefPack() {
        DemoArtifactResponse markdown = recordedArtifact(
                "recorded-wayfinder-guild-brief-md",
                "wayfinder-guild-brief.md",
                "text/plain",
                1638
        );
        DemoArtifactResponse pdf = recordedArtifact(
                "recorded-wayfinder-guild-interview-brief-pdf",
                "wayfinder-guild-interview-brief.pdf",
                "application/pdf",
                3174
        );
        return new DemoToolResponse(
                "portfolio-brief-pack",
                "success",
                "Portfolio Brief Pack generated Wayfinder Guild Markdown and PDF artifacts.",
                null,
                null,
                List.of(markdown, pdf),
                List.of(
                        item("Generated Text Artifact", "wayfinder-guild-brief.md", "text/plain / 1.6 KB", "success"),
                        item("Generated PDF", "wayfinder-guild-interview-brief.pdf", "application/pdf / 3.1 KB", "success")
                )
        );
    }

    private DemoToolResponse traceCardImage() {
        DemoArtifactResponse image = recordedArtifact(
                "recorded-wayfinder-trace-card-png",
                "wayfinder-trace-card.png",
                "image/png",
                82125
        );
        return new DemoToolResponse(
                "trace-card-image",
                "success",
                "wayfinder-trace-card.png generated as a fixed Wayfinder trace artifact.",
                """
                        Trace Card Image
                        ================
                        [ARTIFACT] wayfinder-trace-card.png
                          mime: image/png
                          size: 80.2 KB
                          dimensions: 960 x 540
                        """,
                image,
                List.of(image),
                List.of(
                        item("Generated Image", "wayfinder-trace-card.png", "image/png / 80.2 KB", "success"),
                        item("Dimensions", "960 x 540", "Recorded trace-card image size.", "info")
                )
        );
    }

    private DemoArtifactResponse recordedArtifact(String artifactId, String fileName, String mimeType, long size) {
        return new DemoArtifactResponse(
                artifactId,
                fileName,
                mimeType,
                size,
                null,
                null,
                null,
                "recorded",
                "metadata-only"
        );
    }

    private DemoToolSummaryItem item(String label, String value, String detail, String state) {
        return new DemoToolSummaryItem(label, value, detail, state);
    }
}
