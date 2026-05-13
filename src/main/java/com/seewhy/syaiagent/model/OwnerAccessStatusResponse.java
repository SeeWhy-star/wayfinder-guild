package com.seewhy.syaiagent.model;

public record OwnerAccessStatusResponse(
        boolean ownerTokenConfigured,
        boolean ownerVerified
) {
}
