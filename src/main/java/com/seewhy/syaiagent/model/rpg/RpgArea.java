package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgArea(
        String id,
        String nameEn,
        String nameZh,
        String type,
        String description,
        RpgPosition position,
        List<String> npcIds,
        List<String> moduleIds,
        List<RpgPortal> portals
) {
}
