package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.service.TravelPlanService;
import org.springframework.stereotype.Service;

@Service
public class ItineraryPlannerService {

    private final TravelPlanService travelPlanService;

    public ItineraryPlannerService(TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    public TravelPlan plan(TravelRequirement requirement, String chatId) {
        return travelPlanService.generatePlan(requirement.message(), chatId);
    }
}
