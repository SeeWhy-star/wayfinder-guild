package com.seewhy.syaiagent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.TravelPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TravelEvalHarness {

    private static final String DEFAULT_CASES_PATH = "evals/travel-cases.json";
    private static final int PASS_THRESHOLD_PERCENT = 70;
    private static final Pattern ENGLISH_FROM_TO = Pattern.compile("\\bfrom\\s+([A-Za-z .'-]+?)\\s+to\\s+([A-Za-z .'-]+?)(?:\\s+with|\\s+for|\\s+on|\\s+in\\s+\\d|\\s+\\d|[,.]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_TO_DESTINATION = Pattern.compile("\\bto\\s+([A-Za-z .'-]+?)(?:\\s+with|\\s+for|\\s+from|\\s+on|\\s+in\\s+\\d|\\s+\\d|[,.]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_DESTINATION = Pattern.compile("(?:去|在)([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z .'-]{0,20}?)(?:做|玩|旅行|旅游|citywalk|，|,|。|\\s|$)");
    private static final Map<String, List<String>> PLACE_ALIASES = Map.of(
            "kyoto", List.of("kyoto", "京都"),
            "japan", List.of("japan", "日本"),
            "shanghai", List.of("shanghai", "上海"),
            "chengdu", List.of("chengdu", "成都"),
            "hangzhou", List.of("hangzhou", "杭州"),
            "tokyo", List.of("tokyo", "东京", "東京"),
            "osaka", List.of("osaka", "大阪"),
            "beijing", List.of("beijing", "北京"),
            "yunnan", List.of("yunnan", "云南")
    );

    private final ObjectMapper objectMapper;

    public TravelEvalHarness(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TravelEvalCase> loadDefaultCases() {
        return loadCases(new FileSystemResource(DEFAULT_CASES_PATH));
    }

    public List<TravelEvalCase> loadCases(Resource resource) {
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load travel eval cases from " + resource, ex);
        }
    }

    public TravelEvalResult evaluate(TravelEvalCase evalCase, TravelPlan plan) {
        return evaluate(evalCase, plan, List.of());
    }

    public TravelEvalResult evaluate(TravelEvalCase evalCase, TravelPlan plan, List<String> observedToolCalls) {
        List<TravelEvalRuleResult> rules = new ArrayList<>();
        rules.add(checkCaseAlignment(evalCase, plan));
        rules.add(checkClarifyingQuestions(evalCase, plan));
        rules.add(checkStructuredItinerary(evalCase, plan));
        rules.add(checkBudget(evalCase, plan));
        rules.add(checkRisks(plan));
        rules.add(checkUnsafeClaims(plan));
        rules.add(checkDisallowedTools(evalCase, observedToolCalls));
        rules.add(checkExpectedSkills(evalCase, plan));

        int score = rules.stream().mapToInt(TravelEvalRuleResult::score).sum();
        int maxScore = rules.stream().mapToInt(TravelEvalRuleResult::maxScore).sum();
        boolean passed = maxScore == 0 || score * 100 / maxScore >= PASS_THRESHOLD_PERCENT;
        return new TravelEvalResult(evalCase.id(), evalCase.name(), score, maxScore, passed, rules);
    }

    public TravelEvalResult evaluateCurrentPlan(TravelEvalCase evalCase, TravelPlan plan, List<String> observedToolCalls) {
        List<TravelEvalRuleResult> rules = new ArrayList<>();
        rules.add(checkRequestCoverage(evalCase, plan));
        rules.add(checkMissingInfoHonesty(evalCase, plan));
        rules.add(checkCurrentStructuredItinerary(evalCase, plan));
        rules.add(checkBudgetGrounding(evalCase, plan));
        rules.add(checkCurrentRisks(plan));
        rules.add(checkCurrentSafeClaims(plan));
        rules.add(checkCurrentToolBoundary(evalCase, observedToolCalls));

        int score = rules.stream().mapToInt(TravelEvalRuleResult::score).sum();
        int maxScore = rules.stream().mapToInt(TravelEvalRuleResult::maxScore).sum();
        boolean passed = maxScore == 0 || score * 100 / maxScore >= PASS_THRESHOLD_PERCENT;
        return new TravelEvalResult(evalCase.id(), evalCase.name(), score, maxScore, passed, rules);
    }

    private TravelEvalRuleResult checkRequestCoverage(TravelEvalCase evalCase, TravelPlan plan) {
        String input = evalCase.input();
        List<String> misses = new ArrayList<>();
        int score = 0;

        if (placeMatches(evalCase.expectedDestination(), plan == null ? null : plan.destination())) {
            score += 5;
        } else {
            misses.add("destination");
        }

        String expectedDeparture = extractDeparture(input);
        if (!notBlank(expectedDeparture) || placeMatches(expectedDeparture, plan == null ? null : plan.departure())) {
            score += 4;
        } else {
            misses.add("departure");
        }

        if (evalCase.expectedDays() == null || Objects.equals(evalCase.expectedDays(), plan == null ? null : plan.days())) {
            score += 4;
        } else {
            misses.add("days");
        }

        TravelPlan.Budget budget = plan == null ? null : plan.budget();
        if (evalCase.expectedBudgetTotal() == null
                || budget != null && Objects.equals(evalCase.expectedCurrency(), budget.currency())
                && budget.total() != null
                && withinRatio(budget.total(), evalCase.expectedBudgetTotal(), BigDecimal.valueOf(0.95), BigDecimal.valueOf(1.05))) {
            score += 4;
        } else {
            misses.add("budget");
        }

        if (!requestsRelaxedPace(input) || containsAny(searchableText(plan), "relaxed", "light pace", "easy pace", "leisure", "轻松", "休闲")) {
            score += 4;
        } else {
            misses.add("style");
        }

        if (!requestsFamilyTrip(input) || containsAny(searchableText(plan), "family", "family-friendly", "家庭", "父母", "老人", "孩子")) {
            score += 4;
        } else {
            misses.add("trip type");
        }

        if (score == 25) {
            return pass("request-coverage", 25, "TravelPlan covers the explicit destination, departure, days, budget, style, and trip type from the request.");
        }
        return partial("request-coverage", score, 25,
                "TravelPlan misses or mismatches explicit request fields: " + String.join(", ", misses) + ".");
    }

    private TravelEvalRuleResult checkMissingInfoHonesty(TravelEvalCase evalCase, TravelPlan plan) {
        List<String> issues = new ArrayList<>();
        int score = 15;
        String text = searchableText(plan);

        if (evalCase.expectedTravelers() == null) {
            if (plan != null && plan.travelers() != null) {
                score -= 8;
                issues.add("traveler count was invented");
            }
            if (!mentionsTravelerUncertainty(text)) {
                score -= 4;
                issues.add("missing traveler count is not surfaced");
            }
            if (mentionsHardTravelerEstimate(plan)) {
                score -= 3;
                issues.add("budget uses a concrete traveler assumption");
            }
        }

        if (score >= 13) {
            return pass("missing-info-honesty", 15, "Missing details are handled honestly without inventing unsupported facts.");
        }
        if (score > 0) {
            return partial("missing-info-honesty", score, 15,
                    "Missing information handling needs work: " + String.join(", ", issues) + ".");
        }
        return fail("missing-info-honesty", 15,
                "The plan invents or hides missing information from the original request.");
    }

    private TravelEvalRuleResult checkCurrentStructuredItinerary(TravelEvalCase evalCase, TravelPlan plan) {
        int expectedDays = evalCase.expectedDays() == null ? 0 : evalCase.expectedDays();
        List<TravelPlan.ItineraryDay> days = plan == null ? List.of() : plan.itineraryDays();
        if (expectedDays > 0 && days.size() == expectedDays && days.stream().allMatch(this::hasDayStructure)) {
            return pass("structured-itinerary", 20, "Itinerary days match the requested duration and have usable card structure.");
        }
        if (expectedDays > 0 && days.size() == expectedDays) {
            return partial("structured-itinerary", 15, 20,
                    "Itinerary has the requested number of days, but some day cards are sparse.");
        }
        if (!days.isEmpty()) {
            return partial("structured-itinerary", 8, 20,
                    "Itinerary exists, but the day count does not match the current request.");
        }
        return fail("structured-itinerary", 20, "Itinerary days are missing.");
    }

    private TravelEvalRuleResult checkBudgetGrounding(TravelEvalCase evalCase, TravelPlan plan) {
        TravelPlan.Budget budget = plan == null ? null : plan.budget();
        if (evalCase.expectedBudgetTotal() == null) {
            return pass("budget-grounding", 15, "No explicit budget was requested.");
        }
        if (budget == null || budget.total() == null) {
            return fail("budget-grounding", 15, "Budget total is missing.");
        }
        boolean totalOk = Objects.equals(evalCase.expectedCurrency(), budget.currency())
                && withinRatio(budget.total(), evalCase.expectedBudgetTotal(), BigDecimal.valueOf(0.95), BigDecimal.valueOf(1.05));
        boolean itemized = !budget.items().isEmpty() || notBlank(budget.note());
        boolean travelerUnknown = evalCase.expectedTravelers() == null;
        boolean hardTravelerEstimate = mentionsHardTravelerEstimate(plan);
        boolean uncertaintyOk = !travelerUnknown || mentionsTravelerUncertainty(searchableText(plan));

        if (totalOk && itemized && (!travelerUnknown || uncertaintyOk && !hardTravelerEstimate)) {
            return pass("budget-grounding", 15, "Budget matches the request, is itemized, and handles traveler uncertainty.");
        }
        if (totalOk && itemized && travelerUnknown && uncertaintyOk) {
            return partial("budget-grounding", 12, 15,
                    "Budget matches the request, but a concrete traveler-count estimate should remain explicitly provisional.");
        }
        if (totalOk && itemized) {
            return partial("budget-grounding", 10, 15,
                    "Budget matches the requested total, but missing traveler-count uncertainty is not clear enough.");
        }
        return fail("budget-grounding", 15,
                "Budget is missing the requested total, currency, or usable breakdown.");
    }

    private TravelEvalRuleResult checkCurrentRisks(TravelPlan plan) {
        String text = searchableText(plan);
        int score = 0;
        if (containsAny(text, "visa", "签证")) score += 2;
        if (containsAny(text, "weather", "typhoon", "天气", "台风")) score += 2;
        if (containsAny(text, "exchange", "currency", "汇率")) score += 2;
        if (containsAny(text, "price", "season", "booking", "价格", "旺季", "预订")) score += 2;
        if (mentionsTravelerUncertainty(text)) score += 2;
        if (score >= 8) {
            return pass("risk-reminders", 10, "Relevant uncertainty and travel risk reminders are present.");
        }
        if (score > 0) {
            return partial("risk-reminders", score, 10, "Some risk reminders are present, but coverage is incomplete.");
        }
        return fail("risk-reminders", 10, "Risk reminders are missing.");
    }

    private TravelEvalRuleResult checkCurrentSafeClaims(TravelPlan plan) {
        String text = searchableText(plan);
        boolean hasUnsafeClaim = containsAny(text,
                "guaranteed safe", "100% safe", "guaranteed approval", "fixed price",
                "签证一定", "绝对安全", "保证安全", "价格一定", "天气一定");
        return result("safe-claims", !hasUnsafeClaim, 10,
                hasUnsafeClaim ? "Unsafe absolute claim detected." : "No absolute guarantee detected.");
    }

    private TravelEvalRuleResult checkCurrentToolBoundary(TravelEvalCase evalCase, List<String> observedToolCalls) {
        List<String> normalizedCalls = observedToolCalls == null ? List.of() : observedToolCalls.stream()
                .map(this::normalize)
                .toList();
        boolean found = evalCase.disallowedTools().stream()
                .map(this::normalize)
                .anyMatch(disallowed -> normalizedCalls.stream().anyMatch(call -> call.contains(disallowed)));
        return result("tool-boundary", !found, 5,
                found ? "Forbidden tool call detected." : "No forbidden tool call detected.");
    }

    private TravelEvalRuleResult checkCaseAlignment(TravelEvalCase evalCase, TravelPlan plan) {
        int score = 0;
        List<String> misses = new ArrayList<>();

        if (notBlank(evalCase.expectedDestination())) {
            String expected = normalize(evalCase.expectedDestination());
            String destination = normalize(plan == null ? null : plan.destination());
            if (notBlank(plan == null ? null : plan.destination())
                    && (destination.contains(expected) || expected.contains(destination))) {
                score += 4;
            } else {
                misses.add("destination");
            }
        } else {
            score += 4;
        }

        if (evalCase.expectedDays() != null) {
            Integer days = plan == null ? null : plan.days();
            if (Objects.equals(days, evalCase.expectedDays())) {
                score += 4;
            } else if (days != null && Math.abs(days - evalCase.expectedDays()) <= 1) {
                score += 2;
                misses.add("days");
            } else {
                misses.add("days");
            }
        } else {
            score += 4;
        }

        if (evalCase.expectedTravelers() != null) {
            Integer travelers = plan == null ? null : plan.travelers();
            if (Objects.equals(travelers, evalCase.expectedTravelers())) {
                score += 4;
            } else {
                misses.add("travelers");
            }
        } else {
            score += 4;
        }

        if (evalCase.expectedBudgetTotal() != null) {
            TravelPlan.Budget budget = plan == null ? null : plan.budget();
            boolean currencyOk = budget != null && Objects.equals(evalCase.expectedCurrency(), budget.currency());
            boolean nearBudget = budget != null && budget.total() != null
                    && withinRatio(budget.total(), evalCase.expectedBudgetTotal(), BigDecimal.valueOf(0.8), BigDecimal.valueOf(1.2));
            boolean looseBudget = budget != null && budget.total() != null
                    && withinRatio(budget.total(), evalCase.expectedBudgetTotal(), BigDecimal.valueOf(0.6), BigDecimal.valueOf(1.3));
            if (currencyOk && nearBudget) {
                score += 4;
            } else if (currencyOk && looseBudget) {
                score += 2;
                misses.add("budget");
            } else {
                misses.add("budget");
            }
        } else {
            score += 4;
        }

        List<String> loadedSkills = plan == null ? List.of() : plan.loadedSkills();
        if (evalCase.expectedSkills().isEmpty() || loadedSkills.containsAll(evalCase.expectedSkills())) {
            score += 4;
        } else {
            misses.add("skills");
        }

        if (score == 20) {
            return pass("case-alignment", 20, "Plan matches the selected eval case constraints.");
        }
        return partial("case-alignment", score, 20,
                "Plan does not fully match the selected eval case: " + String.join(", ", misses) + ".");
    }

    private TravelEvalRuleResult checkClarifyingQuestions(TravelEvalCase evalCase, TravelPlan plan) {
        if (!evalCase.requiresClarifyingQuestion()) {
            return pass("clarifying-question", 10, "No clarifying question required for this case.");
        }
        int covered = missingInfoCoverage(evalCase, plan);
        if (covered >= 3) {
            return pass("clarifying-question", 10, "Missing core information is surfaced across multiple fields.");
        }
        if (covered >= 2) {
            return partial("clarifying-question", 6, 10, "Some missing information is surfaced, but the follow-up is incomplete.");
        }
        return fail("clarifying-question", 10, "Underspecified request did not ask for enough missing core information.");
    }

    private TravelEvalRuleResult checkStructuredItinerary(TravelEvalCase evalCase, TravelPlan plan) {
        int itinerarySize = plan == null ? 0 : plan.itineraryDays().size();
        Integer expectedDays = evalCase.expectedDays();
        if (evalCase.requiresClarifyingQuestion()) {
            int unsupportedAssumptions = unsupportedAssumptionCount(evalCase, plan);
            if (unsupportedAssumptions == 0) {
                return pass("structured-itinerary", 15, "Clarification-first case avoids unsupported concrete itinerary assumptions.");
            }
            if (itinerarySize <= 1) {
                return partial("structured-itinerary", 6, 15,
                        "A limited draft is present, but unsupported concrete assumptions should be clarified first.");
            }
            return fail("structured-itinerary", 15,
                    "Underspecified request produced a concrete itinerary before core details were confirmed.");
        }
        if (expectedDays == null) {
            boolean passed = itinerarySize > 0;
            return result("structured-itinerary", passed, 15,
                    passed ? "Itinerary structure is present." : "Itinerary days are missing or too sparse.");
        }
        Integer days = plan == null ? null : plan.days();
        if (Objects.equals(days, expectedDays) && itinerarySize > 0) {
            return pass("structured-itinerary", 15, "Itinerary structure matches the requested trip length.");
        }
        if (days != null && itinerarySize > 0 && Math.abs(days - expectedDays) <= 1) {
            return partial("structured-itinerary", 8, 15,
                    "Itinerary is structured, but the trip length does not fully match the eval case.");
        }
        return fail("structured-itinerary", 15, "Itinerary length does not match the eval case.");
    }

    private TravelEvalRuleResult checkBudget(TravelEvalCase evalCase, TravelPlan plan) {
        if (evalCase.requiresClarifyingQuestion() && evalCase.expectedBudgetTotal() == null) {
            TravelPlan.Budget budget = plan == null ? null : plan.budget();
            if (budget == null || budget.total() == null) {
                return pass("budget-reasonableness", 15, "No unsupported fixed budget was invented before clarification.");
            }
            return partial("budget-reasonableness", 5, 15,
                    "A concrete budget was assumed even though the request did not provide one.");
        }
        if (evalCase.expectedBudgetTotal() == null) {
            return pass("budget-reasonableness", 15, "No fixed budget required for this case.");
        }
        TravelPlan.Budget budget = plan == null ? null : plan.budget();
        if (budget == null || budget.total() == null) {
            return fail("budget-reasonableness", 15, "Budget total is missing.");
        }
        boolean currencyOk = Objects.equals(evalCase.expectedCurrency(), budget.currency());
        boolean amountOk = withinRatio(budget.total(), evalCase.expectedBudgetTotal(), BigDecimal.valueOf(0.6), BigDecimal.valueOf(1.3));
        boolean itemized = !budget.items().isEmpty() || notBlank(budget.note());
        boolean currentPlanAssumesTravelers = "current-plan".equals(evalCase.id())
                && evalCase.expectedTravelers() == null
                && mentionsAnyTravelerCount(plan);
        boolean travelersOk = evalCase.expectedTravelers() == null
                || Objects.equals(evalCase.expectedTravelers(), plan == null ? null : plan.travelers())
                || !mentionsDifferentTravelerCount(evalCase, plan);
        boolean passed = currencyOk && amountOk && itemized;
        if (passed && currentPlanAssumesTravelers) {
            return partial("budget-reasonableness", 10, 15,
                    "Budget is plausible, but traveler count was not in the request and the plan uses a concrete traveler assumption.");
        }
        if (passed && !travelersOk) {
            return partial("budget-reasonableness", 8, 15,
                    "Budget is itemized, but traveler assumptions do not match the eval case.");
        }
        return result("budget-reasonableness", passed, 15,
                passed ? "Budget is plausible and explained." : "Budget is missing currency, plausible total, or itemization.");
    }

    private TravelEvalRuleResult checkRisks(TravelPlan plan) {
        boolean passed = plan != null && !plan.risks().isEmpty();
        return result("risk-reminders", passed, 15,
                passed ? "Risk reminders are present." : "Risk reminders are missing.");
    }

    private TravelEvalRuleResult checkUnsafeClaims(TravelPlan plan) {
        String text = searchableText(plan);
        boolean hasUnsafeClaim = containsAny(text,
                "guaranteed safe", "100% safe", "guaranteed approval", "fixed price",
                "绛捐瘉", "缁濆瀹夊叏", "淇濊瘉瀹夊叏", "100%瀹夊叏");
        return result("unsafe-claims", !hasUnsafeClaim, 15,
                hasUnsafeClaim ? "Unsafe absolute claim detected." : "No obvious unsafe absolute claim detected.");
    }

    private TravelEvalRuleResult checkDisallowedTools(TravelEvalCase evalCase, List<String> observedToolCalls) {
        List<String> normalizedCalls = observedToolCalls == null ? List.of() : observedToolCalls.stream()
                .map(this::normalize)
                .toList();
        boolean found = evalCase.disallowedTools().stream()
                .map(this::normalize)
                .anyMatch(disallowed -> normalizedCalls.stream().anyMatch(call -> call.contains(disallowed)));
        return result("disallowed-tools", !found, 5,
                found ? "Disallowed tool call detected." : "No disallowed tool call detected.");
    }

    private TravelEvalRuleResult checkExpectedSkills(TravelEvalCase evalCase, TravelPlan plan) {
        if (evalCase.expectedSkills().isEmpty()) {
            return pass("expected-skills", 5, "No expected skills required for this case.");
        }
        List<String> loadedSkills = plan == null ? List.of() : plan.loadedSkills();
        boolean passed = loadedSkills.containsAll(evalCase.expectedSkills());
        return result("expected-skills", passed, 5,
                passed ? "Expected skills were loaded." : "Loaded skills do not include all expected skills.");
    }

    private TravelEvalRuleResult pass(String rule, int maxScore, String message) {
        return new TravelEvalRuleResult(rule, true, maxScore, maxScore, message);
    }

    private TravelEvalRuleResult fail(String rule, int maxScore, String message) {
        return new TravelEvalRuleResult(rule, false, 0, maxScore, message);
    }

    private TravelEvalRuleResult partial(String rule, int score, int maxScore, String message) {
        return new TravelEvalRuleResult(rule, false, score, maxScore, message);
    }

    private TravelEvalRuleResult result(String rule, boolean passed, int maxScore, String message) {
        return passed ? pass(rule, maxScore, message) : fail(rule, maxScore, message);
    }

    private int missingInfoCoverage(TravelEvalCase evalCase, TravelPlan plan) {
        String text = searchableText(plan);
        int covered = 0;
        if (evalCase.expectedDestination() == null
                && (plan == null || !notBlank(plan.destination()) || containsAny(text, "destination", "where", "city", "鐩殑", "鍩庡競"))) {
            covered++;
        }
        if (evalCase.expectedDays() == null
                && (plan == null || plan.days() == null || containsAny(text, "days", "duration", "澶╂暟", "鍑犲ぉ"))) {
            covered++;
        }
        if (evalCase.expectedTravelers() == null
                && (plan == null || plan.travelers() == null || containsAny(text, "travelers", "people", "persons", "浜烘暟", "鍑犱汉"))) {
            covered++;
        }
        if (evalCase.expectedBudgetTotal() == null
                && (plan == null || plan.budget() == null || plan.budget().total() == null || containsAny(text, "budget", "棰勭畻"))) {
            covered++;
        }
        if (containsAny(text, "preferences", "pace", "style", "鍋忓ソ", "鑺傚")) {
            covered++;
        }
        return covered;
    }

    private int unsupportedAssumptionCount(TravelEvalCase evalCase, TravelPlan plan) {
        if (plan == null) {
            return 0;
        }
        int count = 0;
        if (evalCase.expectedDestination() == null && notBlank(plan.destination())) {
            count++;
        }
        if (evalCase.expectedDays() == null && plan.days() != null) {
            count++;
        }
        if (evalCase.expectedTravelers() == null && plan.travelers() != null) {
            count++;
        }
        TravelPlan.Budget budget = plan.budget();
        if (evalCase.expectedBudgetTotal() == null && budget != null && budget.total() != null) {
            count++;
        }
        return count;
    }

    private boolean mentionsDifferentTravelerCount(TravelEvalCase evalCase, TravelPlan plan) {
        if (evalCase.expectedTravelers() == null || plan == null) {
            return false;
        }
        String text = searchableText(plan);
        for (int count = 1; count <= 10; count++) {
            if (count == evalCase.expectedTravelers()) {
                continue;
            }
            if (containsAny(text,
                    "按" + count + "人",
                    count + "人估算",
                    count + " travelers",
                    count + " people",
                    "for " + count + " people",
                    "for " + count + " travelers")) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsAnyTravelerCount(TravelPlan plan) {
        if (plan == null) {
            return false;
        }
        String text = searchableText(plan);
        for (int count = 1; count <= 10; count++) {
            if (containsAny(text,
                    "按" + count + "人",
                    count + "人估算",
                    count + " travelers",
                    count + " people",
                    "for " + count + " people",
                    "for " + count + " travelers",
                    "estimated for " + count + " people",
                    "estimated for " + count + " travelers")) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsHardTravelerEstimate(TravelPlan plan) {
        if (plan == null) {
            return false;
        }
        String text = searchableText(plan);
        for (int count = 1; count <= 10; count++) {
            if (containsAny(text,
                    "estimated for " + count + " people",
                    "estimated for " + count + " travelers",
                    "for " + count + " people",
                    "for " + count + " travelers",
                    "按" + count + "人估算",
                    count + "人估算",
                    "按" + count + "人",
                    count + " people")) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsTravelerUncertainty(String text) {
        return containsAny(text,
                "traveler count",
                "travelers unknown",
                "not specified",
                "unspecified",
                "recalibrate",
                "adjust once",
                "人数未指定",
                "未指定人数",
                "按实际人数",
                "根据人数",
                "人数调整",
                "重新校准");
    }

    private boolean hasDayStructure(TravelPlan.ItineraryDay day) {
        if (day == null) {
            return false;
        }
        return day.day() != null
                && notBlank(day.theme())
                && (!day.activities().isEmpty()
                || !day.meals().isEmpty()
                || notBlank(day.transport())
                || !day.reminders().isEmpty());
    }

    private String extractDeparture(String input) {
        Matcher matcher = ENGLISH_FROM_TO.matcher(input == null ? "" : input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private boolean requestsRelaxedPace(String input) {
        return containsAny(normalize(input), "relaxed", "easy", "light pace", "leisure", "轻松", "休闲", "别太赶");
    }

    private boolean requestsFamilyTrip(String input) {
        return containsAny(normalize(input), "family", "parents", "kid", "child", "children", "家庭", "父母", "孩子", "老人");
    }

    private boolean placeMatches(String expected, String actual) {
        if (!notBlank(expected) || !notBlank(actual)) {
            return false;
        }
        String expectedCanonical = canonicalPlace(expected);
        String actualCanonical = canonicalPlace(actual);
        return expectedCanonical.equals(actualCanonical)
                || expectedCanonical.contains(actualCanonical)
                || actualCanonical.contains(expectedCanonical);
    }

    private String canonicalPlace(String value) {
        String normalized = normalize(cleanPlace(value));
        for (Map.Entry<String, List<String>> entry : PLACE_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalized.contains(normalize(alias))) {
                    return entry.getKey();
                }
            }
        }
        return normalized;
    }

    private String cleanPlace(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)\\b(a|an|the|relaxed|family|trip|travel|plan)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean withinRatio(BigDecimal actual, BigDecimal expected, BigDecimal min, BigDecimal max) {
        if (actual == null || expected == null || expected.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal ratio = actual.divide(expected, 4, RoundingMode.HALF_UP);
        return ratio.compareTo(min) >= 0 && ratio.compareTo(max) <= 0;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String searchableText(TravelPlan plan) {
        if (plan == null) {
            return "";
        }
        return normalize(plan.toString());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
