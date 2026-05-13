package com.seewhy.syaiagent.orchestrator;

import java.math.BigDecimal;
import java.util.List;

public record TravelRequirement(
        String message,
        boolean travelRelated,
        List<String> missingFields,
        TravelTaskType taskType,
        String departure,
        String destination,
        Integer days,
        Integer travelers,
        BigDecimal budgetTotal,
        String currency
) {
    public TravelRequirement(String message,
                             boolean travelRelated,
                             List<String> missingFields,
                             TravelTaskType taskType) {
        this(message, travelRelated, missingFields, taskType, null, null, null, null, null, null);
    }

    public TravelRequirement {
        message = message == null ? "" : message.strip();
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        taskType = taskType == null ? TravelTaskType.STRUCTURED_PLAN : taskType;
        departure = blankToNull(departure);
        destination = blankToNull(destination);
        currency = blankToNull(currency);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
