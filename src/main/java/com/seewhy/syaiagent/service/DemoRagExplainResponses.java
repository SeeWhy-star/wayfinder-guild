package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.RagRetrievedDocument;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DemoRagExplainResponses {

    private static final Map<String, DemoDoc> DOCS = Map.of(
            "japan-family-trip", new DemoDoc(
                    "japan-family-trip",
                    "日本家庭旅行轻松节奏规划",
                    "classpath:document/japan-family-trip.md",
                    "家庭日本行优先少换酒店、少跨城、住在交通节点附近；每天设置一个主目标和一个可取消的轻量活动，预算需要保留弹性。",
                    List.of("日本", "家庭旅行", "轻松", "住宿")
            ),
            "budget-travel-planning", new DemoDoc(
                    "budget-travel-planning",
                    "低预算旅行规划与成本控制",
                    "classpath:document/budget-travel-planning.md",
                    "预算先定上限，再拆成必须花、想体验、可取消和应急金；住宿、交通和餐饮都要按体验价值和变更能力一起判断。",
                    List.of("预算", "住宿", "交通", "餐饮")
            ),
            "family-travel-risk-checklist", new DemoDoc(
                    "family-travel-risk-checklist",
                    "家庭旅行风险核对清单",
                    "classpath:document/family-travel-risk-checklist.md",
                    "家庭旅行风险可以拆成健康、证件、交通、住宿、沟通和财务；老人小孩要额外关注药品、保险、走散和临时就医预案。",
                    List.of("家庭旅行", "老人", "小孩", "风险")
            ),
            "japan-transport-pass", new DemoDoc(
                    "japan-transport-pass",
                    "日本交通券与 JR Pass 选择思路",
                    "classpath:document/japan-transport-pass.md",
                    "交通券选择先看路线，而不是先看券名；长距离新干线次数、使用天数和线路覆盖决定 JR Pass 或区域 Pass 是否值得。",
                    List.of("日本", "JR Pass", "交通券", "IC卡")
            ),
            "rainy-day-backup-plan", new DemoDoc(
                    "rainy-day-backup-plan",
                    "雨天旅行备选方案设计",
                    "classpath:document/rainy-day-backup-plan.md",
                    "雨天备选应提前准备室内文化场馆、商业综合体或地下街、轻松餐饮休息点，并减少换乘和长距离步行。",
                    List.of("雨天", "备选方案", "室内活动", "天气")
            ),
            "travel-safety-and-insurance", new DemoDoc(
                    "travel-safety-and-insurance",
                    "旅行安全与保险提醒",
                    "classpath:document/travel-safety-and-insurance.md",
                    "保险不等于没有风险；需要提前确认保障范围、除外责任、理赔材料、医疗网络和紧急联系人。",
                    List.of("安全", "保险", "医疗", "应急")
            )
    );

    private DemoRagExplainResponses() {
    }

    static RagExplainResponse build(String originalQuery, String chatId) {
        String query = originalQuery == null || originalQuery.isBlank()
                ? "What should I consider for a relaxed family trip to Japan?"
                : originalQuery.strip();
        String id = chatId == null || chatId.isBlank() ? "demo-rag-local" : chatId;
        boolean chinese = containsChinese(query);
        DemoIntent intent = detectIntent(query);
        return new RagExplainResponse(
                id,
                "demo",
                query,
                rewriteFor(intent, chinese),
                documentsFor(intent),
                answerFor(intent, chinese),
                false,
                null
        );
    }

    private static DemoIntent detectIntent(String query) {
        String normalized = normalize(query);
        if (containsAny(normalized, "jrpass", "交通券", "通票", "ic卡", "railpass", "pass")) {
            return DemoIntent.TRANSPORT_PASS;
        }
        if (containsAny(normalized, "下雨", "雨天", "rain", "weather", "备选", "indoor")) {
            return DemoIntent.RAINY_DAY;
        }
        if (containsAny(normalized, "老人", "小孩", "孩子", "风险", "保险", "药品", "走散", "elderly", "child", "children", "risk", "insurance")) {
            return DemoIntent.FAMILY_RISK;
        }
        if (containsAny(normalized, "低预算", "控制", "餐饮", "成本", "budget", "cost", "food")) {
            return DemoIntent.BUDGET;
        }
        if (containsAny(normalized, "日本", "japan", "父母", "parents", "轻松", "relaxed")) {
            return DemoIntent.JAPAN_FAMILY;
        }
        return DemoIntent.GENERAL;
    }

    private static List<RagRetrievedDocument> documentsFor(DemoIntent intent) {
        return switch (intent) {
            case JAPAN_FAMILY -> toRetrieved(List.of(
                    scored("japan-family-trip", 0.94),
                    scored("budget-travel-planning", 0.88),
                    scored("family-travel-risk-checklist", 0.84)
            ));
            case TRANSPORT_PASS -> toRetrieved(List.of(
                    scored("japan-transport-pass", 0.95),
                    scored("budget-travel-planning", 0.82),
                    scored("japan-family-trip", 0.76)
            ));
            case RAINY_DAY -> toRetrieved(List.of(
                    scored("rainy-day-backup-plan", 0.95),
                    scored("japan-family-trip", 0.82),
                    scored("family-travel-risk-checklist", 0.77)
            ));
            case BUDGET -> toRetrieved(List.of(
                    scored("budget-travel-planning", 0.95),
                    scored("japan-transport-pass", 0.80),
                    scored("japan-family-trip", 0.76)
            ));
            case FAMILY_RISK -> toRetrieved(List.of(
                    scored("family-travel-risk-checklist", 0.96),
                    scored("travel-safety-and-insurance", 0.84),
                    scored("rainy-day-backup-plan", 0.74)
            ));
            case GENERAL -> toRetrieved(List.of(
                    scored("japan-family-trip", 0.82),
                    scored("budget-travel-planning", 0.78),
                    scored("family-travel-risk-checklist", 0.74)
            ));
        };
    }

    private static String rewriteFor(DemoIntent intent, boolean chinese) {
        if (!chinese) {
            return switch (intent) {
                case JAPAN_FAMILY -> "Japan family relaxed trip budget lodging transport risks";
                case TRANSPORT_PASS -> "Japan JR Pass regional pass IC card route coverage value";
                case RAINY_DAY -> "Japan rainy day backup indoor activities transport adjustment";
                case BUDGET -> "low budget travel lodging transport food cost contingency";
                case FAMILY_RISK -> "elderly children family travel health documents insurance emergency";
                case GENERAL -> "travel knowledge base itinerary budget transport lodging risk reminders";
            };
        }
        return switch (intent) {
            case JAPAN_FAMILY -> "日本 家庭 轻松旅行 预算 住宿 交通 风险";
            case TRANSPORT_PASS -> "日本 交通券 JR Pass 区域 Pass IC卡 路线覆盖 是否划算";
            case RAINY_DAY -> "日本 下雨天 备选方案 室内活动 交通调整 家庭旅行";
            case BUDGET -> "低预算 旅行 住宿 交通 餐饮 成本拆分 应急金";
            case FAMILY_RISK -> "老人 小孩 家庭旅行 风险 健康 证件 保险 应急";
            case GENERAL -> "旅行知识库 行程 预算 交通 住宿 风险提醒";
        };
    }

    private static String answerFor(DemoIntent intent, boolean chinese) {
        if (!chinese) {
            return switch (intent) {
                case JAPAN_FAMILY -> "Keep the trip to one or two cities, reduce hotel changes and long transfers, and stay near transit. Split the budget into transport, lodging, local meals, tickets, and contingency. Demo Mode uses fixed local Markdown snippets, so verify live prices, visa rules, weather, schedules, and opening hours before booking.";
                case TRANSPORT_PASS -> "Choose transport passes from the route, not from the pass name. JR Pass value depends on long-distance rail frequency, travel days, and covered lines; for mostly city travel, IC cards or a small regional pass may be more flexible.";
                case RAINY_DAY -> "Prepare rainy-day backups before the trip: indoor culture venues, malls or underground streets, and easy rest stops. Reduce transfers and walking when it rains, keep one main activity, and verify live weather and venue hours.";
                case BUDGET -> "Set a budget ceiling first, then split it into must-pay items, optional experiences, cancellable items, and contingency. Include transport time, cancellation policy, room fit, and safety when comparing lodging.";
                case FAMILY_RISK -> "For elderly travelers and children, check health, documents, transport, lodging, communication, and money. Prepare medicines, insurance details, document copies, emergency contacts, and a lost-contact plan.";
                case GENERAL -> "This demo searches fixed local Markdown snippets and returns a grounded summary for the closest travel-planning topic. Verify live policies, prices, weather, and opening hours before booking.";
            };
        }
        return switch (intent) {
            case JAPAN_FAMILY -> "建议把行程控制在 1-2 个城市，减少换酒店和长距离移动；住宿尽量靠近交通节点，给家人预留午休或早回酒店的窗口。预算按交通、住宿、餐饮、门票和应急金拆分。Demo Mode 使用固定本地 Markdown 片段展示 RAG 解释链；实际价格、签证规则、天气、交通时刻和景点开放时间请在预订前实时核验。";
            case TRANSPORT_PASS -> "交通券先看路线，不是先看券名。JR Pass 是否划算主要取决于长距离铁路次数、使用天数和覆盖线路；如果主要是市内移动，IC 卡或少量区域 Pass 往往更灵活。";
            case RAINY_DAY -> "雨天备选要提前做：每个城市准备室内文化场馆、商业综合体或地下街、轻松餐饮休息点。下雨时减少换乘和步行，保留一个主活动即可，并实时核验天气、交通和场馆开放时间。";
            case BUDGET -> "先定预算上限，再拆成必须花、想体验、可取消和应急金。住宿不要只看低价，还要看交通时间、取消政策、房间条件和同行人是否合适；餐饮可以用便利店、本地小店、市场轻食和少量特色餐组合控制成本。";
            case FAMILY_RISK -> "带老人小孩旅行，建议按健康、证件、交通、住宿、沟通、财务和应急来核对。出发前准备常用药、处方、保险信息、证件复印件和紧急联系人，并约定走散集合点、手机没电方案和不舒服时的就医预案。";
            case GENERAL -> "Demo Mode 会在固定本地 Markdown 片段里做确定性检索，并把最相关的文档、改写查询和简短回答展示出来。请把下面的来源当作可解释链路；实时政策、价格、天气和开放时间仍需预订前核验。";
        };
    }

    private static List<RagRetrievedDocument> toRetrieved(List<ScoredDoc> scoredDocs) {
        return scoredDocs.stream()
                .map(scored -> {
                    DemoDoc doc = DOCS.get(scored.documentId());
                    return new RagRetrievedDocument(
                            doc.title(),
                            doc.source(),
                            doc.snippet(),
                            scored.score(),
                            doc.id(),
                            doc.tags(),
                            "2026-05-10",
                            "curated-demo-markdown"
                    );
                })
                .toList();
    }

    private static ScoredDoc scored(String documentId, double score) {
        return new ScoredDoc(documentId, score);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip().replace(" ", "");
    }

    private static boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private enum DemoIntent {
        JAPAN_FAMILY,
        TRANSPORT_PASS,
        RAINY_DAY,
        BUDGET,
        FAMILY_RISK,
        GENERAL
    }

    private record DemoDoc(String id, String title, String source, String snippet, List<String> tags) {
    }

    private record ScoredDoc(String documentId, double score) {
    }
}
