package com.seewhy.syaiagent.model;

import java.util.List;

public record TravelReport(String title, List<String> recommendations,
                           String destination, String duration,
                           String budget, List<String> itinerary) {
    public TravelReport {
        if (recommendations == null) {
            recommendations = List.of();
        }
        if (itinerary == null) {
            itinerary = List.of();
        }
    }
}
