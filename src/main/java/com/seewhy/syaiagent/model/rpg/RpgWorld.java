package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgWorld(
        String id,
        String name,
        String role,
        String style,
        String taglineEn,
        String taglineZh,
        String positioningZh,
        String welcome,
        List<RpgQuickRoute> quickRoutes,
        List<RpgArea> areas,
        List<RpgNpc> npcs
) {
}
