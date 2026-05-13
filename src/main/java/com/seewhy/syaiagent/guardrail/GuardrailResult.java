package com.seewhy.syaiagent.guardrail;

import java.util.List;

public record GuardrailResult(
        boolean allowed,
        boolean travelRelated,
        String normalizedInput,
        List<String> warnings,
        String message
) {
    public GuardrailResult {
        normalizedInput = normalizedInput == null ? "" : normalizedInput.strip();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static GuardrailResult allow(String normalizedInput, boolean travelRelated, List<String> warnings) {
        return new GuardrailResult(true, travelRelated, normalizedInput, warnings, "");
    }

    public static GuardrailResult block(String message) {
        return new GuardrailResult(false, false, "", List.of(), message);
    }
}
