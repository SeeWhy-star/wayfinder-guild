package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.eval.TravelEvalRuleResult;
import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunResponse;
import com.seewhy.syaiagent.model.rpg.RpgEvalSampleResult;
import com.seewhy.syaiagent.trace.AgentTraceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WayfinderDemoService {

    private static final String DEMO_TRAVEL_PLAN_RESOURCE = "rpg/demo-travel-plan.json";
    private static final String DEMO_TRAVEL_TRACE_RESOURCE = "rpg/demo-travel-trace.json";
    private static final String DEMO_TRAVEL_SCORE_RESOURCE = "rpg/demo-travel-score.json";

    private final boolean enabled;
    private final TravelPlan demoTravelPlan;
    private final List<AgentTraceEvent> demoTraceEvents;
    private final List<RpgEvalSampleResult> demoEvalResults;

    @Autowired
    public WayfinderDemoService(@Value("${wayfinder.demo.enabled:false}") boolean enabled,
                                ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.demoTravelPlan = readDemoTravelPlan(objectMapper);
        this.demoTraceEvents = readDemoTraceEvents(objectMapper);
        this.demoEvalResults = readDemoEvalResults(objectMapper);
    }

    public WayfinderDemoService(boolean enabled) {
        this(enabled, new ObjectMapper().findAndRegisterModules());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TravelPlan demoTravelPlan() {
        return demoTravelPlan;
    }

    public Flux<String> demoChatStream(String message, String chatId) {
        return Flux.just(
                "已收到你的旅行需求：上海出发，5 天家庭轻松游，目的地 Kyoto，预算 15000 CNY。",
                "\n\n我会先抽取 destination、departure、days、budget 和 travel style；如果缺少 travelers，会保留人数假设和风险提示。",
                "\n已加载 Skills：family-trip-planning、japan-travel、relaxed-travel、budget-travel。",
                "\n随后进入 requirement -> RAG -> tool/plan -> guardrail -> trace 链路，生成可渲染的 TravelPlan。",
                "\n\n当前 public demo 使用一次 live run 冻结的稳定样例：Structured Plan 是 Kyoto 5-day fixture，评分样例为 92/100，Trace 来自同一次真实链路。",
                "\nOwner token verification only unlocks live controls. To run live output, enter a valid Owner token and explicitly enable Live Chat or Live TravelPlan; otherwise the public demo stream and fixture remain active.",
                "\n\n",
                "[DONE]"
        );
    }

    public RagExplainResponse demoRagExplain(String originalQuery, String chatId) {
        return DemoRagExplainResponses.build(originalQuery, chatId);
    }

    public List<AgentTraceEvent> demoTrace(String chatId) {
        String id = chatId == null || chatId.isBlank() ? "demo-kyoto-family" : chatId;
        return demoTraceEvents.stream()
                .map(event -> new AgentTraceEvent(
                        event.traceId(),
                        id,
                        event.step(),
                        event.status(),
                        event.message(),
                        fixtureMetadata(event.metadata()),
                        event.timestamp()
                ))
                .toList();
    }

    public List<RpgEvalSampleResult> demoEvalResults() {
        return demoEvalResults;
    }

    private TravelPlan readDemoTravelPlan(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(DEMO_TRAVEL_PLAN_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), TravelPlan.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load demo travel plan resource: " + DEMO_TRAVEL_PLAN_RESOURCE, ex);
        }
    }

    private List<AgentTraceEvent> readDemoTraceEvents(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(DEMO_TRAVEL_TRACE_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load demo travel trace resource: " + DEMO_TRAVEL_TRACE_RESOURCE, ex);
        }
    }

    private List<RpgEvalSampleResult> readDemoEvalResults(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(DEMO_TRAVEL_SCORE_RESOURCE);
        try {
            RpgEvalRunResponse run = objectMapper.readValue(resource.getInputStream(), RpgEvalRunResponse.class);
            return List.of(
                    mappedSample(run, "case-alignment", "request-coverage", 20),
                    mappedSample(run, "clarifying-question", "missing-info-honesty", 10),
                    mappedSample(run, "structured-itinerary", "structured-itinerary", 15),
                    mappedSample(run, "budget-reasonableness", "budget-grounding", 15),
                    mappedSample(run, "risk-reminders", "risk-reminders", 15),
                    mappedSample(run, "unsafe-claims", "safe-claims", 15),
                    mappedSample(run, "disallowed-tools", "tool-boundary", 5),
                    new RpgEvalSampleResult(
                            "expected-skills",
                            true,
                            5,
                            5,
                            String.join(", ", demoTravelPlan.loadedSkills()) + " are loaded in the frozen live TravelPlan fixture."
                    )
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load demo travel score resource: " + DEMO_TRAVEL_SCORE_RESOURCE, ex);
        }
    }

    private RpgEvalSampleResult mappedSample(RpgEvalRunResponse run, String sampleRule, String fixtureRule, int sampleMaxScore) {
        TravelEvalRuleResult rule = run.result().rules().stream()
                .filter(candidate -> candidate.rule().equals(fixtureRule))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing rule in demo travel score fixture: " + fixtureRule));
        int score = scaleScore(rule.score(), rule.maxScore(), sampleMaxScore);
        return new RpgEvalSampleResult(sampleRule, rule.passed(), score, sampleMaxScore, rule.message());
    }

    private int scaleScore(int score, int maxScore, int targetMaxScore) {
        if (maxScore <= 0) {
            return 0;
        }
        return Math.min(targetMaxScore, Math.round(score * targetMaxScore / (float) maxScore));
    }

    private Map<String, Object> fixtureMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        enriched.put("source", "fixture");
        enriched.put("mode", "demo");
        return enriched;
    }
}
