package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.TravelPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BudgetSanityService {

    private static final Set<String> DOMESTIC_CITIES = Set.of(
            "北京", "上海", "杭州", "苏州", "天津", "成都", "重庆", "广州", "深圳", "南京",
            "西安", "武汉", "长沙", "厦门", "青岛", "三亚", "昆明", "大理", "丽江"
    );
    private static final Map<String, BigDecimal> RAIL_ONE_WAY_ESTIMATES = Map.of(
            routeKey("北京", "上海"), BigDecimal.valueOf(650),
            routeKey("上海", "杭州"), BigDecimal.valueOf(100),
            routeKey("上海", "苏州"), BigDecimal.valueOf(50),
            routeKey("北京", "天津"), BigDecimal.valueOf(60),
            routeKey("成都", "重庆"), BigDecimal.valueOf(150),
            routeKey("上海", "南京"), BigDecimal.valueOf(150),
            routeKey("广州", "深圳"), BigDecimal.valueOf(100),
            routeKey("西安", "北京"), BigDecimal.valueOf(550),
            routeKey("武汉", "长沙"), BigDecimal.valueOf(180)
    );
    private static final BigDecimal RAIL_OVERAGE_MULTIPLIER = BigDecimal.valueOf(1.6);
    private static final BigDecimal LOCAL_TRANSPORT_PER_PERSON_DAY = BigDecimal.valueOf(180);
    private static final BigDecimal LOCAL_TRANSPORT_OVERAGE_MULTIPLIER = BigDecimal.valueOf(1.8);
    private static final BigDecimal FOOD_PER_PERSON_DAY = BigDecimal.valueOf(250);
    private static final BigDecimal FOOD_OVERAGE_MULTIPLIER = BigDecimal.valueOf(2.0);
    private static final BigDecimal TICKET_PER_PERSON_DAY = BigDecimal.valueOf(150);
    private static final BigDecimal TICKET_OVERAGE_MULTIPLIER = BigDecimal.valueOf(2.0);
    private static final BigDecimal HOTEL_ROOM_NIGHT = BigDecimal.valueOf(900);
    private static final BigDecimal HOTEL_OVERAGE_MULTIPLIER = BigDecimal.valueOf(2.5);
    private static final String RESERVE_NAME = "预留/升级资金";
    private static final String RESERVE_NOTE = "用户预算较充足，未强行花满；可用于升级住宿、商务座、高档餐饮、购物或应急。";
    private static final String BUDGET_NOTE_SUFFIX = "预算为上限型规划，不代表必须花完；未使用部分归入预留/升级资金。";

    public AuditResult audit(TravelPlan plan) {
        if (plan == null || plan.budget() == null || plan.budget().items().isEmpty()) {
            return new AuditResult(plan, false, List.of());
        }

        String departureCity = domesticCity(plan.departure());
        String destinationCity = domesticCity(plan.destination());
        boolean domesticRoute = departureCity != null && destinationCity != null;
        boolean domesticLocalSpend = destinationCity != null && (isBlank(plan.departure()) || departureCity != null);
        if (!domesticRoute && !domesticLocalSpend) {
            return new AuditResult(plan, false, List.of());
        }

        List<TravelPlan.BudgetItem> auditedItems = new ArrayList<>();
        List<String> adjustedItems = new ArrayList<>();
        BigDecimal reserveDelta = BigDecimal.ZERO;

        for (TravelPlan.BudgetItem item : plan.budget().items()) {
            TravelPlan.BudgetItem audited = item;
            if (domesticRoute) {
                AuditItemResult railResult = auditRail(item, departureCity, destinationCity, plan.travelers());
                audited = railResult.item();
                reserveDelta = reserveDelta.add(railResult.delta());
                addAdjustment(adjustedItems, item, audited);
            }
            if (audited == item && domesticLocalSpend) {
                AuditItemResult localResult = auditLocalTransport(item, plan.travelers(), plan.days());
                audited = localResult.item();
                reserveDelta = reserveDelta.add(localResult.delta());
                addAdjustment(adjustedItems, item, audited);
            }
            if (audited == item && domesticLocalSpend) {
                AuditItemResult foodResult = auditFood(item, plan.travelers(), plan.days());
                audited = foodResult.item();
                reserveDelta = reserveDelta.add(foodResult.delta());
                addAdjustment(adjustedItems, item, audited);
            }
            if (audited == item && domesticLocalSpend) {
                AuditItemResult ticketResult = auditTickets(item, plan.travelers(), plan.days());
                audited = ticketResult.item();
                reserveDelta = reserveDelta.add(ticketResult.delta());
                addAdjustment(adjustedItems, item, audited);
            }
            if (audited == item && domesticLocalSpend) {
                AuditItemResult hotelResult = auditHotel(item, plan.travelers(), plan.days());
                audited = hotelResult.item();
                reserveDelta = reserveDelta.add(hotelResult.delta());
                addAdjustment(adjustedItems, item, audited);
            }
            auditedItems.add(audited);
        }

        if (adjustedItems.isEmpty()) {
            return new AuditResult(plan, false, List.of());
        }
        if (reserveDelta.compareTo(BigDecimal.ZERO) > 0) {
            auditedItems = moveDeltaToReserve(auditedItems, reserveDelta);
        }

        TravelPlan.Budget auditedBudget = new TravelPlan.Budget(
                plan.budget().total(),
                plan.budget().currency(),
                auditedItems,
                appendBudgetNote(plan.budget().note())
        );
        TravelPlan auditedPlan = new TravelPlan(
                plan.summary(),
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                auditedBudget,
                plan.itineraryDays(),
                plan.transportation(),
                plan.accommodation(),
                plan.risks(),
                plan.alternatives(),
                plan.loadedSkills()
        );
        return new AuditResult(auditedPlan, true, adjustedItems);
    }

    private AuditItemResult auditRail(TravelPlan.BudgetItem item, String departureCity, String destinationCity, Integer travelers) {
        if (travelers == null || travelers <= 0 || !shouldAuditOrdinaryRail(item)) {
            return AuditItemResult.unchanged(item);
        }
        BigDecimal oneWay = RAIL_ONE_WAY_ESTIMATES.get(routeKey(departureCity, destinationCity));
        if (oneWay == null) {
            return AuditItemResult.unchanged(item);
        }
        BigDecimal expected = oneWay.multiply(BigDecimal.valueOf(travelers)).multiply(BigDecimal.valueOf(2));
        BigDecimal amount = safeAmount(item);
        if (amount.compareTo(expected.multiply(RAIL_OVERAGE_MULTIPLIER)) <= 0) {
            return AuditItemResult.unchanged(item);
        }
        TravelPlan.BudgetItem audited = new TravelPlan.BudgetItem(
                item.name(),
                expected,
                "按高铁二等座规划估算：约%s CNY/人/单程，%d人往返约%s CNY；实时票价以12306为准。"
                        .formatted(format(oneWay), travelers, format(expected))
        );
        return new AuditItemResult(audited, amount.subtract(expected));
    }

    private AuditItemResult auditLocalTransport(TravelPlan.BudgetItem item, Integer travelers, Integer days) {
        if (travelers == null || travelers <= 0 || days == null || days <= 0 || !shouldAuditLocalTransport(item)) {
            return AuditItemResult.unchanged(item);
        }
        BigDecimal expected = max(BigDecimal.valueOf(500), BigDecimal.valueOf(travelers).multiply(BigDecimal.valueOf(days)).multiply(LOCAL_TRANSPORT_PER_PERSON_DAY));
        BigDecimal amount = safeAmount(item);
        if (amount.compareTo(expected.multiply(LOCAL_TRANSPORT_OVERAGE_MULTIPLIER)) <= 0) {
            return AuditItemResult.unchanged(item);
        }
        TravelPlan.BudgetItem audited = new TravelPlan.BudgetItem(
                item.name(),
                expected,
                "按地铁+打车混合规划估算：约180 CNY/人/天，%d人%d天约%s CNY；如需全天包车或专车应单独说明。"
                        .formatted(travelers, days, format(expected))
        );
        return new AuditItemResult(audited, amount.subtract(expected));
    }

    private AuditItemResult auditFood(TravelPlan.BudgetItem item, Integer travelers, Integer days) {
        if (travelers == null || travelers <= 0 || days == null || days <= 0 || !shouldAuditFood(item)) {
            return AuditItemResult.unchanged(item);
        }
        BigDecimal expected = BigDecimal.valueOf(travelers).multiply(BigDecimal.valueOf(days)).multiply(FOOD_PER_PERSON_DAY);
        BigDecimal amount = safeAmount(item);
        if (amount.compareTo(expected.multiply(FOOD_OVERAGE_MULTIPLIER)) <= 0) {
            return AuditItemResult.unchanged(item);
        }
        TravelPlan.BudgetItem audited = new TravelPlan.BudgetItem(
                item.name(),
                expected,
                "按舒适餐饮规划估算：约250 CNY/人/天，%d人%d天约%s CNY；高端餐饮可从预留/升级资金中支出。"
                        .formatted(travelers, days, format(expected))
        );
        return new AuditItemResult(audited, amount.subtract(expected));
    }

    private AuditItemResult auditTickets(TravelPlan.BudgetItem item, Integer travelers, Integer days) {
        if (travelers == null || travelers <= 0 || days == null || days <= 0 || !shouldAuditTickets(item)) {
            return AuditItemResult.unchanged(item);
        }
        BigDecimal expected = BigDecimal.valueOf(travelers).multiply(BigDecimal.valueOf(days)).multiply(TICKET_PER_PERSON_DAY);
        BigDecimal amount = safeAmount(item);
        if (amount.compareTo(expected.multiply(TICKET_OVERAGE_MULTIPLIER)) <= 0) {
            return AuditItemResult.unchanged(item);
        }
        TravelPlan.BudgetItem audited = new TravelPlan.BudgetItem(
                item.name(),
                expected,
                "按普通城市观光门票规划估算：约150 CNY/人/天，%d人%d天约%s CNY；高价演出或主题乐园需单独列项。"
                        .formatted(travelers, days, format(expected))
        );
        return new AuditItemResult(audited, amount.subtract(expected));
    }

    private AuditItemResult auditHotel(TravelPlan.BudgetItem item, Integer travelers, Integer days) {
        if (travelers == null || travelers <= 0 || days == null || days <= 0 || !shouldAuditHotel(item)) {
            return AuditItemResult.unchanged(item);
        }
        int nights = Math.max(days - 1, 1);
        int rooms = (int) Math.ceil(travelers / 2.0);
        BigDecimal expected = BigDecimal.valueOf(rooms).multiply(BigDecimal.valueOf(nights)).multiply(HOTEL_ROOM_NIGHT);
        BigDecimal amount = safeAmount(item);
        if (amount.compareTo(expected.multiply(HOTEL_OVERAGE_MULTIPLIER)) <= 0) {
            return AuditItemResult.unchanged(item);
        }
        TravelPlan.BudgetItem audited = new TravelPlan.BudgetItem(
                item.name(),
                expected,
                "按舒适酒店规划估算：约%d间房 × %d晚 × 900 CNY/间/晚，合计约%s CNY；高端酒店可从预留/升级资金中支出。"
                        .formatted(rooms, nights, format(expected))
        );
        return new AuditItemResult(audited, amount.subtract(expected));
    }

    private List<TravelPlan.BudgetItem> moveDeltaToReserve(List<TravelPlan.BudgetItem> items, BigDecimal reserveDelta) {
        List<TravelPlan.BudgetItem> result = new ArrayList<>();
        boolean reserveFound = false;
        for (TravelPlan.BudgetItem item : items) {
            if (!reserveFound && isReserveItem(item)) {
                result.add(new TravelPlan.BudgetItem(
                        item.name(),
                        safeAmount(item).add(reserveDelta),
                        item.note() == null || item.note().isBlank() ? RESERVE_NOTE : item.note()
                ));
                reserveFound = true;
            } else {
                result.add(item);
            }
        }
        if (!reserveFound) {
            result.add(new TravelPlan.BudgetItem(RESERVE_NAME, reserveDelta, RESERVE_NOTE));
        }
        return result;
    }

    private void addAdjustment(List<String> adjustedItems, TravelPlan.BudgetItem original, TravelPlan.BudgetItem audited) {
        if (audited != original) {
            adjustedItems.add("%s: %s -> %s".formatted(
                    original.name(),
                    format(safeAmount(original)),
                    format(safeAmount(audited))
            ));
        }
    }

    private boolean shouldAuditOrdinaryRail(TravelPlan.BudgetItem item) {
        String text = itemText(item);
        boolean rail = containsAny(text, "高铁", "动车", "城际", "铁路", "train", "rail");
        boolean roundTrip = containsAny(text, "往返", "双程", "来回", "↔");
        boolean oneWay = containsAny(text, "单程", "去程");
        boolean premium = containsAny(text, "商务座", "一等座", "包车", "专车", "private transfer", "business class");
        return rail && roundTrip && (!oneWay || text.contains("往返")) && !premium;
    }

    private boolean shouldAuditLocalTransport(TravelPlan.BudgetItem item) {
        String text = itemText(item);
        boolean local = containsAny(text, "市内交通", "当地交通", "本地交通", "地铁", "打车", "出租车", "网约车");
        boolean premium = containsAny(text, "包车", "专车", "private transfer");
        return local && !premium;
    }

    private boolean shouldAuditFood(TravelPlan.BudgetItem item) {
        String text = itemText(item);
        boolean food = containsAny(text, "餐饮", "吃饭", "美食", "meal", "food");
        boolean premium = containsAny(text, "高端", "米其林", "商务宴请", "高档餐饮");
        return food && !premium;
    }

    private boolean shouldAuditTickets(TravelPlan.BudgetItem item) {
        String text = itemText(item);
        boolean tickets = containsAny(text, "门票", "景点", "活动", "ticket", "activity");
        boolean premium = containsAny(text, "迪士尼", "环球影城", "主题乐园", "演出", "滑雪");
        return tickets && !premium;
    }

    private boolean shouldAuditHotel(TravelPlan.BudgetItem item) {
        String text = itemText(item);
        boolean hotel = containsAny(text, "住宿", "酒店", "accommodation", "hotel");
        boolean premium = containsAny(text, "高端", "五星", "豪华", "套房", "亲子套房", "商务酒店");
        return hotel && !premium;
    }

    private boolean isReserveItem(TravelPlan.BudgetItem item) {
        String text = normalizeText(item.name());
        return containsAny(text, "预留", "备用金", "升级资金", "reserve", "contingency");
    }

    private String appendBudgetNote(String note) {
        String current = note == null ? "" : note.strip();
        if (current.contains(BUDGET_NOTE_SUFFIX)) {
            return current;
        }
        return current.isBlank() ? BUDGET_NOTE_SUFFIX : current + " " + BUDGET_NOTE_SUFFIX;
    }

    private String domesticCity(String value) {
        String text = normalizeText(value);
        for (String city : DOMESTIC_CITIES) {
            if (text.contains(city)) {
                return city;
            }
        }
        return null;
    }

    private BigDecimal safeAmount(TravelPlan.BudgetItem item) {
        return item.amount() == null ? BigDecimal.ZERO : item.amount();
    }

    private String itemText(TravelPlan.BudgetItem item) {
        return normalizeText((item.name() == null ? "" : item.name()) + " " + (item.note() == null ? "" : item.note()));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String routeKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private record AuditItemResult(TravelPlan.BudgetItem item, BigDecimal delta) {
        static AuditItemResult unchanged(TravelPlan.BudgetItem item) {
            return new AuditItemResult(item, BigDecimal.ZERO);
        }
    }

    public record AuditResult(
            TravelPlan plan,
            boolean adjusted,
            List<String> adjustedItems
    ) {
        public AuditResult {
            adjustedItems = adjustedItems == null ? List.of() : List.copyOf(adjustedItems);
        }
    }
}
