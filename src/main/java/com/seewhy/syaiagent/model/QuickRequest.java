package com.seewhy.syaiagent.model;

import jakarta.validation.constraints.NotBlank;

public class QuickRequest {

    @NotBlank(message = "message 不能为空")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
