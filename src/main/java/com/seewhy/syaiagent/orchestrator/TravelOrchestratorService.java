package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class TravelOrchestratorService {

    private final RequirementCollectorService requirementCollectorService;
    private final ItineraryPlannerService itineraryPlannerService;
    private final BudgetEstimatorService budgetEstimatorService;
    private final RiskAdvisorService riskAdvisorService;
    private final ReportComposerService reportComposerService;
    private final AgentTraceService agentTraceService;

    public TravelOrchestratorService(RequirementCollectorService requirementCollectorService,
                                     ItineraryPlannerService itineraryPlannerService,
                                     BudgetEstimatorService budgetEstimatorService,
                                     RiskAdvisorService riskAdvisorService,
                                     ReportComposerService reportComposerService,
                                     AgentTraceService agentTraceService) {
        this.requirementCollectorService = requirementCollectorService;
        this.itineraryPlannerService = itineraryPlannerService;
        this.budgetEstimatorService = budgetEstimatorService;
        this.riskAdvisorService = riskAdvisorService;
        this.reportComposerService = reportComposerService;
        this.agentTraceService = agentTraceService;
    }

    public TravelPlan generatePlan(String message, String chatId) {
        log.info("Travel orchestrator started [{}]", chatId);
        agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.STARTED, "RequirementCollector is analyzing the request.");
        TravelRequirement requirement = requirementCollectorService.collect(message);
        agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.COMPLETED, "RequirementCollector completed.", requirementMetadata(requirement));

        TravelPlan planned = switch (requirement.taskType()) {
            case STRUCTURED_PLAN, REPORT -> itineraryPlannerService.plan(requirement, chatId);
        };

        agentTraceService.record(chatId, AgentTraceStep.BUDGET_CHECK, AgentTraceStatus.STARTED, "BudgetEstimator is checking budget structure.");
        TravelPlan budgetChecked = budgetEstimatorService.estimate(planned);
        agentTraceService.record(chatId, AgentTraceStep.BUDGET_CHECK, AgentTraceStatus.COMPLETED, "BudgetEstimator completed.");

        agentTraceService.record(chatId, AgentTraceStep.RISK_CHECK, AgentTraceStatus.STARTED, "RiskAdvisor is checking risks and missing inputs.");
        TravelPlan riskChecked = riskAdvisorService.advise(budgetChecked, requirement);
        agentTraceService.record(chatId, AgentTraceStep.RISK_CHECK, AgentTraceStatus.COMPLETED, "RiskAdvisor completed.", Map.of("riskCount", riskChecked.risks().size()));

        agentTraceService.record(chatId, AgentTraceStep.REPORT_GENERATION, AgentTraceStatus.STARTED, "ReportComposer is preparing final structured response.");
        TravelPlan finalPlan = reportComposerService.compose(riskChecked, requirement);
        agentTraceService.record(chatId, AgentTraceStep.REPORT_GENERATION, AgentTraceStatus.COMPLETED, "ReportComposer completed.");
        log.info("Travel orchestrator completed [{}]", chatId);
        return finalPlan;
    }

    private Map<String, Object> requirementMetadata(TravelRequirement requirement) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "live");
        metadata.put("mode", "live");
        metadata.put("taskType", requirement.taskType().name());
        metadata.put("missingFields", requirement.missingFields());
        putIfPresent(metadata, "departure", requirement.departure());
        putIfPresent(metadata, "destination", requirement.destination());
        putIfPresent(metadata, "days", requirement.days());
        putIfPresent(metadata, "travelers", requirement.travelers());
        putIfPresent(metadata, "budgetTotal", requirement.budgetTotal());
        putIfPresent(metadata, "currency", requirement.currency());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
