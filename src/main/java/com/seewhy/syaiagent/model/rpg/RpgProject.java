package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgProject(
        String id,
        String name,
        String subtitle,
        String description,
        String areaId,
        List<String> tags,
        List<String> highlights,
        List<String> moduleIds,
        List<String> links
) {
}
