package com.seewhy.syaiagent.skill;

import java.util.List;

public record Skill(
        String id,
        String name,
        String description,
        List<String> tags,
        List<String> triggers,
        int priority,
        String content
) {
    public Skill {
        tags = tags == null ? List.of() : List.copyOf(tags);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        content = content == null ? "" : content.strip();
    }
}
