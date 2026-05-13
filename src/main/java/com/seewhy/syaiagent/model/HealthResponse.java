package com.seewhy.syaiagent.model;

public class HealthResponse {

    private final String status;
    private final String message;

    public HealthResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
