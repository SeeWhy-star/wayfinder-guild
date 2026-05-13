# Agentic Travel Planning Backend

This backend upgrades the travel planner from a single prompt chat flow into a layered agentic application.

## Runtime Flow

`POST /api/travel/plan`

1. `WayfinderTravelController`
   - Handles HTTP protocol, validation, and chat id normalization.
   - Delegates to `WayfinderTravelFacade`.
2. `WayfinderTravelFacade`
   - Compatibility facade for existing travel capabilities.
   - Routes structured planning to `TravelOrchestratorService`.
3. `TravelOrchestratorService`
   - Coordinates lightweight expert services.
   - Records trace events for each major phase.
4. Expert services
   - `RequirementCollectorService`: validates input and detects missing fields.
   - `ItineraryPlannerService`: delegates structured generation to `TravelPlanService`.
   - `BudgetEstimatorService`: checks and enriches budget structure.
   - `RiskAdvisorService`: applies output guardrails and missing-info reminders.
   - `ReportComposerService`: prepares the final `TravelPlan` response.

## Skills

Skills are markdown resources under `src/main/resources/skills/{skill-id}/SKILL.md`.

Each skill contains front matter:

- `id`
- `name`
- `description`
- `tags`
- `triggers`
- `priority`

`SkillLoaderService` loads all skill files and selects relevant skills from user input. Skills are composed into the structured planning prompt rather than hardcoded into one giant system prompt.

## Structured Output

`TravelPlan` is the stable backend shape for UI cards, reports, evals, and orchestration:

- `summary`
- `destination`
- `departure`
- `days`
- `travelers`
- `budget`
- `itineraryDays`
- `transportation`
- `accommodation`
- `risks`
- `alternatives`
- `loadedSkills`

`TravelPlanService` calls the configured `travelChatClient` and parses model output into `TravelPlan`. If parsing fails, it returns a safe fallback plan.

## Eval Harness

Eval cases live in `evals/travel-cases.json`.

`TravelEvalHarness` scores plans with deterministic rules:

- Clarifying missing information.
- Structured itinerary presence.
- Budget reasonableness.
- Risk reminders.
- Unsafe absolute claims.
- Disallowed tool calls.
- Expected skill loading.

The optional runner is disabled by default and can be enabled with:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--travel.eval.enabled=true
```

## Guardrails

`GuardrailService` provides:

- Input checks: blank, too long, prompt injection, non-travel downgrade.
- Tool checks: file path whitelist, terminal command allowlist/blocklist, URL validation.
- Output checks: softens unsafe guarantees and adds uncertainty reminders for weather, visa, and policy content.

High-risk tools now call guardrails before execution:

- `FileOperationTool`
- `TerminalOperationTool`
- `ResourceDownloadTool`

## Agent Trace

`AgentTraceService` stores recent per-chat events in memory and exposes a Flux stream.

Trace endpoints:

- `GET /api/travel/trace/{chatId}`
- `GET /api/travel/trace/{chatId}/stream`

Current trace steps include:

- User intent recognition.
- Skill loading.
- RAG retrieval.
- Tool and MCP calls.
- Itinerary generation.
- Budget check.
- Risk check.
- Report generation.

## Design Boundaries

- Controllers only handle protocol and request/response mapping.
- `WayfinderTravelFacade` remains the compatibility facade.
- Orchestration is lightweight service composition, not a heavy multi-agent framework.
- Prompts, skills, and eval cases stay configurable where practical.
- No real API keys are committed.
