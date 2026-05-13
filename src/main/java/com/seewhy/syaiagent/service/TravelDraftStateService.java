package com.seewhy.syaiagent.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TravelDraftStateService {

    private static final List<String> CITIES = List.of(
            "北京", "上海", "杭州", "苏州", "天津", "成都", "重庆", "广州", "深圳", "南京",
            "西安", "武汉", "长沙", "厦门", "青岛", "三亚", "昆明", "大理", "丽江",
            "东京", "京都", "大阪", "首尔", "新加坡", "曼谷", "巴黎", "伦敦", "纽约"
    );
    private static final List<String> THEMES = List.of("观光", "美食", "亲子", "家庭", "拍照", "打卡", "休闲", "逛逛", "博物馆", "历史", "购物");
    private static final Pattern PLAN_DRAFT_PATTERN = Pattern.compile("(?im)^\\s*PLAN_DRAFT:\\s*(.+?)\\s*$");
    private static final Pattern DAYS_PATTERN = Pattern.compile("([0-9]+|[一二两三四五六七八九十]+)\\s*(天|日)(?!气)");
    private static final Pattern TRAVELERS_PATTERN = Pattern.compile("([0-9]+|[一二两三四五六七八九十]+)\\s*(人|位|个人|个朋友|个大人|个成人|个孩子|个小孩)");
    private static final Pattern EXPLICIT_BUDGET_PATTERN = Pattern.compile("(?:预算|费用|总预算)\\s*([0-9]+(?:\\.[0-9]+)?|[一二两三四五六七八九十百千万]+)\\s*(万|千|元|块|cny|rmb|人民币)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?|[一二两三四五六七八九十百千万]+)\\s*(万元|万|千元|元|块|cny|rmb|人民币)", Pattern.CASE_INSENSITIVE);

    private final Map<String, DraftState> states = new ConcurrentHashMap<>();

    public DraftState updateFromTurn(String chatId, String userMessage, String aiContent) {
        DraftState current = states.computeIfAbsent(normalizeChatId(chatId), ignored -> new DraftState());
        extractPlanDraft(aiContent).ifPresent(draft -> applyText(current, draft));
        applyText(current, userMessage);
        return current.copy();
    }

    public String draftLine(String chatId) {
        return states.getOrDefault(normalizeChatId(chatId), new DraftState()).draftLine();
    }

    public String postProcessResponse(String chatId, String userMessage, String aiContent) {
        String content = aiContent == null ? "" : aiContent.stripTrailing();
        DraftState state = updateFromTurn(chatId, userMessage, content);
        String draftLine = state.draftLine();
        if (draftLine.isBlank()) {
            return content;
        }

        String withoutModelDraft = PLAN_DRAFT_PATTERN.matcher(content).replaceAll("").stripTrailing();
        String normalized = ensureReadyHint(withoutModelDraft, state);
        return normalized.isBlank() ? draftLine : normalized + System.lineSeparator() + draftLine;
    }

    public String streamCompletionSuffix(String chatId, String userMessage, String aiContent) {
        DraftState state = updateFromTurn(chatId, userMessage, aiContent);
        String draftLine = state.draftLine();
        if (draftLine.isBlank()) {
            return "";
        }
        StringBuilder suffix = new StringBuilder();
        if (!containsAny(aiContent == null ? "" : aiContent, "生成结构化计划", "Generate Structured Plan")) {
            suffix.append(System.lineSeparator())
                    .append(isChinese(aiContent) ? "信息已经基本足够，我已在下方准备好‘生成结构化计划’按钮。"
                            : "The details are ready. Use the Generate Structured Plan button below.");
        }
        suffix.append(System.lineSeparator()).append(draftLine);
        return suffix.toString();
    }

    public void clear(String chatId) {
        states.remove(normalizeChatId(chatId));
    }

    public DraftState snapshot(String chatId) {
        return states.getOrDefault(normalizeChatId(chatId), new DraftState()).copy();
    }

    private void applyText(DraftState state, String text) {
        String value = normalize(text);
        if (value.isBlank()) {
            return;
        }

        extractRoute(value, state);
        extractStandaloneDeparture(value, state);
        extractStandaloneDestination(value, state);
        extractAmbiguousCity(value, state);
        extractDays(value, state);
        extractTravelers(value, state);
        extractBudget(value, state);
        extractDate(value, state);
        extractTheme(value, state);
    }

    private void extractRoute(String text, DraftState state) {
        for (String from : CITIES) {
            for (String to : CITIES) {
                if (from.equals(to)) {
                    continue;
                }
                if (containsAny(text, from + "出发去" + to, from + "出发到" + to, from + "出发前往" + to,
                        "从" + from + "去" + to, "从" + from + "到" + to, from + "去" + to, from + "到" + to)) {
                    state.departure = from;
                    state.destination = to;
                    clearAmbiguousIfUsed(state, from, to);
                    return;
                }
            }
        }
    }

    private void extractStandaloneDeparture(String text, DraftState state) {
        for (String city : CITIES) {
            if (containsAny(text, city + "出发", "从" + city + "走", city + "是出发地")) {
                state.departure = city;
                clearAmbiguousIfUsed(state, city);
                return;
            }
        }
    }

    private void extractStandaloneDestination(String text, DraftState state) {
        for (String city : CITIES) {
            if (containsAny(text, "去" + city, "到" + city, "前往" + city, "目的地" + city, city + "是目的地")) {
                state.destination = city;
                clearAmbiguousIfUsed(state, city);
                return;
            }
        }
    }

    private void extractAmbiguousCity(String text, DraftState state) {
        if (state.departure != null || state.destination != null) {
            return;
        }
        List<String> mentioned = CITIES.stream().filter(text::contains).toList();
        if (mentioned.size() == 1 && !containsAny(text, "去", "到", "出发", "从", "目的地", "前往")) {
            state.ambiguousCity = mentioned.getFirst();
        }
    }

    private void extractDays(String text, DraftState state) {
        Matcher matcher = DAYS_PATTERN.matcher(text);
        if (matcher.find()) {
            Integer value = parseNumber(matcher.group(1));
            if (value != null && value > 0) {
                state.days = value;
            }
        }
    }

    private void extractTravelers(String text, DraftState state) {
        Matcher matcher = TRAVELERS_PATTERN.matcher(text);
        if (matcher.find()) {
            Integer value = parseNumber(matcher.group(1));
            if (value != null && value > 0) {
                state.travelers = value;
            }
        }
    }

    private void extractBudget(String text, DraftState state) {
        Matcher explicit = EXPLICIT_BUDGET_PATTERN.matcher(text);
        if (explicit.find()) {
            BigDecimal amount = parseMoney(explicit.group(1), explicit.group(2));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                state.budgetAmount = amount;
                state.budgetCurrency = "CNY";
                return;
            }
        }

        Matcher money = MONEY_PATTERN.matcher(text);
        BigDecimal best = null;
        while (money.find()) {
            BigDecimal amount = parseMoney(money.group(1), money.group(2));
            if (amount != null && (best == null || amount.compareTo(best) > 0)) {
                best = amount;
            }
        }
        if (best != null && best.compareTo(BigDecimal.ZERO) > 0) {
            state.budgetAmount = best;
            state.budgetCurrency = "CNY";
        }
    }

    private void extractDate(String text, DraftState state) {
        for (String candidate : List.of("今天", "明天", "后天", "下周", "五一", "国庆", "春节", "暑假", "寒假")) {
            if (text.contains(candidate)) {
                state.dateText = candidate + (candidate.endsWith("天") ? "出发" : "");
                return;
            }
        }
        Matcher month = Pattern.compile("([0-9]{1,2}|[一二三四五六七八九十]+)\\s*月").matcher(text);
        if (month.find()) {
            state.dateText = month.group().replaceAll("\\s+", "");
        }
    }

    private void extractTheme(String text, DraftState state) {
        for (String theme : THEMES) {
            if (text.contains(theme)) {
                state.theme = switch (theme) {
                    case "打卡" -> state.theme == null ? "拍照打卡" : state.theme;
                    case "逛逛" -> "休闲逛逛";
                    default -> theme;
                };
                return;
            }
        }
    }

    private java.util.Optional<String> extractPlanDraft(String aiContent) {
        Matcher matcher = PLAN_DRAFT_PATTERN.matcher(aiContent == null ? "" : aiContent);
        String draft = "";
        while (matcher.find()) {
            draft = matcher.group(1).trim();
        }
        return draft.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(draft);
    }

    private String ensureReadyHint(String content, DraftState state) {
        if (!state.hasCoreFields() || containsAny(content, "生成结构化计划", "Generate Structured Plan")) {
            return content;
        }
        String hint = isChinese(content) ? "信息已经基本足够，我已在下方准备好‘生成结构化计划’按钮。"
                : "The details are ready. Use the Generate Structured Plan button below.";
        return content.isBlank() ? hint : content + System.lineSeparator() + hint;
    }

    private boolean isChinese(String text) {
        return text != null && Pattern.compile("[\\u4e00-\\u9fff]").matcher(text).find();
    }

    private Integer parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("[0-9]+")) {
            return Integer.parseInt(value);
        }
        return parseChineseInteger(value);
    }

    private BigDecimal parseMoney(String rawAmount, String rawUnit) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return null;
        }
        BigDecimal amount;
        if (rawAmount.matches("[0-9]+(?:\\.[0-9]+)?")) {
            amount = new BigDecimal(rawAmount);
        } else {
            Integer parsed = parseChineseInteger(rawAmount);
            if (parsed == null) {
                return null;
            }
            amount = BigDecimal.valueOf(parsed);
        }
        String unit = rawUnit == null ? "" : rawUnit.toLowerCase(Locale.ROOT);
        boolean amountAlreadyHasUnit = rawAmount.contains("万") || rawAmount.contains("千");
        if (!amountAlreadyHasUnit && unit.contains("万")) {
            amount = amount.multiply(BigDecimal.valueOf(10000));
        } else if (!amountAlreadyHasUnit && unit.contains("千")) {
            amount = amount.multiply(BigDecimal.valueOf(1000));
        }
        return amount.stripTrailingZeros();
    }

    private Integer parseChineseInteger(String raw) {
        String value = raw.replace("两", "二").replaceAll("\\s+", "");
        if (value.isBlank()) {
            return null;
        }
        int result = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = chineseDigit(value.charAt(i));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(value.charAt(i));
            if (unit == 10000) {
                section = (section + number) == 0 ? 1 : section + number;
                result += section * unit;
                section = 0;
                number = 0;
            } else if (unit > 0) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            }
        }
        return result + section + number;
    }

    private int chineseDigit(char c) {
        return switch (c) {
            case '零' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private int chineseUnit(char c) {
        return switch (c) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            case '万' -> 10000;
            default -> -1;
        };
    }

    private void clearAmbiguousIfUsed(DraftState state, String... cities) {
        for (String city : cities) {
            if (Objects.equals(state.ambiguousCity, city)) {
                state.ambiguousCity = null;
                return;
            }
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private String normalizeChatId(String chatId) {
        return chatId == null || chatId.isBlank() ? "default" : chatId;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static final class DraftState {
        private String departure;
        private String destination;
        private Integer days;
        private Integer travelers;
        private BigDecimal budgetAmount;
        private String budgetCurrency = "CNY";
        private String dateText;
        private String theme;
        private String ambiguousCity;

        public String departure() {
            return departure;
        }

        public String destination() {
            return destination;
        }

        public Integer days() {
            return days;
        }

        public Integer travelers() {
            return travelers;
        }

        public BigDecimal budgetAmount() {
            return budgetAmount;
        }

        public String budgetCurrency() {
            return budgetCurrency;
        }

        public String dateText() {
            return dateText;
        }

        public String theme() {
            return theme;
        }

        public String ambiguousCity() {
            return ambiguousCity;
        }

        public boolean hasCoreFields() {
            return destination != null && days != null && travelers != null && budgetAmount != null;
        }

        public String draftLine() {
            if (!hasCoreFields()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            if (departure != null) {
                parts.add(departure + "出发");
            }
            parts.add("去" + destination);
            parts.add(days + "天");
            parts.add(travelers + "人");
            parts.add("预算" + formatMoney(budgetAmount) + " " + (budgetCurrency == null ? "CNY" : budgetCurrency));
            if (dateText != null) {
                parts.add(dateText.endsWith("出发") ? dateText : dateText + "出发");
            }
            if (theme != null) {
                parts.add("主题" + theme);
            }
            return "PLAN_DRAFT: " + String.join("，", parts) + "。";
        }

        private String formatMoney(BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }

        private DraftState copy() {
            DraftState copy = new DraftState();
            copy.departure = departure;
            copy.destination = destination;
            copy.days = days;
            copy.travelers = travelers;
            copy.budgetAmount = budgetAmount;
            copy.budgetCurrency = budgetCurrency;
            copy.dateText = dateText;
            copy.theme = theme;
            copy.ambiguousCity = ambiguousCity;
            return copy;
        }
    }
}
