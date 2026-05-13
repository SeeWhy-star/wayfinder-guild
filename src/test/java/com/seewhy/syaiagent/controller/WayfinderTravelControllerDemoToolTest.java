package com.seewhy.syaiagent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.service.ArtifactDeliveryService;
import com.seewhy.syaiagent.service.CapabilityStatusService;
import com.seewhy.syaiagent.service.DemoArtifactService;
import com.seewhy.syaiagent.service.SseEmitterStreamService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.seewhy.syaiagent.service.SyManusDemoToolService;
import com.seewhy.syaiagent.service.SyManusRecordedDemoToolService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.service.WayfinderDoctorRunner;
import com.seewhy.syaiagent.service.WayfinderFixedCommandRunner;
import com.seewhy.syaiagent.security.OwnerAccessService;
import com.seewhy.syaiagent.trace.AgentTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WayfinderTravelControllerDemoToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WayfinderTravelFacade facade;
    private DemoArtifactService artifactService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        facade = mock(WayfinderTravelFacade.class);
        artifactService = new DemoArtifactService(
                Path.of(FileConstant.FILE_SAVE_DIR),
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        mockMvc = buildMockMvc(false, facade);
    }

    @Test
    void demoModeStreamsLocalTravelChatWithoutCallingModelFacade() throws Exception {
        WayfinderTravelFacade demoFacade = mock(WayfinderTravelFacade.class);
        MockMvc demoMockMvc = buildMockMvc(true, demoFacade);

        MvcResult result = demoMockMvc.perform(get("/travel/chat/stream")
                        .param("message", "Plan a relaxed Kyoto trip")
                        .param("chatId", "demo-stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        demoMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("requirement -> RAG -> tool/plan -> guardrail -> trace")))
                .andExpect(content().string(containsString("Owner token verification only unlocks live controls")))
                .andExpect(content().string(containsString("Live Chat or Live TravelPlan")))
                .andExpect(content().string(containsString("[DONE]")));

        verify(demoFacade, never()).doChatByStream(anyString(), anyString());
    }

    @Test
    void demoModePlanStillReturnsLocalTravelPlanWithoutCallingStructuredFacade() throws Exception {
        WayfinderTravelFacade demoFacade = mock(WayfinderTravelFacade.class);
        MockMvc demoMockMvc = buildMockMvc(true, demoFacade);

        demoMockMvc.perform(post("/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"demo-plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"))
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.budget.total").value(15000))
                .andExpect(jsonPath("$.loadedSkills[0]").value("family-trip-planning"));

        verify(demoFacade, never()).doStructuredPlan(anyString(), anyString());
    }

    @Test
    void demoModeManusLiveTaskReturnsBoundaryMessageInsteadOfRunningAgent() throws Exception {
        WayfinderTravelFacade demoFacade = mock(WayfinderTravelFacade.class);
        MockMvc demoMockMvc = buildMockMvc(true, demoFacade);

        MvcResult result = demoMockMvc.perform(get("/travel/manus/chat")
                        .param("message", "Search for one image of Kyoto station and download it as a safe artifact")
                        .param("chatId", "demo-manus")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        demoMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Current public demo mode keeps Live Tool Tasks behind a configuration boundary")))
                .andExpect(content().string(containsString("Stable Engineering Demos")))
                .andExpect(content().string(containsString("Image search depends on Pexels API key and external network")))
                .andExpect(content().string(containsString("__DONE__")));
    }

    @Test
    void demoStatusReportsPublicDemoBoundary() throws Exception {
        WayfinderTravelFacade demoFacade = mock(WayfinderTravelFacade.class);
        MockMvc demoMockMvc = buildMockMvc(true, demoFacade);

        demoMockMvc.perform(get("/travel/demo-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.ragMode").value("demo"))
                .andExpect(jsonPath("$.liveManusAvailable").value(false))
                .andExpect(jsonPath("$.searchAvailable").value(false))
                .andExpect(jsonPath("$.imageSearchAvailable").value(false));
    }

    @Test
    void demoEndpointReturnsDoctorTerminalStructure() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("doctor"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Wayfinder Doctor completed."))
                .andExpect(jsonPath("$.terminalOutput", containsString("[OK] skills")))
                .andExpect(jsonPath("$.summaryItems[0].label").value("Skills"))
                .andExpect(jsonPath("$.summaryItems[0].value").value("5 OK"))
                .andExpect(jsonPath("$.artifact").doesNotExist());
    }

    @Test
    void recordedDemoEndpointReturnsRealRunReplayWithoutArtifactLinks() throws Exception {
        mockMvc.perform(post("/travel/manus/recorded-demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"portfolio-brief-pack\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("portfolio-brief-pack"))
                .andExpect(jsonPath("$.message").value("Portfolio Brief Pack generated Wayfinder Guild Markdown and PDF artifacts."))
                .andExpect(jsonPath("$.artifacts[0].fileName").value("wayfinder-guild-brief.md"))
                .andExpect(jsonPath("$.artifacts[0].previewUrl").doesNotExist())
                .andExpect(jsonPath("$.artifacts[0].downloadUrl").doesNotExist())
                .andExpect(jsonPath("$.artifacts[0].expiresAt").doesNotExist())
                .andExpect(jsonPath("$.artifacts[1].fileName").value("wayfinder-guild-interview-brief.pdf"))
                .andExpect(jsonPath("$.summaryItems[0].label").value("Generated Text Artifact"));
    }

    @Test
    void recordedBackendTestsUseSanitizedTranscript() throws Exception {
        mockMvc.perform(post("/travel/manus/recorded-demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"backend-tests\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("mvn -Dtest=WayfinderTravelControllerDemoToolTest,TravelRagServiceTest,RpgEvalServiceTest,DemoArtifactServiceTest,SyManusArtifactLinkServiceTest,SyManusDemoToolServiceTest,ToolRegistrationTest test completed."))
                .andExpect(jsonPath("$.terminalOutput", containsString("<workspace>")))
                .andExpect(jsonPath("$.terminalOutput", containsString("<user-home>")))
                .andExpect(jsonPath("$.terminalOutput", containsString("Tests run: 57, Failures: 0, Errors: 0, Skipped: 0")))
                .andExpect(jsonPath("$.summaryItems[1].value").value("57 passed"));
    }

    @Test
    void demoEndpointReturnsBackendTargetedTestStructure() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"backend-tests\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("backend-tests"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("mvn -Dtest=WayfinderTravelControllerDemoToolTest,... test completed."))
                .andExpect(jsonPath("$.terminalOutput", containsString("BUILD SUCCESS")))
                .andExpect(jsonPath("$.summaryItems[0].label").value("Build"))
                .andExpect(jsonPath("$.summaryItems[0].value").value("BUILD SUCCESS"))
                .andExpect(jsonPath("$.summaryItems[1].label").value("Tests"))
                .andExpect(jsonPath("$.summaryItems[1].value").value("55 passed"))
                .andExpect(jsonPath("$.artifact").doesNotExist());
    }

    @Test
    void demoEndpointReturnsJavaRuntimeCheckStructure() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"java-runtime\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("java-runtime"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("java -version completed."))
                .andExpect(jsonPath("$.terminalOutput").value("openjdk version \"21\""))
                .andExpect(jsonPath("$.summaryItems[0].label").value("Runtime"))
                .andExpect(jsonPath("$.summaryItems[0].value").value("21"))
                .andExpect(jsonPath("$.artifact").doesNotExist());
    }

    @Test
    void demoEndpointReturnsPortfolioBriefPackGeneratedFiles() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"portfolio-brief-pack\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("portfolio-brief-pack"))
                .andExpect(jsonPath("$.artifacts[0].fileName").value("wayfinder-guild-brief.md"))
                .andExpect(jsonPath("$.artifacts[0].mimeType").value("text/plain"))
                .andExpect(jsonPath("$.artifacts[0].previewUrl", startsWith("/travel/manus/artifacts/")))
                .andExpect(jsonPath("$.artifacts[0].downloadUrl", endsWith("/download")))
                .andExpect(jsonPath("$.artifacts[1].fileName").value("wayfinder-guild-interview-brief.pdf"))
                .andExpect(jsonPath("$.artifacts[1].mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.artifacts[1].previewUrl", startsWith("/travel/manus/artifacts/")))
                .andExpect(jsonPath("$.artifacts[1].downloadUrl", endsWith("/download")));
    }

    @Test
    void demoEndpointRoutesResumePackAliasToPortfolioBriefPack() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"resume-pack\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("portfolio-brief-pack"))
                .andExpect(jsonPath("$.artifacts[0].fileName").value("wayfinder-guild-brief.md"));
    }

    @Test
    void demoEndpointReturnsWayfinderTraceImageArtifactStructure() throws Exception {
        mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"trace-card-image\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("trace-card-image"))
                .andExpect(jsonPath("$.artifact.fileName").value("wayfinder-trace-card.png"))
                .andExpect(jsonPath("$.artifact.mimeType").value("image/png"));
    }

    @Test
    void artifactPreviewAndDownloadSetSafeUtf8TextHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"portfolio-brief-pack\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String previewUrl = json.at("/artifacts/0/previewUrl").asText();
        String downloadUrl = json.at("/artifacts/0/downloadUrl").asText();

        mockMvc.perform(get(previewUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(header().string("Content-Disposition", startsWith("inline")));

        mockMvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")));
    }

    @Test
    void chineseMarkdownPreviewIsServedAsUtf8Text() throws Exception {
        String chineseMarkdown = "# \u4e2d\u6587 Markdown\n\u8fd9\u662f SyManus artifact \u9884\u89c8\u3002";
        Path file = Path.of(FileConstant.FILE_SAVE_DIR, "file", "chinese-preview.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, chineseMarkdown, StandardCharsets.UTF_8);
        DemoArtifactResponse artifact = artifactService.register(file, "chinese-preview.md", "text/plain");

        MvcResult result = mockMvc.perform(get(artifact.previewUrl()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andReturn();

        assertEquals(chineseMarkdown, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockMvc buildMockMvc(boolean demoEnabled, WayfinderTravelFacade facade) {
        SyManusDemoToolService demoToolService = new SyManusDemoToolService(
                artifactService,
                new GuardrailService(),
                new StubDoctorRunner(),
                new StubCommandRunner()
        );
        WayfinderDemoService demoService = new WayfinderDemoService(demoEnabled);
        ArtifactDeliveryService artifactDeliveryService = new ArtifactDeliveryService(artifactService);
        CapabilityStatusService capabilityStatusService = new CapabilityStatusService(
                demoService,
                new ToolCallback[0],
                mock(ChatModel.class),
                "demo-disabled",
                "disabled",
                "",
                "",
                "demo"
        );
        WayfinderTravelController controller = new WayfinderTravelController(
                facade,
                mock(SseEmitterStreamService.class),
                mock(TravelRagService.class),
                demoService,
                new OwnerAccessService("")
        );
        OwnerAccessService ownerAccessService = new OwnerAccessService("");
        TravelCapabilityController capabilityController = new TravelCapabilityController(capabilityStatusService, ownerAccessService);
        SyManusController syManusController = new SyManusController(
                new ToolCallback[0],
                mock(ChatModel.class),
                mock(ChatMemory.class),
                demoService,
                demoToolService,
                new SyManusRecordedDemoToolService(),
                new SyManusArtifactLinkService(artifactService),
                objectMapper,
                ownerAccessService
        );
        ArtifactController artifactController = new ArtifactController(artifactDeliveryService);
        TravelTraceController traceController = new TravelTraceController(mock(AgentTraceService.class), demoService, ownerAccessService);
        return MockMvcBuilders.standaloneSetup(controller, capabilityController, syManusController, artifactController, traceController).build();
    }

    private static class StubDoctorRunner extends WayfinderDoctorRunner {
        @Override
        public DoctorResult runDoctor() {
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
