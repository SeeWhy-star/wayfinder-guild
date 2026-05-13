package com.seewhy.syaiagent.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelDraftStateServiceTest {

    @Test
    void extractsCompleteCompactChineseTravelRequest() {
        TravelDraftStateService service = new TravelDraftStateService();

        TravelDraftStateService.DraftState state = service.updateFromTurn(
                "chat-a",
                "北京去上海五天五万元五个人想去观光",
                "已记录"
        );

        assertEquals("北京", state.departure());
        assertEquals("上海", state.destination());
        assertEquals(5, state.days());
        assertEquals(5, state.travelers());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(state.budgetAmount()));
        assertEquals("观光", state.theme());
        assertTrue(state.draftLine().contains("PLAN_DRAFT: 北京出发，去上海，5天，5人，预算50000 CNY，主题观光。"));
    }

    @Test
    void routeOnlyIsInsufficientForPlanDraft() {
        TravelDraftStateService service = new TravelDraftStateService();

        TravelDraftStateService.DraftState state = service.updateFromTurn("chat-b", "上海去北京", "已记录");

        assertEquals("上海", state.departure());
        assertEquals("北京", state.destination());
        assertEquals("", state.draftLine());
    }

    @Test
    void mergesFollowUpFieldsIntoExistingRoute() {
        TravelDraftStateService service = new TravelDraftStateService();

        service.updateFromTurn("chat-c", "上海去北京", "已记录");
        TravelDraftStateService.DraftState state = service.updateFromTurn("chat-c", "五天六个人一万元", "已记录");

        assertTrue(state.draftLine().contains("上海出发，去北京，5天，6人，预算10000 CNY"));
    }

    @Test
    void keepsSingleCityAmbiguousWithoutRouteWords() {
        TravelDraftStateService service = new TravelDraftStateService();

        TravelDraftStateService.DraftState state = service.updateFromTurn("chat-d", "北京五天十万元", "待确认北京是出发地还是目的地");

        assertEquals("北京", state.ambiguousCity());
        assertNull(state.departure());
        assertNull(state.destination());
        assertEquals(5, state.days());
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(state.budgetAmount()));
        assertEquals("", state.draftLine());
    }

    @Test
    void confirmsDepartureAfterAmbiguousCityWhenUserSaysDeparture() {
        TravelDraftStateService service = new TravelDraftStateService();

        service.updateFromTurn("chat-e", "北京五天十万元", "待确认北京是出发地还是目的地");
        TravelDraftStateService.DraftState state = service.updateFromTurn("chat-e", "北京出发", "已确认北京出发");

        assertEquals("北京", state.departure());
        assertNull(state.destination());
        assertNull(state.ambiguousCity());
        assertEquals("", state.draftLine());
    }

    @Test
    void updatesDateOnly() {
        TravelDraftStateService service = new TravelDraftStateService();

        TravelDraftStateService.DraftState state = service.updateFromTurn("chat-f", "后天", "已记录后天出发");

        assertEquals("后天出发", state.dateText());
        assertEquals("", state.draftLine());
    }

    @Test
    void postProcessAppendsDraftWhenAiMissesPlanDraft() {
        TravelDraftStateService service = new TravelDraftStateService();

        String content = service.postProcessResponse(
                "chat-g",
                "北京去上海五天五万元五个人想去观光",
                "已记录：北京出发、目的地上海、5天、5人、预算50000 CNY、主题观光。"
        );

        assertTrue(content.contains("生成结构化计划"), content);
        assertTrue(content.endsWith("PLAN_DRAFT: 北京出发，去上海，5天，5人，预算50000 CNY，主题观光。"));
    }

    @Test
    void postProcessReplacesOldOrIncompleteModelDraft() {
        TravelDraftStateService service = new TravelDraftStateService();

        String content = service.postProcessResponse(
                "chat-h",
                "北京去上海五天五万元五个人想去观光",
                "信息足够。\nPLAN_DRAFT: 去北京，3天，2人，预算1000 CNY。"
        );

        assertTrue(content.endsWith("PLAN_DRAFT: 北京出发，去上海，5天，5人，预算50000 CNY，主题观光。"));
        assertEquals(1, countOccurrences(content, "PLAN_DRAFT:"));
    }

    @Test
    void travelChatServicePostProcessDelegatesToDraftService() {
        TravelDraftStateService draftStateService = new TravelDraftStateService();
        TravelChatService chatService = new TravelChatService(null, draftStateService);

        String content = chatService.postProcessResponse(
                "chat-i",
                "上海去北京五天六个人一万元",
                "已记录：上海出发、目的地北京、5天、6人、预算10000 CNY。"
        );

        assertTrue(content.endsWith("PLAN_DRAFT: 上海出发，去北京，5天，6人，预算10000 CNY。"));
    }

    @Test
    void streamCompletionSuffixAppendsOnlyTrustedDraftAndHint() {
        TravelDraftStateService service = new TravelDraftStateService();

        String suffix = service.streamCompletionSuffix(
                "chat-j",
                "北京去上海五天五万元五个人想去观光",
                "已记录：北京出发、目的地上海、5天、5人、预算50000 CNY、主题观光。\nPLAN_DRAFT: 去北京，3天，2人，预算1000 CNY。"
        );

        assertTrue(suffix.contains("生成结构化计划"), suffix);
        assertTrue(suffix.endsWith("PLAN_DRAFT: 北京出发，去上海，5天，5人，预算50000 CNY，主题观光。"), suffix);
        assertEquals(1, countOccurrences(suffix, "PLAN_DRAFT:"));
    }

    @Test
    void travelChatServiceRecognizesPlanDraftLinesForStreamingFilter() {
        TravelChatService chatService = new TravelChatService(null, new TravelDraftStateService());

        assertTrue(chatService.isPlanDraftLine("PLAN_DRAFT: 北京出发，去上海。"));
        assertTrue(chatService.isPlanDraftLine("   plan_draft: 北京出发，去上海。"));
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
