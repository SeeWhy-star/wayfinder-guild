package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.model.WayfinderDemoStatusResponse;
import com.seewhy.syaiagent.model.OwnerAccessStatusResponse;
import com.seewhy.syaiagent.security.OwnerAccessService;
import com.seewhy.syaiagent.service.CapabilityStatusService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel")
public class TravelCapabilityController {

    private final CapabilityStatusService capabilityStatusService;
    private final OwnerAccessService ownerAccessService;

    public TravelCapabilityController(CapabilityStatusService capabilityStatusService,
                                      OwnerAccessService ownerAccessService) {
        this.capabilityStatusService = capabilityStatusService;
        this.ownerAccessService = ownerAccessService;
    }

    @GetMapping("/demo-status")
    public WayfinderDemoStatusResponse demoStatus() {
        return capabilityStatusService.currentStatus();
    }

    @GetMapping("/owner-status")
    public OwnerAccessStatusResponse ownerStatus(HttpServletRequest request) {
        return new OwnerAccessStatusResponse(
                ownerAccessService.hasConfiguredOwnerToken(),
                ownerAccessService.hasOwnerAccess(request)
        );
    }
}
