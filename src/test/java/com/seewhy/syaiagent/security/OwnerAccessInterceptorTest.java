package com.seewhy.syaiagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.controller.ArtifactController;
import com.seewhy.syaiagent.controller.GlobalExceptionHandler;
import com.seewhy.syaiagent.controller.HealthController;
import com.seewhy.syaiagent.controller.SyManusController;
import com.seewhy.syaiagent.controller.TravelCapabilityController;
import com.seewhy.syaiagent.controller.TravelTraceController;
import com.seewhy.syaiagent.controller.WayfinderTravelController;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.service.ArtifactDeliveryService;
import com.seewhy.syaiagent.service.CapabilityStatusService;
import com.seewhy.syaiagent.service.SseEmitterStreamService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.seewhy.syaiagent.service.SyManusDemoToolService;
import com.seewhy.syaiagent.service.SyManusRecordedDemoToolService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OwnerAccessInterceptorTest {

    private static final String OWNER_TOKEN = "owner-secret";

    private WayfinderTravelFacade facade;
    private SyManusDemoToolService demoToolService;
    private SyManusRecordedDemoToolService recordedDemoToolService;
    private AgentTraceService traceService;
    private MockMvc publicDemoMockMvc;
    private MockMvc liveMockMvc;

    @BeforeEach
    void setUp() {
        facade = mock(WayfinderTravelFacade.class);
        when(facade.doChat(anyString(), anyString())).thenReturn("live response");
        when(facade.doChatByStream(anyString(), anyString())).thenReturn(Flux.just("live stream", "[DONE]"));
        when(facade.doStructuredPlan(anyString(), anyString())).thenReturn(new WayfinderDemoService(true).demoTravelPlan());
        demoToolService = mock(SyManusDemoToolService.class);
        recordedDemoToolService = mock(SyManusRecordedDemoToolService.class);
        traceService = new AgentTraceService();
        traceService.record("live-trace", AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.COMPLETED, "Live trace recorded");
        when(demoToolService.runDemo("doctor")).thenReturn(new DemoToolResponse("doctor", "success", "ok", null, null));
        when(recordedDemoToolService.runRecordedDemo("doctor")).thenReturn(new DemoToolResponse("doctor", "success", "recorded", null, null));

        publicDemoMockMvc = buildMockMvc(true, OWNER_TOKEN);
        liveMockMvc = buildMockMvc(false, OWNER_TOKEN);
    }

    @Test
    void liveApiEndpointRejectsMissingOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Owner token required for live model, tool, API, MCP, search, or artifact access."));
    }

    @Test
    void liveApiEndpointRejectsWrongOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void liveApiEndpointAllowsCorrectOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value("live-3"))
                .andExpect(jsonPath("$.content").value("live response"));

        verify(facade).doChat("call live model", "live-3");
    }

    @Test
    void configuredProductionWithoutOwnerTokenRejectsLiveAccess() throws Exception {
        MockMvc noConfiguredTokenMockMvc = buildMockMvc(false, "");

        noConfiguredTokenMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-4\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoAndHealthEndpointsStayPublic() throws Exception {
        publicDemoMockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        publicDemoMockMvc.perform(get("/travel/demo-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true));

        publicDemoMockMvc.perform(post("/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"demo-plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"));
    }

    @Test
    void travelPlanStaysPublicDemoEvenWhenDemoFlagIsDisabledWithoutOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"public-plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"));

        verify(facade, never()).doStructuredPlan("Plan a relaxed Kyoto trip", "public-plan");
    }

    @Test
    void travelPlanWithoutExplicitLiveFlagStaysDemoEvenWithVerifiedOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/plan")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"owner-plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"));

        verify(facade, never()).doStructuredPlan("Plan a relaxed Kyoto trip", "owner-plan");
    }

    @Test
    void travelPlanExplicitLiveFlagUsesLiveFacadeOnlyWithVerifiedOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/plan")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"owner-live-plan\",\"liveMode\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"));

        verify(facade).doStructuredPlan("Plan a relaxed Kyoto trip", "owner-live-plan");
    }

    @Test
    void travelPlanExplicitLiveFlagRejectsMissingOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"owner-live-plan\",\"liveMode\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void travelChatStreamDefaultsToDemoEvenWithVerifiedOwnerToken() throws Exception {
        MvcResult result = liveMockMvc.perform(get("/travel/chat/stream")
                        .param("message", "Plan a relaxed Kyoto trip")
                        .param("chatId", "owner-demo-chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        liveMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Owner token verification only unlocks live controls")))
                .andExpect(content().string(containsString("[DONE]")));

        verify(facade, never()).doChatByStream(anyString(), anyString());
    }

    @Test
    void travelChatStreamExplicitLiveFlagRejectsMissingOrWrongOwnerToken() throws Exception {
        liveMockMvc.perform(get("/travel/chat/stream")
                        .param("message", "Plan a relaxed Kyoto trip")
                        .param("chatId", "owner-live-chat")
                        .param("liveMode", "true"))
                .andExpect(status().isForbidden());

        liveMockMvc.perform(get("/travel/chat/stream")
                        .param("message", "Plan a relaxed Kyoto trip")
                        .param("chatId", "owner-live-chat")
                        .param("liveMode", "true")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void travelChatStreamExplicitLiveFlagUsesLiveFacadeWithVerifiedOwnerToken() throws Exception {
        MvcResult result = liveMockMvc.perform(get("/travel/chat/stream")
                        .param("message", "Plan a relaxed Kyoto trip")
                        .param("chatId", "owner-live-chat")
                        .param("liveMode", "true")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        liveMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("live stream")))
                .andExpect(content().string(containsString("[DONE]")));

        verify(facade).doChatByStream("Plan a relaxed Kyoto trip", "owner-live-chat");
    }

    @Test
    void ownerStatusReportsBackendVerifiedOwnerState() throws Exception {
        publicDemoMockMvc.perform(get("/travel/owner-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerTokenConfigured").value(true))
                .andExpect(jsonPath("$.ownerVerified").value(false));

        publicDemoMockMvc.perform(get("/travel/owner-status")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerTokenConfigured").value(true))
                .andExpect(jsonPath("$.ownerVerified").value(true));
    }

    @Test
    void serverToolAndArtifactEndpointsRequireOwnerToken() throws Exception {
        publicDemoMockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isForbidden());

        publicDemoMockMvc.perform(post("/travel/manus/demo-tool")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("doctor"));

        publicDemoMockMvc.perform(get("/travel/manus/artifacts/artifact-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordedDemoToolEndpointStaysPublicAndDoesNotCallLiveToolService() throws Exception {
        publicDemoMockMvc.perform(post("/travel/manus/recorded-demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("doctor"))
                .andExpect(jsonPath("$.message").value("recorded"));

        verify(recordedDemoToolService).runRecordedDemo("doctor");
        verify(demoToolService, never()).runDemo("doctor");
    }

    @Test
    void traceDefaultsToFixtureEvenWhenLiveEventsExist() throws Exception {
        liveMockMvc.perform(get("/travel/trace/live-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metadata.source").value("fixture"))
                .andExpect(jsonPath("$[0].metadata.mode").value("demo"));
    }

    @Test
    void liveTraceRequiresExplicitLiveModeAndVerifiedOwnerToken() throws Exception {
        liveMockMvc.perform(get("/travel/trace/live-trace")
                        .param("liveMode", "true"))
                .andExpect(status().isForbidden());

        liveMockMvc.perform(get("/travel/trace/live-trace")
                        .param("liveMode", "true")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metadata.source").value("live"))
                .andExpect(jsonPath("$[0].metadata.mode").value("live"));
    }

    private MockMvc buildMockMvc(boolean demoEnabled, String ownerToken) {
        WayfinderDemoService demoService = new WayfinderDemoService(demoEnabled);
        OwnerAccessService ownerAccessService = new OwnerAccessService(ownerToken);
        OwnerAccessInterceptor ownerAccessInterceptor = new OwnerAccessInterceptor(ownerAccessService, demoService);

        WayfinderTravelController travelController = new WayfinderTravelController(
                facade,
                mock(SseEmitterStreamService.class),
                mock(TravelRagService.class),
                demoService,
                ownerAccessService
        );
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
        SyManusController syManusController = new SyManusController(
                new ToolCallback[0],
                mock(ChatModel.class),
                mock(ChatMemory.class),
                demoService,
                demoToolService,
                recordedDemoToolService,
                mock(SyManusArtifactLinkService.class),
                new ObjectMapper(),
                ownerAccessService
        );

        return MockMvcBuilders.standaloneSetup(
                        travelController,
                        new TravelCapabilityController(capabilityStatusService, ownerAccessService),
                        syManusController,
                        new TravelTraceController(traceService, demoService, ownerAccessService),
                        new ArtifactController(mock(ArtifactDeliveryService.class)),
                        new HealthController()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(ownerAccessInterceptor)
                .build();
    }
}
