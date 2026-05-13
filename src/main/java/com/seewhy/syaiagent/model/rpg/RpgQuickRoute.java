package com.seewhy.syaiagent.model.rpg;

public record RpgQuickRoute(
        String id,
        String labelEn,
        String labelZh,
        String targetType,
        String targetId,
        String path
) {
}
