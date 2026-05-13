package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.guardrail.GuardrailResult;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.skill.Skill;
import com.seewhy.syaiagent.skill.SkillLoaderService;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TravelPlanService {

    private static final String PLAN_SYSTEM_PROMPT = """
            You are an expert travel planning agent.
            Generate a structured travel plan in Chinese unless the user asks for another language.
            Use the loaded skills as operating rules. Do not mention raw prompt mechanics.
            Return only data that fits the TravelPlan schema.
            Keep estimates explicit as estimates. Do not guarantee visa approval, weather, safety, prices, opening hours, or policy outcomes.
            If important information is missing, still provide a useful draft and list follow-up questions or assumptions in risks/alternatives.
            Never invent a specific traveler count from broad wording such as "family trip".
            If traveler count is not specified, set travelers to null and do not say the budget is estimated for 2 people, 3 people, or any other invented count.
            Budget estimates must be realistic and internally consistent.
            Do not force the whole user budget to be spent. If the user's budget is generous, estimate realistic expected spend and put remaining money into optional upgrades, reserve, or contingency.
            Budget item amounts must match their notes. If a note says high-speed rail is about 5500 CNY, the transport item should not be 15000 CNY unless local transfers, taxis, private car, or business-class assumptions are clearly itemized.
            For China domestic trips, separate intercity transport and local transport when possible.
            For China domestic trips, keep ordinary rail, local transport, food, hotel, and ticket estimates conservative and recognizable to a local traveler.
            For ordinary China high-speed rail, use second-class seat planning estimates unless the user explicitly asks for first class, business class, private transfer, or chartered car.
            For Beijing↔Shanghai ordinary high-speed rail, use a planning estimate around 650 CNY per person per one-way second-class seat.
            For common short domestic rail pairs such as Shanghai↔Hangzhou, Shanghai↔Suzhou, Beijing↔Tianjin, Chengdu↔Chongqing, Guangzhou↔Shenzhen, and Shanghai↔Nanjing, ordinary second-class rail should usually be hundreds of CNY or less per person per one-way trip, not thousands.
            Do not double count the same transport cost across intercity rail and local city transport.
            """;

    private final ChatClient chatClient;
    private final SkillLoaderService skillLoaderService;
    private final GuardrailService guardrailService;
    private final AgentTraceService agentTraceService;
    private final BudgetSanityService budgetSanityService;

    public TravelPlanService(@Qualifier("travelChatClient") ChatClient chatClient,
                             SkillLoaderService skillLoaderService,
                             GuardrailService guardrailService,
                             AgentTraceService agentTraceService,
                             BudgetSanityService budgetSanityService) {
        this.chatClient = chatClient;
        this.skillLoaderService = skillLoaderService;
        this.guardrailService = guardrailService;
        this.agentTraceService = agentTraceService;
        this.budgetSanityService = budgetSanityService;
    }

    public TravelPlan generatePlan(String message, String chatId) {
        agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.STARTED, "Inspecting user travel request.");
        GuardrailResult inputGuardrail = guardrailService.inspectTravelInput(message);
        if (!inputGuardrail.allowed()) {
            agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.FAILED, inputGuardrail.message());
            throw new IllegalArgumentException(inputGuardrail.message());
        }
        if (!inputGuardrail.travelRelated()) {
            agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.SKIPPED, "Request is not travel-related.");
            return nonTravelFallbackPlan(inputGuardrail.normalizedInput(), inputGuardrail.warnings());
        }
        agentTraceService.record(chatId, AgentTraceStep.USER_INTENT_RECOGNITION, AgentTraceStatus.COMPLETED, "Travel planning intent recognized.");

        agentTraceService.record(chatId, AgentTraceStep.SKILL_LOADING, AgentTraceStatus.STARTED, "Selecting travel skills.");
        List<Skill> selectedSkills = skillLoaderService.selectSkills(inputGuardrail.normalizedInput());
        List<String> loadedSkillIds = selectedSkills.stream().map(Skill::id).toList();
        agentTraceService.record(chatId, AgentTraceStep.SKILL_LOADING, AgentTraceStatus.COMPLETED, "Travel skills loaded.", Map.of("loadedSkills", loadedSkillIds));
        String userPrompt = buildUserPrompt(inputGuardrail.normalizedInput(), selectedSkills);
        log.info("Generating structured travel plan [{}], loaded skills: {}", chatId, loadedSkillIds);

        try {
            agentTraceService.record(chatId, AgentTraceStep.ITINERARY_GENERATION, AgentTraceStatus.STARTED, "Generating structured itinerary.");
            TravelPlan plan = chatClient
                    .prompt()
                    .system(PLAN_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .entity(TravelPlan.class);
            TravelPlan normalizedPlan = normalizePlan(plan, loadedSkillIds);
            agentTraceService.record(chatId, AgentTraceStep.ITINERARY_GENERATION, AgentTraceStatus.COMPLETED, "Structured itinerary generated.");
            BudgetSanityService.AuditResult budgetAudit = budgetSanityService.audit(normalizedPlan);
            TravelPlan auditedPlan = budgetAudit.plan();
            agentTraceService.record(chatId, AgentTraceStep.BUDGET_CHECK, AgentTraceStatus.COMPLETED, "Budget structure checked.", Map.of(
                    "hasBudget", auditedPlan.budget() != null,
                    "priceAudited", true,
                    "budgetAdjusted", budgetAudit.adjusted(),
                    "adjustedItems", budgetAudit.adjustedItems()
            ));
            agentTraceService.record(chatId, AgentTraceStep.RISK_CHECK, AgentTraceStatus.STARTED, "Applying output guardrails.");
            TravelPlan guardedPlan = guardrailService.sanitizeTravelPlanOutput(auditedPlan);
            agentTraceService.record(chatId, AgentTraceStep.RISK_CHECK, AgentTraceStatus.COMPLETED, "Risk wording and uncertainty reminders checked.", Map.of("riskCount", guardedPlan.risks().size()));
            return guardedPlan;
        } catch (RuntimeException ex) {
            log.warn("Structured travel plan generation failed [{}], returning fallback plan", chatId, ex);
            agentTraceService.record(chatId, AgentTraceStep.ITINERARY_GENERATION, AgentTraceStatus.FAILED, "Structured itinerary generation failed, returning fallback.", Map.of("error", ex.getClass().getSimpleName()));
            return fallbackPlan(inputGuardrail.normalizedInput(), loadedSkillIds);
        }
    }

    String buildUserPrompt(String message, List<Skill> selectedSkills) {
        return """
                User travel request:
                %s

                Loaded travel skills:
                %s

                Output requirements:
                - summary: concise planning summary.
                - destination, departure, days, travelers: extract from user request where explicit; do not invent travelers.
                - budget: include total, currency, itemized estimates, and uncertainty note.
                - budget items: keep amount and note consistent; do not force the full user budget to be spent; put unused generous budget into optional upgrades/reserve/contingency.
                - domestic China transport: separate intercity rail/flight from local transport when practical.
                - domestic China budget: keep ordinary rail, local transport, food, hotels, and ticket estimates conservative and locally plausible; do not inflate line items to spend the user's full budget.
                - ordinary China high-speed rail: use second-class seat planning estimates unless business class/private transfer is requested; Beijing↔Shanghai is around 650 CNY per person per one-way seat.
                - do not double count the same transport cost.
                - itineraryDays: one object per day where possible.
                - transportation, accommodation, risks, alternatives: practical arrays for UI cards.
                - loadedSkills: include the loaded skill ids exactly.
                """.formatted(message, renderSkills(selectedSkills));
    }

    private String renderSkills(List<Skill> selectedSkills) {
        if (selectedSkills.isEmpty()) {
            return "- none";
        }
        StringBuilder builder = new StringBuilder();
        for (Skill skill : selectedSkills) {
            builder.append("- ")
                    .append(skill.id())
                    .append(" (")
                    .append(skill.name())
                    .append("): ")
                    .append(skill.description())
                    .append(System.lineSeparator())
                    .append(skill.content())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private TravelPlan normalizePlan(TravelPlan plan, List<String> loadedSkillIds) {
        if (plan == null) {
            return fallbackPlan("模型未返回可解析的旅行需求", loadedSkillIds);
        }
        return new TravelPlan(
                plan.summary(),
                plan.destination(),
                plan.departure(),
                plan.days(),
                plan.travelers(),
                plan.budget(),
                plan.itineraryDays(),
                plan.transportation(),
                plan.accommodation(),
                plan.risks(),
                plan.alternatives(),
                loadedSkillIds
        );
    }

    private TravelPlan fallbackPlan(String message, List<String> loadedSkillIds) {
        return new TravelPlan(
                "已根据当前请求生成结构化旅行规划草案，但模型结构化输出解析失败，以下为可继续完善的降级结果。",
                null,
                null,
                null,
                null,
                new TravelPlan.Budget(
                        null,
                        "CNY",
                        List.of(),
                        "预算需要结合出发地、城市组合、机票和住宿实时价格进一步估算。"
                ),
                List.of(),
                List.of("建议先确认出发城市、抵达机场、城市间交通方式和同行人的体力情况。"),
                List.of("建议选择交通便利、减少换酒店次数的住宿区域。"),
                List.of(
                        "模型未能稳定生成完整结构化 JSON，已返回降级结构。",
                        "天气、签证、交通政策和价格均可能变化，请以官方或实时信息为准。",
                        "原始需求：" + message
                ),
                List.of("补充出发地、偏好城市、住宿标准和每日节奏后，可重新生成更完整的结构化行程。"),
                loadedSkillIds
        );
    }

    private TravelPlan nonTravelFallbackPlan(String message, List<String> warnings) {
        return new TravelPlan(
                "当前后端专注旅行规划，暂不处理非旅行主题。可以补充目的地、出发地、天数、人数、预算和偏好后生成结构化行程。",
                null,
                null,
                null,
                null,
                new TravelPlan.Budget(null, "CNY", List.of(), "未识别到旅行预算。"),
                List.of(),
                List.of(),
                List.of(),
                warnings.isEmpty() ? List.of("请求未识别为旅行规划场景。") : warnings,
                List.of("示例：我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点。原始请求：" + message),
                List.of()
        );
    }

}
