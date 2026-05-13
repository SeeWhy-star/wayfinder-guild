package com.seewhy.syaiagent.model;

import jakarta.validation.constraints.NotBlank;

public record TravelPlanRequest(
        @NotBlank(message = "message cannot be blank")
        String message,
        String chatId,
        Boolean liveMode
) {
}
