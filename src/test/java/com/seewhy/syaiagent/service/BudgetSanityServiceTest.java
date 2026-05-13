package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.TravelPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetSanityServiceTest {

    private final BudgetSanityService service = new BudgetSanityService();

    @Test
    void adjustsOverpricedBeijingShanghaiSecondClassRail() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("交通（上海↔北京高铁）", BigDecimal.valueOf(11000), "5人高铁二等座往返，单程约5500元，合计约11000元")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem rail = result.plan().budget().items().getFirst();
        assertAmount(6500, rail);
        assertTrue(rail.note().contains("650 CNY/人/单程"));
        assertTrue(rail.note().contains("5人往返约6500 CNY"));
        assertTrue(result.adjustedItems().getFirst().contains("11000 -> 6500"));
    }

    @Test
    void adjustsOverpricedShanghaiHangzhouSecondClassRail() {
        TravelPlan plan = planWithItems("上海", "杭州", 3, 5, List.of(
                new TravelPlan.BudgetItem("交通（上海↔杭州高铁）", BigDecimal.valueOf(3000), "高铁二等座往返")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        assertAmount(1000, result.plan().budget().items().getFirst());
        assertTrue(result.plan().budget().items().getFirst().note().contains("100 CNY/人/单程"));
    }

    @Test
    void doesNotAdjustBusinessClassOrPrivateTransferRail() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("交通（上海↔北京高铁商务座）", BigDecimal.valueOf(18000), "高铁商务座，按舒适出行估算")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertFalse(result.adjusted());
        assertAmount(18000, result.plan().budget().items().getFirst());
    }

    @Test
    void adjustsOverpricedDomesticLocalTransport() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("北京当地交通", BigDecimal.valueOf(8000), "地铁、打车等5人3天预估")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem local = result.plan().budget().items().getFirst();
        assertAmount(2700, local);
        assertTrue(local.note().contains("180 CNY/人/天"));
    }

    @Test
    void adjustsOverpricedDomesticFood() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("餐饮", BigDecimal.valueOf(20000), "普通观光餐饮")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem food = result.plan().budget().items().getFirst();
        assertAmount(3750, food);
        assertTrue(food.note().contains("250 CNY/人/天"));
    }

    @Test
    void adjustsOverpricedDomesticTickets() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("景点门票", BigDecimal.valueOf(10000), "故宫、颐和园等普通城市观光")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem ticket = result.plan().budget().items().getFirst();
        assertAmount(2250, ticket);
        assertTrue(ticket.note().contains("150 CNY/人/天"));
    }

    @Test
    void adjustsOverpricedDomesticHotel() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("住宿", BigDecimal.valueOf(30000), "普通舒适酒店")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem hotel = result.plan().budget().items().getFirst();
        assertAmount(5400, hotel);
        assertTrue(hotel.note().contains("3间房 × 2晚 × 900 CNY/间/晚"));
    }

    @Test
    void doesNotAdjustExplicitLuxuryHotel() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("住宿", BigDecimal.valueOf(30000), "五星豪华套房，家庭舒适出行")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertFalse(result.adjusted());
        assertAmount(30000, result.plan().budget().items().getFirst());
    }

    @Test
    void doesNotHardCorrectForeignOrUncertainRoute() {
        TravelPlan plan = planWithItems("上海", "京都", 3, 5, List.of(
                new TravelPlan.BudgetItem("餐饮", BigDecimal.valueOf(20000), "普通观光餐饮"),
                new TravelPlan.BudgetItem("当地交通", BigDecimal.valueOf(8000), "地铁打车")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertFalse(result.adjusted());
        assertAmount(20000, result.plan().budget().items().get(0));
        assertAmount(8000, result.plan().budget().items().get(1));
    }

    @Test
    void movesReductionIntoExistingReserve() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("交通（上海↔北京高铁）", BigDecimal.valueOf(11000), "高铁二等座往返"),
                new TravelPlan.BudgetItem("备用金", BigDecimal.valueOf(1000), "应急")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem reserve = result.plan().budget().items().get(1);
        assertEquals("备用金", reserve.name());
        assertAmount(5500, reserve);
    }

    @Test
    void createsReserveWhenAdjustmentHasNoExistingReserve() {
        TravelPlan plan = planWithItems("上海", "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("交通（上海↔北京高铁）", BigDecimal.valueOf(11000), "高铁二等座往返")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        TravelPlan.BudgetItem reserve = result.plan().budget().items().get(1);
        assertEquals("预留/升级资金", reserve.name());
        assertAmount(4500, reserve);
        assertTrue(reserve.note().contains("未强行花满"));
        assertTrue(result.plan().budget().note().contains("预算为上限型规划"));
    }

    @Test
    void auditsLocalSpendWhenDestinationIsDomesticAndDepartureMissing() {
        TravelPlan plan = planWithItems(null, "北京", 3, 5, List.of(
                new TravelPlan.BudgetItem("餐饮", BigDecimal.valueOf(20000), "普通观光餐饮")
        ));

        BudgetSanityService.AuditResult result = service.audit(plan);

        assertTrue(result.adjusted());
        assertAmount(3750, result.plan().budget().items().getFirst());
    }

    private TravelPlan planWithItems(String departure, String destination, Integer days, Integer travelers, List<TravelPlan.BudgetItem> items) {
        return new TravelPlan(
                "旅行计划",
                destination,
                departure,
                days,
                travelers,
                new TravelPlan.Budget(
                        BigDecimal.valueOf(100000),
                        "CNY",
                        items,
                        "以上为规划估算。"
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("budget-travel")
        );
    }

    private void assertAmount(long expected, TravelPlan.BudgetItem item) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(item.amount()), () -> item.name() + " amount was " + item.amount());
    }
}
