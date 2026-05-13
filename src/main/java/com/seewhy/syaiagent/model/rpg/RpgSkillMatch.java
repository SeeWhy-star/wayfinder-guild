package com.seewhy.syaiagent.model.rpg;

import java.util.List;

public record RpgSkillMatch(
        String id,
        String name,
        String rpgName,
        String level,
        String category,
        String description,
        List<String> matchedTerms,
        List<String> triggers,
        String matchedReason
) {
    public RpgSkillMatch {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }
}
