package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record AgentModule(
        String id,
        String name,
        String displayName,
        String description,
        List<String> capabilities,
        List<String> endpoints,
        List<String> relatedProjectIds
) {
}
