package com.seewhy.syaiagent.model;

public record WayfinderDemoStatusResponse(
        boolean demoMode,
        String ragMode,
        boolean liveManusAvailable,
        boolean searchAvailable,
        boolean imageSearchAvailable
) {
}
