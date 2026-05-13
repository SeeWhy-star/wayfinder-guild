package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BudgetEstimatorService {

    public TravelPlan estimate(TravelPlan plan) {
        TravelPlan.Budget budget = plan.budget();
        if (budget == null) {
            budget = new TravelPlan.Budget(
                    null,
                    "CNY",
                    List.of(),
                    "缺少明确预算，建议补充总预算后再做交通、住宿、餐饮和门票拆分。"
            );
        } else if (budget.total() != null && budget.items().isEmpty()) {
            budget = new TravelPlan.Budget(
                    budget.total(),
                    budget.currency(),
                    defaultItems(budget.total()),
                    appendNote(budget.note(), "以下为按旅行规划经验生成的粗略拆分，请以实时价格为准。")
            );
        }

        return copyWithBudget(plan, budget);
    }

    private List<TravelPlan.BudgetItem> defaultItems(BigDecimal total) {
        return List.of(
                item("交通", total, "0.35", "机票、铁路、当地交通估算"),
                item("住宿", total, "0.35", "酒店或民宿估算"),
                item("餐饮", total, "0.15", "日常餐饮和特色体验估算"),
                item("门票与体验", total, "0.10", "景点、展馆和活动估算"),
                item("预留", total, "0.05", "天气、行李、打车和临时调整预留")
        );
    }

    private TravelPlan.BudgetItem item(String name, BigDecimal total, String ratio, String note) {
        return new TravelPlan.BudgetItem(name, total.multiply(new BigDecimal(ratio)), note);
    }

    private String appendNote(String original, String addition) {
        if (original == null || original.isBlank()) {
            return addition;
        }
        if (original.contains(addition)) {
            return original;
        }
        return original + " " + addition;
    }

    private TravelPlan copyWithBudget(TravelPlan plan, TravelPlan.Budget budget) {
        return new TravelPlan(
                plan.summary(),
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                budget,
                plan.itineraryDays(),
                plan.transportation(),
                plan.accommodation(),
                plan.risks(),
                plan.alternatives(),
                plan.loadedSkills()
        );
    }
}
