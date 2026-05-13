package com.seewhy.syaiagent.orchestrator;

import com.seewhy.syaiagent.guardrail.GuardrailResult;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RequirementCollectorService {

    private static final String CHINESE_NUMBER = "[一二两三四五六七八九十百千万]+";
    private static final Pattern DAYS_PATTERN = Pattern.compile(
            "(\\d+|" + CHINESE_NUMBER + ")\\s*(天|日|晚|day|days)|\\d+\\s*[- ]?day",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAVELERS_PATTERN = Pattern.compile(
            "(\\d+|" + CHINESE_NUMBER + ")\\s*(人|位|个|traveler|travelers|people|persons|adults|kids|children)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(预算|budget|(\\d+|" + CHINESE_NUMBER + ")\\s*(万|元|块|cny|rmb|usd|jpy))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUDGET_VALUE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?|" + CHINESE_NUMBER + ")\\s*(万|元|块|cny|rmb|usd|jpy)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUDGET_AFTER_WORD_PATTERN = Pattern.compile(
            "(?:预算|budget)\\s*(?:是|为|:|：)?\\s*(\\d+(?:\\.\\d+)?|" + CHINESE_NUMBER + ")\\s*(万|元|块|cny|rmb|usd|jpy)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?|" + CHINESE_NUMBER);
    private static final Pattern EN_FROM_TO_PATTERN = Pattern.compile(
            "\\bfrom\\s+([a-zA-Z\\s]{2,40}?)\\s+to\\s+([a-zA-Z\\s]{2,40}?)(?=\\s+(?:with|for|in|on|and|\\d)|[,.。]|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ZH_FROM_TO_PATTERN = Pattern.compile(
            "(?:从)?([\\p{IsHan}A-Za-z]{2,20})(?:出发)?(?:到|去|前往)([\\p{IsHan}A-Za-z]{2,20})"
    );
    private static final Map<String, String> DESTINATION_HINTS = orderedHints(
            "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "天津", "南京", "苏州", "云南", "新疆",
            "日本", "东京", "大阪", "京都", "japan", "tokyo", "osaka", "kyoto", "chengdu", "beijing", "shanghai",
            "hangzhou", "suzhou", "guangzhou", "shenzhen"
    );

    private final GuardrailService guardrailService;

    public RequirementCollectorService(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    public TravelRequirement collect(String message) {
        GuardrailResult guardrailResult = guardrailService.inspectTravelInput(message);
        if (!guardrailResult.allowed()) {
            throw new IllegalArgumentException(guardrailResult.message());
        }
        String normalized = guardrailResult.normalizedInput();
        ExtractedRequirement extracted = extract(normalized);
        List<String> missingFields = detectMissingFields(normalized, extracted);
        return new TravelRequirement(
                normalized,
                guardrailResult.travelRelated(),
                missingFields,
                detectTaskType(normalized),
                extracted.departure(),
                extracted.destination(),
                extracted.days(),
                extracted.travelers(),
                extracted.budgetTotal(),
                extracted.currency()
        );
    }

    private List<String> detectMissingFields(String message, ExtractedRequirement extracted) {
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        if (extracted.destination() == null && DESTINATION_HINTS.keySet().stream().noneMatch(lower::contains)) {
            missing.add("destination");
        }
        if (extracted.days() == null) {
            missing.add("days");
        }
        if (extracted.travelers() == null) {
            missing.add("travelers");
        }
        if (extracted.budgetTotal() == null && !BUDGET_PATTERN.matcher(lower).find()) {
            missing.add("budget");
        }
        return missing;
    }

    private ExtractedRequirement extract(String message) {
        Route route = extractRoute(message);
        Budget budget = extractBudget(message).orElse(new Budget(null, null));
        return new ExtractedRequirement(
                route.departure(),
                route.destination().orElseGet(() -> firstHint(message).orElse(null)),
                firstNumber(DAYS_PATTERN.matcher(message)),
                firstNumber(TRAVELERS_PATTERN.matcher(message)),
                budget.total(),
                budget.currency()
        );
    }

    private Route extractRoute(String message) {
        Matcher english = EN_FROM_TO_PATTERN.matcher(message);
        if (english.find()) {
            return new Route(cleanPlace(english.group(1)), Optional.of(cleanPlace(english.group(2))));
        }
        Matcher chinese = ZH_FROM_TO_PATTERN.matcher(message);
        if (chinese.find()) {
            String departure = cleanPlace(chinese.group(1));
            String destination = cleanPlace(chinese.group(2));
            return new Route(firstHint(departure).orElse(departure), Optional.of(firstHint(destination).orElse(destination)));
        }
        return new Route(null, Optional.empty());
    }

    private Optional<Budget> extractBudget(String message) {
        Matcher valueMatcher = BUDGET_VALUE_PATTERN.matcher(message);
        Budget best = null;
        while (valueMatcher.find()) {
            Budget candidate = toBudget(valueMatcher.group(1), valueMatcher.group(2));
            if (best == null || valueMatcher.start() > 0 && message.substring(0, valueMatcher.start()).contains("预算")) {
                best = candidate;
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        Matcher wordMatcher = BUDGET_AFTER_WORD_PATTERN.matcher(message);
        if (wordMatcher.find()) {
            return Optional.of(toBudget(wordMatcher.group(1), wordMatcher.group(2)));
        }
        return Optional.empty();
    }

    private Budget toBudget(String rawAmount, String unit) {
        BigDecimal amount = parseDecimal(rawAmount);
        String normalizedUnit = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
        String currency = switch (normalizedUnit) {
            case "usd" -> "USD";
            case "jpy" -> "JPY";
            default -> "CNY";
        };
        if ("万".equals(normalizedUnit) && amount != null) {
            amount = amount.multiply(BigDecimal.valueOf(10_000));
        }
        return new Budget(amount, currency);
    }

    private Integer firstNumber(Matcher matcher) {
        if (!matcher.find()) {
            return null;
        }
        Matcher numberMatcher = NUMBER_TOKEN_PATTERN.matcher(matcher.group());
        return numberMatcher.find() ? parseInteger(numberMatcher.group()) : null;
    }

    private Integer parseInteger(String raw) {
        BigDecimal decimal = parseDecimal(raw);
        return decimal == null ? null : decimal.intValue();
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.strip();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            Integer chinese = parseChineseNumber(normalized);
            return chinese == null ? null : BigDecimal.valueOf(chinese);
        }
    }

    private Integer parseChineseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.replace("两", "二");
        int total = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = chineseDigit(value.charAt(i));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(value.charAt(i));
            if (unit == 10 || unit == 100 || unit == 1000) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            } else if (unit == 10_000) {
                total += (section + number) * unit;
                section = 0;
                number = 0;
            } else {
                return null;
            }
        }
        return total + section + number;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            case '零' -> 0;
            default -> -1;
        };
    }

    private int chineseUnit(char value) {
        return switch (value) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            case '万' -> 10_000;
            default -> -1;
        };
    }

    private Optional<String> firstHint(String text) {
        String lower = String.valueOf(text).toLowerCase(Locale.ROOT);
        return DESTINATION_HINTS.entrySet().stream()
                .filter(entry -> lower.contains(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> lower.indexOf(entry.getKey())))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private String cleanPlace(String value) {
        return value == null ? null : value.replaceAll("(?i)\\b(plan|a|relaxed|trip)\\b", "")
                .replaceAll("[，,。.!?].*$", "")
                .strip();
    }

    private TravelTaskType detectTaskType(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("报告") || lower.contains("report")) {
            return TravelTaskType.REPORT;
        }
        return TravelTaskType.STRUCTURED_PLAN;
    }

    private static Map<String, String> orderedHints(String... values) {
        Map<String, String> hints = new LinkedHashMap<>();
        for (String value : values) {
            hints.put(value.toLowerCase(Locale.ROOT), value);
        }
        return hints;
    }

    private record ExtractedRequirement(
            String departure,
            String destination,
            Integer days,
            Integer travelers,
            BigDecimal budgetTotal,
            String currency
    ) {
    }

    private record Route(String departure, Optional<String> destination) {
    }

    private record Budget(BigDecimal total, String currency) {
    }
}
