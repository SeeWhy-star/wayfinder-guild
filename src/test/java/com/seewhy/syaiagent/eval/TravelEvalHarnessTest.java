package com.seewhy.syaiagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelEvalHarnessTest {

    private final TravelEvalHarness harness = new TravelEvalHarness(new ObjectMapper());

    @Test
    void loadDefaultCasesReadsJsonConfiguration() {
        List<TravelEvalCase> cases = harness.loadDefaultCases();

        assertTrue(cases.size() >= 3);
        assertTrue(cases.stream().anyMatch(evalCase -> evalCase.id().equals("jp-family-relaxed-budget")));
    }

    @Test
    void evaluatePassesStrongStructuredPlan() {
        TravelEvalCase evalCase = caseById("jp-family-relaxed-budget");

        TravelEvalResult result = harness.evaluate(evalCase, strongPlan(evalCase));

        assertTrue(result.passed());
        assertEquals(result.maxScore(), result.score());
        assertEquals(100, result.maxScore());
    }

    @Test
    void evaluateFlagsUnsafeClaimsAndDisallowedTools() {
        TravelEvalCase evalCase = caseById("jp-family-relaxed-budget");

        TravelPlan riskyPlan = new TravelPlan(
                "Guaranteed visa approval and 100% safe trip.",
                evalCase.expectedDestination(),
                "Shanghai",
                3,
                evalCase.expectedTravelers(),
                new TravelPlan.Budget(BigDecimal.valueOf(5000), "CNY", List.of(), "Fixed price."),
                List.of(new TravelPlan.ItineraryDay(1, "Arrival", List.of(), List.of(), "Tokyo", "train", "relaxed", List.of())),
                List.of("train"),
                List.of("station hotel"),
                List.of("weather may change"),
                List.of("shorten the trip"),
                evalCase.expectedSkills()
        );

        TravelEvalResult result = harness.evaluate(evalCase, riskyPlan, List.of("terminal.run"));

        assertFalse(result.passed());
        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("unsafe-claims") && !rule.passed()));
        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("disallowed-tools") && !rule.passed()));
    }

    @Test
    void underspecifiedRequestRequiresClarifyingQuestion() {
        TravelEvalCase evalCase = caseById("missing-core-info");

        TravelPlan plan = new TravelPlan(
                "Need destination, days, travelers, budget, and pace before making a concrete plan.",
                null,
                null,
                null,
                null,
                null,
                List.of(new TravelPlan.ItineraryDay(1, "TBD", List.of(), List.of(), null, null, null, List.of("Please confirm city and days."))),
                List.of(),
                List.of(),
                List.of("Please confirm travelers and budget."),
                List.of("Can draft options after preferences are known."),
                List.of()
        );

        TravelEvalResult result = harness.evaluate(evalCase, plan);

        assertTrue(result.rules().stream().anyMatch(rule -> rule.rule().equals("clarifying-question") && rule.passed()));
    }

    @Test
    void underspecifiedRequestWithUnsupportedAssumptionsDoesNotScorePerfectly() {
        TravelEvalCase evalCase = caseById("missing-core-info");

        TravelPlan plan = new TravelPlan(
                "Hangzhou 3-day draft. Still missing destination, days, travelers, and budget.",
                "Hangzhou",
                null,
                3,
                null,
                new TravelPlan.Budget(BigDecimal.valueOf(3000), "CNY", List.of(
                        new TravelPlan.BudgetItem("Hotel", BigDecimal.valueOf(1200), "estimate")
                ), "Assumed budget."),
                List.of(
                        new TravelPlan.ItineraryDay(1, "West Lake", List.of(), List.of(), "Hangzhou", "metro", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "Temple", List.of(), List.of(), "Hangzhou", "bus", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(3, "Return", List.of(), List.of(), "Hangzhou", "metro", "relaxed", List.of())
                ),
                List.of("metro"),
                List.of("near West Lake"),
                List.of("Travelers, budget, days, and destination still need confirmation."),
                List.of("Change destination after confirmation."),
                List.of()
        );

        TravelEvalResult result = harness.evaluate(evalCase, plan);

        assertFalse(result.score() == result.maxScore());
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("structured-itinerary") && !rule.passed()));
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("budget-reasonableness") && !rule.passed()));
    }

    @Test
    void mismatchedPlanDoesNotScorePerfectlyForJapanFamilyCase() {
        TravelEvalCase evalCase = caseById("jp-family-relaxed-budget");

        TravelEvalResult result = harness.evaluate(evalCase, fiveDayKyotoPlan());

        assertFalse(result.score() == result.maxScore());
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("case-alignment") && !rule.passed()));
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("structured-itinerary") && rule.score() < rule.maxScore()));
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("budget-reasonableness") && rule.score() < rule.maxScore()));
    }

    @Test
    void currentPlanCaseScoresMatchingKyotoPlanAgainstCurrentRequest() {
        TravelEvalCase evalCase = currentKyotoCase();

        TravelEvalResult result = harness.evaluateCurrentPlan(evalCase, fiveDayKyotoPlan(), List.of());

        assertEquals(100, result.maxScore());
        assertTrue(result.passed());
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("request-coverage") && rule.passed()));
        assertFalse(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("case-alignment")));
        assertFalse(result.rules().stream()
                .anyMatch(rule -> String.valueOf(rule.message()).toLowerCase().contains("destination mismatch")));
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("missing-info-honesty") && rule.score() >= 10));
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("budget-grounding") && rule.score() < rule.maxScore()));
        assertTrue(result.score() >= 80, "Expected a useful score for the matching current request.");
    }

    @Test
    void currentPlanPenalizesInventedTravelers() {
        TravelPlan plan = new TravelPlan(
                "A relaxed 5-day Kyoto plan.",
                "京都",
                "Shanghai",
                5,
                2,
                new TravelPlan.Budget(BigDecimal.valueOf(15000), "CNY", List.of(
                        new TravelPlan.BudgetItem("Flights", BigDecimal.valueOf(6000), "overall family estimate")
                ), "overall family estimate; recalibrate after traveler count is known"),
                fiveDayKyotoPlan().itineraryDays(),
                fiveDayKyotoPlan().transportation(),
                fiveDayKyotoPlan().accommodation(),
                List.of("Traveler count is not specified; recalibrate budget after traveler count is known."),
                fiveDayKyotoPlan().alternatives(),
                fiveDayKyotoPlan().loadedSkills()
        );

        TravelEvalResult result = harness.evaluateCurrentPlan(currentKyotoCase(), plan, List.of());

        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("missing-info-honesty") && rule.score() < rule.maxScore()));
    }

    @Test
    void currentPlanAllowsOverallFamilyBudgetWithTravelerUncertainty() {
        TravelPlan plan = new TravelPlan(
                "A relaxed 5-day Kyoto plan.",
                "京都",
                "Shanghai",
                5,
                null,
                new TravelPlan.Budget(BigDecimal.valueOf(15000), "CNY", List.of(
                        new TravelPlan.BudgetItem("Flights", BigDecimal.valueOf(6000), "overall family estimate")
                ), "overall family estimate; recalibrate after traveler count is known"),
                fiveDayKyotoPlan().itineraryDays(),
                fiveDayKyotoPlan().transportation(),
                fiveDayKyotoPlan().accommodation(),
                List.of("Traveler count is not specified; recalibrate budget after traveler count is known."),
                fiveDayKyotoPlan().alternatives(),
                fiveDayKyotoPlan().loadedSkills()
        );

        TravelEvalResult result = harness.evaluateCurrentPlan(currentKyotoCase(), plan, List.of());

        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("budget-grounding") && rule.passed()));
    }

    @Test
    void fixedFoodCitywalkCaseStillRejectsKyotoPlan() {
        TravelEvalCase evalCase = caseById("food-citywalk");

        TravelEvalResult result = harness.evaluate(evalCase, fiveDayKyotoPlan());

        assertFalse(result.score() == result.maxScore());
        assertTrue(result.rules().stream()
                .anyMatch(rule -> rule.rule().equals("case-alignment") && !rule.passed()));
    }

    private TravelEvalCase caseById(String id) {
        return harness.loadDefaultCases().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private TravelPlan strongPlan(TravelEvalCase evalCase) {
        return new TravelPlan(
                "Strong plan for the selected eval case.",
                evalCase.expectedDestination(),
                "Shanghai",
                evalCase.expectedDays(),
                evalCase.expectedTravelers(),
                new TravelPlan.Budget(
                        evalCase.expectedBudgetTotal(),
                        evalCase.expectedCurrency(),
                        List.of(
                                new TravelPlan.BudgetItem("Transport", BigDecimal.valueOf(8000), "estimated"),
                                new TravelPlan.BudgetItem("Hotel", BigDecimal.valueOf(7000), "estimated")
                        ),
                        "Prices are estimates and should be checked before booking."
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "Arrival", List.of(), List.of("simple meal"), "station area", "train", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "Classic route", List.of(), List.of("local meal"), "station area", "metro", "relaxed", List.of())
                ),
                List.of("Prefer direct trains and fewer transfers."),
                List.of("Stay near transit."),
                List.of("Weather, visa, and ticket prices may change; verify before booking."),
                List.of("Reduce walking if family members are tired."),
                evalCase.expectedSkills()
        );
    }

    private TravelPlan fiveDayKyotoPlan() {
        return new TravelPlan(
                "A relaxed 5-day Kyoto plan. Budget is estimated for 2 people.",
                "京都",
                "Shanghai",
                5,
                null,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(15000),
                        "CNY",
                        List.of(
                                new TravelPlan.BudgetItem("Flights", BigDecimal.valueOf(6000), "estimated for 2 people"),
                                new TravelPlan.BudgetItem("Hotel", BigDecimal.valueOf(4000), "estimated for 2 people")
                        ),
                        "Estimated for 2 people; actual cost changes with travelers."
                ),
                List.of(
                        new TravelPlan.ItineraryDay(1, "Arrival", List.of(), List.of(), "Kyoto", "train", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(2, "Higashiyama", List.of(), List.of(), "Kyoto", "bus", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(3, "Arashiyama", List.of(), List.of(), "Kyoto", "train", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(4, "Kinkakuji", List.of(), List.of(), "Kyoto", "bus", "relaxed", List.of()),
                        new TravelPlan.ItineraryDay(5, "Return", List.of(), List.of(), null, "train", "relaxed", List.of())
                ),
                List.of("JR and bus"),
                List.of("Kyoto Station hotel"),
                List.of("Prices and traveler count need confirmation."),
                List.of("Add Nara if days increase."),
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel")
        );
    }

    private TravelEvalCase currentKyotoCase() {
        return new TravelEvalCase(
                "current-plan",
                "Current TravelPlan request",
                "Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget.",
                "Kyoto",
                5,
                null,
                BigDecimal.valueOf(15000),
                "CNY",
                List.of("family-trip-planning", "japan-travel", "budget-travel", "relaxed-travel"),
                false,
                List.of("terminal", "file-write", "resource-download")
        );
    }
}
