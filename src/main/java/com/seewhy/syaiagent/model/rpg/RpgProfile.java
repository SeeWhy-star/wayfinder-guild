package com.seewhy.syaiagent.model.rpg;

import java.util.List;
import java.util.Map;

public record RpgProfile(
        String id,
        String name,
        String title,
        String role,
        String location,
        String summary,
        List<String> focusAreas,
        List<String> strengths,
        Map<String, String> links,
        Map<String, String> stats
) {
}
