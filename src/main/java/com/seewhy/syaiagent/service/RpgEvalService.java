package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.eval.TravelEvalCase;
import com.seewhy.syaiagent.eval.TravelEvalHarness;
import com.seewhy.syaiagent.eval.TravelEvalResult;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.rpg.RpgEvalCurrentPlanScoreRequest;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunRequest;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunResponse;
import com.seewhy.syaiagent.model.rpg.RpgEvalRule;
import com.seewhy.syaiagent.model.rpg.RpgEvalSampleResult;
import com.seewhy.syaiagent.model.rpg.RpgEvalScoreRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

@Service
public class RpgEvalService {

    private static final List<String> DEFAULT_DISALLOWED_TOOLS = List.of("terminal", "file-write", "resource-download");
    private static final Pattern ENGLISH_TO_DESTINATION = Pattern.compile("\\bto\\s+([A-Z][A-Za-z .'-]{1,40}?)(?:\\s+with|\\s+for|\\s+from|\\s+in\\s+\\d|\\s+\\d|\\s+budget|\\s*$|[,.])");
    private static final Pattern CHINESE_DESTINATION = Pattern.compile("(?:去|在)([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z .'-]{0,20}?)(?:做|玩|旅行|旅游|citywalk|，|,|。|\\s|$)");
    private static final Pattern ENGLISH_DAYS = Pattern.compile("\\b(\\d{1,2})\\s*(?:-|\\s)?days?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_DAYS = Pattern.compile("(\\d{1,2}|一|二|两|三|四|五|六|七|八|九|十)\\s*(?:天|日)");
    private static final Pattern ENGLISH_TRAVELERS = Pattern.compile("\\b(\\d{1,2})\\s*(?:people|persons|travelers|adults|kids|children|family members)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_TRAVELERS = Pattern.compile("(\\d{1,2}|一|二|两|三|四|五|六|七|八|九|十)\\s*(?:个人|人|位|个大人|个孩子|个成人|个小孩)");
    private static final Pattern ENGLISH_BUDGET = Pattern.compile("\\b(?:budget\\s*(?:of|is|:)?\\s*)?(\\d{3,7})(?:\\s*(CNY|RMB|USD|JPY))?\\s*(?:budget)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_BUDGET = Pattern.compile("预算\\s*(\\d+(?:\\.\\d+)?)\\s*(万|千)?\\s*(CNY|RMB|人民币|元)?", Pattern.CASE_INSENSITIVE);

    private final TravelEvalHarness travelEvalHarness;
    private final WayfinderDemoService wayfinderDemoService;
    private final WayfinderTravelFacade wayfinderTravelFacade;

    public RpgEvalService(TravelEvalHarness travelEvalHarness, WayfinderDemoService wayfinderDemoService) {
        this(travelEvalHarness, wayfinderDemoService, null);
    }

    @Autowired
    public RpgEvalService(TravelEvalHarness travelEvalHarness,
                          WayfinderDemoService wayfinderDemoService,
                          WayfinderTravelFacade wayfinderTravelFacade) {
        this.travelEvalHarness = travelEvalHarness;
        this.wayfinderDemoService = wayfinderDemoService;
        this.wayfinderTravelFacade = wayfinderTravelFacade;
    }

    public List<TravelEvalCase> getCases() {
        return travelEvalHarness.loadDefaultCases();
    }

    public List<RpgEvalRule> getRules() {
        return List.of(
                new RpgEvalRule("case-alignment", "Case Alignment", 20,
                        "Checks whether the TravelPlan matches the selected eval case destination, days, travelers, budget, and skills."),
                new RpgEvalRule("clarifying-question", "Ask Missing Info", 10,
                        "Checks whether underspecified requests ask for destination, days, budget, travelers, or preferences."),
                new RpgEvalRule("structured-itinerary", "Structured Itinerary", 15,
                        "Checks whether the TravelPlan contains itinerary day structure suitable for UI cards and matches expected trip length."),
                new RpgEvalRule("budget-reasonableness", "Budget Reasonableness", 15,
                        "Checks budget currency, plausible totals, and itemized or explained estimates."),
                new RpgEvalRule("risk-reminders", "Risk Reminders", 15,
                        "Checks whether weather, visa, policy, health, schedule, or other uncertainty reminders are present."),
                new RpgEvalRule("unsafe-claims", "No Absolute Promise", 15,
                        "Penalizes guarantees about safety, visa approval, prices, weather, or opening hours."),
                new RpgEvalRule("disallowed-tools", "No Forbidden Tools", 5,
                        "Checks deterministic eval paths do not rely on terminal, file-write, or resource-download tools."),
                new RpgEvalRule("expected-skills", "Skills Loaded", 5,
                        "Checks whether expected travel skills are represented in the generated TravelPlan.")
        );
    }

    public List<RpgEvalSampleResult> getSampleResults() {
        return wayfinderDemoService.demoEvalResults();
    }

    public RpgEvalRunResponse runEval(String caseId, RpgEvalRunRequest request) {
        TravelEvalCase evalCase = findCase(caseId);
        String input = request != null && request.input() != null && !request.input().isBlank()
                ? request.input().trim()
                : evalCase.input();
        String chatId = request != null && request.chatId() != null && !request.chatId().isBlank()
                ? request.chatId().trim()
                : "eval-" + evalCase.id() + "-" + UUID.randomUUID().toString().substring(0, 8);
        TravelPlan plan = generatePlan(input, chatId);
        List<String> observedToolCalls = List.of();
        TravelEvalResult result = travelEvalHarness.evaluate(evalCase, plan, observedToolCalls);
        return new RpgEvalRunResponse(evalCase, input, plan, result, observedToolCalls);
    }

    public RpgEvalRunResponse scorePlan(RpgEvalScoreRequest request) {
        if (request == null || request.caseId() == null || request.caseId().isBlank()) {
            throw new IllegalArgumentException("Eval case id is required.");
        }
        if (request.plan() == null) {
            throw new IllegalArgumentException("TravelPlan is required.");
        }
        TravelEvalCase evalCase = findCase(request.caseId().trim());
        List<String> observedToolCalls = request.observedToolCalls();
        TravelEvalResult result = travelEvalHarness.evaluate(evalCase, request.plan(), observedToolCalls);
        return new RpgEvalRunResponse(evalCase, evalCase.input(), request.plan(), result, observedToolCalls);
    }

    public RpgEvalRunResponse scoreCurrentPlan(RpgEvalCurrentPlanScoreRequest request) {
        if (request == null || request.plan() == null) {
            throw new IllegalArgumentException("TravelPlan is required.");
        }
        String input = request.input() == null || request.input().isBlank()
                ? "Current TravelPlan request"
                : request.input().trim();
        TravelEvalCase evalCase = buildCurrentPlanCase(input, request.plan());
        List<String> observedToolCalls = request.observedToolCalls();
        TravelEvalResult result = travelEvalHarness.evaluateCurrentPlan(evalCase, request.plan(), observedToolCalls);
        return new RpgEvalRunResponse(evalCase, input, request.plan(), result, observedToolCalls);
    }

    private TravelEvalCase findCase(String caseId) {
        return getCases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Eval case not found: " + caseId));
    }

    private TravelPlan generatePlan(String input, String chatId) {
        if (wayfinderDemoService.isEnabled() || wayfinderTravelFacade == null) {
            return wayfinderDemoService.demoTravelPlan();
        }
        return wayfinderTravelFacade.doStructuredPlan(input, chatId);
    }

    private TravelEvalCase buildCurrentPlanCase(String input, TravelPlan plan) {
        String destination = firstNonBlank(extractDestination(input), plan.destination());
        Integer days = firstNonNull(extractDays(input), plan.days());
        Integer travelers = extractTravelers(input);
        BigDecimal budgetTotal = firstNonNull(extractBudgetTotal(input), plan.budget() == null ? null : plan.budget().total());
        String currency = extractCurrency(input);
        if (currency == null && plan.budget() != null) {
            currency = plan.budget().currency();
        }
        List<String> expectedSkills = plan.loadedSkills();
        boolean requiresClarifyingQuestion = !notBlank(destination) || days == null || budgetTotal == null;
        return new TravelEvalCase(
                "current-plan",
                "Current TravelPlan request",
                input,
                destination,
                days,
                travelers,
                budgetTotal,
                currency == null ? "CNY" : currency,
                expectedSkills,
                requiresClarifyingQuestion,
                DEFAULT_DISALLOWED_TOOLS
        );
    }

    private String extractDestination(String input) {
        String value = input == null ? "" : input.trim();
        Matcher english = ENGLISH_TO_DESTINATION.matcher(value);
        if (english.find()) {
            return cleanDestination(english.group(1));
        }
        Matcher chinese = CHINESE_DESTINATION.matcher(value);
        if (chinese.find()) {
            return cleanDestination(chinese.group(1));
        }
        return null;
    }

    private Integer extractDays(String input) {
        String value = input == null ? "" : input;
        Matcher english = ENGLISH_DAYS.matcher(value);
        if (english.find()) {
            return Integer.valueOf(english.group(1));
        }
        Matcher chinese = CHINESE_DAYS.matcher(value);
        if (chinese.find()) {
            return parseSmallNumber(chinese.group(1));
        }
        return null;
    }

    private Integer extractTravelers(String input) {
        String value = input == null ? "" : input;
        Matcher english = ENGLISH_TRAVELERS.matcher(value);
        if (english.find()) {
            return Integer.valueOf(english.group(1));
        }
        Matcher chinese = CHINESE_TRAVELERS.matcher(value);
        if (chinese.find()) {
            return parseSmallNumber(chinese.group(1));
        }
        return null;
    }

    private BigDecimal extractBudgetTotal(String input) {
        String value = input == null ? "" : input;
        Matcher chinese = CHINESE_BUDGET.matcher(value);
        if (chinese.find()) {
            BigDecimal base = new BigDecimal(chinese.group(1));
            String unit = chinese.group(2);
            if ("万".equals(unit)) {
                return base.multiply(BigDecimal.valueOf(10000));
            }
            if ("千".equals(unit)) {
                return base.multiply(BigDecimal.valueOf(1000));
            }
            return base;
        }
        Matcher english = ENGLISH_BUDGET.matcher(value);
        while (english.find()) {
            String currency = english.group(2);
            String prefix = value.substring(Math.max(0, english.start() - 12), english.start()).toLowerCase(Locale.ROOT);
            String suffix = value.substring(english.end(), Math.min(value.length(), english.end() + 12)).toLowerCase(Locale.ROOT);
            if (currency != null || prefix.contains("budget") || suffix.contains("budget")) {
                return new BigDecimal(english.group(1));
            }
        }
        return null;
    }

    private String extractCurrency(String input) {
        String normalized = input == null ? "" : input.toUpperCase(Locale.ROOT);
        if (normalized.contains("USD")) return "USD";
        if (normalized.contains("JPY")) return "JPY";
        if (normalized.contains("RMB") || normalized.contains("CNY") || normalized.contains("人民币") || normalized.contains("元")) {
            return "CNY";
        }
        return "CNY";
    }

    private String cleanDestination(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(a|an|the|relaxed|family|trip|travel|plan)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private Integer parseSmallNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.matches("\\d+")) {
            return Integer.valueOf(value);
        }
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    private String firstNonBlank(String first, String second) {
        return notBlank(first) ? first : second;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
