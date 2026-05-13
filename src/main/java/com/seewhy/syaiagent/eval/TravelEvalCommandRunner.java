package com.seewhy.syaiagent.eval;

import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.service.TravelPlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "travel.eval.enabled", havingValue = "true")
@Slf4j
public class TravelEvalCommandRunner implements ApplicationRunner {

    private final TravelEvalHarness travelEvalHarness;
    private final TravelPlanService travelPlanService;

    public TravelEvalCommandRunner(TravelEvalHarness travelEvalHarness,
                                   TravelPlanService travelPlanService) {
        this.travelEvalHarness = travelEvalHarness;
        this.travelPlanService = travelPlanService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TravelEvalCase> cases = travelEvalHarness.loadDefaultCases();
        log.info("Running {} travel eval cases", cases.size());
        for (TravelEvalCase evalCase : cases) {
            TravelPlan plan = travelPlanService.generatePlan(evalCase.input(), "eval-" + evalCase.id());
            TravelEvalResult result = travelEvalHarness.evaluate(evalCase, plan);
            log.info("Travel eval [{}] score: {}/{} passed={}", result.caseId(), result.score(), result.maxScore(), result.passed());
        }
    }
}
