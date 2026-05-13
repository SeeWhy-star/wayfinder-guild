package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgNpc(
        String id,
        String nameEn,
        String nameZh,
        String role,
        String areaId,
        String greeting,
        String persona,
        List<String> actions
) {
}
