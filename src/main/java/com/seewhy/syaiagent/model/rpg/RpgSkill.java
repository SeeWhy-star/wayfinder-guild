package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgSkill(
        String id,
        String name,
        String category,
        String level,
        String description,
        String areaId,
        List<String> keywords
) {
}
