package com.seewhy.syaiagent.model;

import java.math.BigDecimal;
import java.util.List;

public record TravelPlan(
        String summary,
        String destination,
        String departure,
        Integer days,
        Integer travelers,
        Budget budget,
        List<ItineraryDay> itineraryDays,
        List<String> transportation,
        List<String> accommodation,
        List<String> risks,
        List<String> alternatives,
        List<String> loadedSkills
) {
    public TravelPlan {
        itineraryDays = itineraryDays == null ? List.of() : List.copyOf(itineraryDays);
        transportation = transportation == null ? List.of() : List.copyOf(transportation);
        accommodation = accommodation == null ? List.of() : List.copyOf(accommodation);
        risks = risks == null ? List.of() : List.copyOf(risks);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        loadedSkills = loadedSkills == null ? List.of() : List.copyOf(loadedSkills);
    }

    public record Budget(
            BigDecimal total,
            String currency,
            List<BudgetItem> items,
            String note
    ) {
        public Budget {
            currency = currency == null || currency.isBlank() ? "CNY" : currency;
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record BudgetItem(
            String name,
            BigDecimal amount,
            String note
    ) {
    }

    public record ItineraryDay(
            Integer day,
            String theme,
            List<Activity> activities,
            List<String> meals,
            String accommodation,
            String transport,
            String pace,
            List<String> reminders
    ) {
        public ItineraryDay {
            activities = activities == null ? List.of() : List.copyOf(activities);
            meals = meals == null ? List.of() : List.copyOf(meals);
            reminders = reminders == null ? List.of() : List.copyOf(reminders);
        }
    }

    public record Activity(
            String time,
            String title,
            String description,
            String area,
            String costLevel,
            List<String> tips
    ) {
        public Activity {
            tips = tips == null ? List.of() : List.copyOf(tips);
        }
    }
}
