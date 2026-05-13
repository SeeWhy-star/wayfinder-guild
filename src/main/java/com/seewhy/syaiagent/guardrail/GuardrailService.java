package com.seewhy.syaiagent.guardrail;

import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GuardrailService {

    private static final int MAX_INPUT_LENGTH = 4_000;
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}._ -]{1,120}");
    private static final Set<String> ALLOWED_COMMANDS = Set.of("dir", "type", "echo", "where", "java", "mvn");
    private static final List<String> BLOCKED_COMMAND_TOKENS = List.of(
            "del", "erase", "rd", "rmdir", "remove-item", "rm", "format", "shutdown", "taskkill",
            "reg", "powershell", "cmd /c", "curl", "wget", "scp", "ssh", ">", ">>", "|", "&&", "||", ";"
    );
    private static final List<String> TRAVEL_KEYWORDS = List.of(
            "travel", "trip", "itinerary", "hotel", "flight", "visa", "budget", "citywalk",
            "旅行", "旅游", "行程", "酒店", "机票", "签证", "预算", "景点", "美食", "出行", "攻略", "交通"
    );
    private static final List<String> PROMPT_INJECTION_PATTERNS = List.of(
            "ignore previous instructions", "ignore all previous", "system prompt", "developer message",
            "reveal prompt", "bypass", "jailbreak", "忽略之前", "忽略以上", "系统提示词", "开发者消息",
            "泄露提示词", "绕过限制", "越狱"
    );

    public GuardrailResult inspectTravelInput(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.block("message cannot be blank");
        }
        String normalized = input.strip();
        if (normalized.length() > MAX_INPUT_LENGTH) {
            return GuardrailResult.block("message is too long, max length is " + MAX_INPUT_LENGTH);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, PROMPT_INJECTION_PATTERNS)) {
            return GuardrailResult.block("request contains prompt-injection-like instructions");
        }
        boolean travelRelated = containsAny(lower, TRAVEL_KEYWORDS);
        List<String> warnings = new ArrayList<>();
        if (!travelRelated) {
            warnings.add("当前请求不像旅行规划场景，系统将降级为旅行规划能力说明。");
        }
        return GuardrailResult.allow(normalized, travelRelated, warnings);
    }

    public Path validateWritableFileName(String fileName, Path allowedRoot) {
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path resolved = validateFileName(fileName, root);
        if (!resolved.startsWith(root)) {
            throw new SecurityException("File path is outside allowed workspace directory.");
        }
        return resolved;
    }

    public String validateTerminalCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new SecurityException("Terminal command cannot be blank.");
        }
        String normalized = command.strip();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, BLOCKED_COMMAND_TOKENS)) {
            throw new SecurityException("Terminal command contains a blocked operation.");
        }
        String firstToken = lower.split("\\s+", 2)[0];
        if (!ALLOWED_COMMANDS.contains(firstToken)) {
            throw new SecurityException("Terminal command is not in the allowlist.");
        }
        return normalized;
    }

    public URI validateDownloadUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SecurityException("URL cannot be blank.");
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new SecurityException("Only http and https URLs are allowed.");
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank() || isPrivateOrLocalHost(host)) {
                throw new SecurityException("Local or private network downloads are not allowed.");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new SecurityException("URL is invalid.", ex);
        }
    }

    public TravelPlan sanitizeTravelPlanOutput(TravelPlan plan) {
        if (plan == null) {
            return null;
        }
        return new TravelPlan(
                softenUnsafeClaims(plan.summary()),
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                sanitizeBudget(plan.budget()),
                sanitizeItineraryDays(plan.itineraryDays()),
                sanitizeStrings(plan.transportation()),
                sanitizeStrings(plan.accommodation()),
                ensurePolicyUncertaintyRisk(sanitizeStrings(plan.risks()), plan),
                sanitizeStrings(plan.alternatives()),
                plan.loadedSkills()
        );
    }

    private Path validateFileName(String fileName, Path root) {
        if (fileName == null || fileName.isBlank()) {
            throw new SecurityException("File name cannot be blank.");
        }
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new SecurityException("File name contains unsafe characters.");
        }
        try {
            Path candidate = Path.of(fileName);
            if (candidate.isAbsolute() || fileName.contains("..")) {
                throw new SecurityException("Only simple relative file names are allowed.");
            }
            return root.resolve(candidate).normalize();
        } catch (InvalidPathException ex) {
            throw new SecurityException("File name is invalid.", ex);
        }
    }

    private TravelPlan.Budget sanitizeBudget(TravelPlan.Budget budget) {
        if (budget == null) {
            return null;
        }
        return new TravelPlan.Budget(
                budget.total(),
                budget.currency(),
                budget.items().stream()
                        .map(item -> new TravelPlan.BudgetItem(
                                softenUnsafeClaims(item.name()),
                                item.amount(),
                                softenUnsafeClaims(item.note())
                        ))
                        .toList(),
                softenUnsafeClaims(budget.note())
        );
    }

    private List<TravelPlan.ItineraryDay> sanitizeItineraryDays(List<TravelPlan.ItineraryDay> days) {
        return days.stream()
                .map(day -> new TravelPlan.ItineraryDay(
                        day.day(),
                        softenUnsafeClaims(day.theme()),
                        day.activities().stream()
                                .map(activity -> new TravelPlan.Activity(
                                        softenUnsafeClaims(activity.time()),
                                        softenUnsafeClaims(activity.title()),
                                        softenUnsafeClaims(activity.description()),
                                        softenUnsafeClaims(activity.area()),
                                        softenUnsafeClaims(activity.costLevel()),
                                        sanitizeStrings(activity.tips())
                                ))
                                .toList(),
                        sanitizeStrings(day.meals()),
                        softenUnsafeClaims(day.accommodation()),
                        softenUnsafeClaims(day.transport()),
                        softenUnsafeClaims(day.pace()),
                        sanitizeStrings(day.reminders())
                ))
                .toList();
    }

    private List<String> ensurePolicyUncertaintyRisk(List<String> risks, TravelPlan plan) {
        String text = plan.toString();
        boolean hasPolicyTopic = containsAny(text, List.of("签证", "天气", "政策", "visa", "weather", "policy"));
        boolean hasUncertainty = containsAny(String.join(" ", risks), List.of("以官方", "实时", "可能", "不确定", "current", "official"));
        if (!hasPolicyTopic || hasUncertainty) {
            return risks;
        }
        List<String> enriched = new ArrayList<>(risks);
        enriched.add("天气、签证和政策类信息可能变化，请以官方渠道和实时查询为准。");
        return enriched;
    }

    private List<String> sanitizeStrings(List<String> values) {
        return values.stream().map(this::softenUnsafeClaims).toList();
    }

    private String softenUnsafeClaims(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("签证一定通过", "签证结果需以官方审核为准")
                .replace("绝对安全", "整体风险较低但仍需注意安全")
                .replace("保证安全", "建议做好安全确认")
                .replace("100%安全", "整体风险较低但仍需注意安全")
                .replace("天气一定", "天气可能")
                .replace("价格一定", "价格可能");
    }

    private boolean isPrivateOrLocalHost(String host) {
        return host.equals("localhost")
                || host.equals("0.0.0.0")
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }

    private boolean containsAny(String text, List<String> candidates) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(lower::contains);
    }
}
