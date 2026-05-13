package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReportComposerService {

    public TravelPlan compose(TravelPlan plan, TravelRequirement requirement) {
        List<String> alternatives = new ArrayList<>(plan.alternatives());
        if (!requirement.missingFields().isEmpty()) {
            addIfAbsent(alternatives, "补全 " + String.join(", ", requirement.missingFields()) + " 后，可重新生成更精确的版本。");
        }
        String summary = plan.summary();
        if (summary == null || summary.isBlank()) {
            summary = "已生成结构化旅行规划草案，可继续补充偏好后细化。";
        }
        return new TravelPlan(
                summary,
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                plan.budget(),
                plan.itineraryDays(),
                plan.transportation(),
                plan.accommodation(),
                plan.risks(),
                alternatives,
                plan.loadedSkills()
        );
    }

    private void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
