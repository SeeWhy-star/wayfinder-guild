package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAdvisorService {

    public TravelPlan advise(TravelPlan plan, TravelRequirement requirement) {
        List<String> risks = new ArrayList<>(plan.risks());
        if (!requirement.missingFields().isEmpty()) {
            addIfAbsent(risks, "仍需补充关键信息：" + String.join(", ", requirement.missingFields()) + "，否则行程和预算只能作为草案。");
        }
        if (risks.isEmpty()) {
            risks.add("天气、交通、开放时间和价格可能变化，请在出发前进行实时确认。");
        }
        return new TravelPlan(
                plan.summary(),
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                plan.budget(),
                plan.itineraryDays(),
                plan.transportation(),
                plan.accommodation(),
                risks,
                plan.alternatives(),
                plan.loadedSkills()
        );
    }

    private void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
