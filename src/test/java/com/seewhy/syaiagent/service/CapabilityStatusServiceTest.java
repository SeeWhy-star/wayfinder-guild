package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.WayfinderDemoStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CapabilityStatusServiceTest {

    @Test
    void demoModeDisablesLiveCapabilities() {
        CapabilityStatusService service = new CapabilityStatusService(
                new WayfinderDemoService(true),
                new ToolCallback[]{mock(ToolCallback.class)},
                mock(ChatModel.class),
                "sk-live",
                "tavily",
                "tvly-live",
                "pexels-live",
                "lightweight"
        );

        WayfinderDemoStatusResponse status = service.currentStatus();

        assertTrue(status.demoMode());
        assertEquals("lightweight", status.ragMode());
        assertFalse(status.liveManusAvailable());
        assertFalse(status.searchAvailable());
        assertFalse(status.imageSearchAvailable());
    }

    @Test
    void liveModeReportsConfiguredCapabilities() {
        CapabilityStatusService service = new CapabilityStatusService(
                new WayfinderDemoService(false),
                new ToolCallback[]{mock(ToolCallback.class)},
                mock(ChatModel.class),
                "sk-live",
                "tavily",
                "tvly-live",
                "pexels-live",
                "pgvector"
        );

        WayfinderDemoStatusResponse status = service.currentStatus();

        assertFalse(status.demoMode());
        assertEquals("pgvector", status.ragMode());
        assertTrue(status.liveManusAvailable());
        assertTrue(status.searchAvailable());
        assertTrue(status.imageSearchAvailable());
    }

    @Test
    void unknownRagModeFallsBackToDemo() {
        CapabilityStatusService service = new CapabilityStatusService(
                new WayfinderDemoService(false),
                new ToolCallback[]{mock(ToolCallback.class)},
                mock(ChatModel.class),
                "sk-live",
                "tavily",
                "tvly-live",
                "pexels-live",
                "experimental"
        );

        WayfinderDemoStatusResponse status = service.currentStatus();

        assertEquals("demo", status.ragMode());
    }
}
