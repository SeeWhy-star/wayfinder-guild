# Technical Design: Wayfinder Guild

## 1. Overall Architecture

```text
Browser
  -> Vue 3 / Vite frontend
     -> Phaser RPG homepage
     -> Capability pages
  -> /api reverse proxy
     -> Spring Boot 3.4 / Java 21 backend
        -> Agentic Travel Backend
        -> RPG Portfolio Backend
        -> RAG / Skills / Eval / Trace / Guardrails
        -> DeepSeek via Spring AI
  -> Rust Wayfinder CLI
     -> local static quality checks for metadata, prompts, evals, and skills
```

The product is split into three layers:

- Experience layer: Vue pages and Phaser map.
- Capability layer: Spring Boot APIs and Agent services.
- Governance layer: Rust CLI, verification docs, release checklists, and deployment docs.

## 2. Frontend Architecture

Technology:

- Vue 3
- Vite
- Axios
- Vue Router
- Phaser for the RPG homepage only

Key decisions:

- Phaser is limited to the homepage map experience.
- Capability pages are normal Vue pages for maintainability.
- API base URL defaults to `/api`, which works with same-origin Nginx reverse proxy.
- Vue history mode requires Nginx `try_files $uri $uri/ /index.html`.
- TravelPlan generation uses a longer request timeout because live model structured output can be slow.

Important routes:

- `/`
- `/profile`
- `/projects`
- `/skills`
- `/evals`
- `/travel-agent`
- `/rag-library`
- `/trace`
- `/architecture`

## 3. Backend Architecture

Technology:

- Spring Boot 3.4
- Java 21
- Spring AI
- DeepSeek OpenAI-compatible API
- Optional PostgreSQL / PgVector for full RAG

Core backend boundaries:

```text
Controller
  -> Service
     -> Orchestrator / Skill / RAG / Eval / Trace / Guardrail components
```

Controller responsibilities:

- HTTP protocol mapping.
- Request validation.
- ChatId normalization.
- Delegation to services.

Service responsibilities:

- Business flow.
- Resource loading.
- Agent orchestration.
- Demo fallback.
- Trace emission.

## 4. API Design

Travel APIs:

- `POST /api/travel/chat`
- `GET /api/travel/chat/stream`
- `POST /api/travel/plan`
- `POST /api/travel/rag`
- `POST /api/travel/rag/explain`
- `GET /api/travel/trace/{chatId}`
- `GET /api/travel/trace/{chatId}/stream`
- `GET /api/travel/health`

RPG APIs:

- `GET /api/rpg/world`
- `GET /api/rpg/npcs`
- `GET /api/rpg/npcs/{id}`
- `GET /api/rpg/projects`
- `GET /api/rpg/skills`
- `POST /api/rpg/skills/match`
- `GET /api/rpg/modules`
- `GET /api/rpg/profile`
- `GET /api/rpg/evals/cases`
- `GET /api/rpg/evals/rules`

Design principles:

- JSON resources are static and readable.
- Frontend has graceful fallback where possible.
- Existing `/api/travel/**` compatibility is preserved.
- No database is required for RPG metadata in v0.1.0.

## 5. Agentic Travel Backend

Main flow:

```text
WayfinderTravelController
  -> WayfinderTravelFacade
     -> TravelOrchestratorService
        -> RequirementCollector
        -> ItineraryPlanner
        -> BudgetEstimator
        -> RiskAdvisor
        -> ReportComposer
```

Supporting capabilities:

- Skills loaded from `src/main/resources/skills/**/SKILL.md`.
- Structured `TravelPlan` model for UI-ready output.
- `TravelEvalHarness` for regression-oriented quality checks.
- `GuardrailService` for input/tool/output safety boundaries.
- `AgentTraceService` for execution trace and streamable observability.
- RAG explain flow for query rewrite, retrieved documents, and answer.

## 6. RPG Portfolio Backend

Resource files:

- `src/main/resources/rpg/world.json`
- `src/main/resources/rpg/projects.json`
- `src/main/resources/rpg/skills.json`
- `src/main/resources/rpg/modules.json`
- `src/main/resources/rpg/profile.json`

Purpose:

- Decouple portfolio content from frontend rendering.
- Let the RPG homepage and capability pages consume consistent metadata.
- Make product structure visible to interviewers.

## 7. Demo Mode

Configuration:

```env
WAYFINDER_DEMO_ENABLED=true
```

Behavior:

- Returns stable demo TravelPlan.
- Returns demo trace when real trace is absent.
- Returns demo RAG explain response.
- Returns eval sample results.

Use cases:

- Public portfolio traffic.
- Interview environments with unreliable network.
- Cost-controlled demos.

## 8. Owner Live Mode

Configuration:

```env
WAYFINDER_DEMO_ENABLED=false
DEEPSEEK_API_KEY=...
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_CHAT_MODEL=deepseek-chat
```

Behavior:

- Calls the live model through Spring AI.
- May have variable latency.
- Requires billing and quota controls.

## 9. Rust Wayfinder CLI

Path:

```text
tools/wayfinder-cli
```

Commands:

- `doctor`
- `lint-skills`
- `lint-rpg`
- `lint-evals`
- `lint-prompts`
- `summary`

Purpose:

- Static quality gate for portfolio metadata.
- Validates Skills front matter, RPG JSON, eval cases, and prompt templates.
- Provides a simple engineering governance story for interviews and CI.

## 10. Security Design

Current controls:

- Sensitive values are environment-backed in `application.yml`.
- `application-local.yml` is ignored.
- `.env` and local env variants are ignored.
- Guardrails protect risky file, URL, and terminal tool usage.
- Demo Mode reduces reliance on live keys for public demos.

Production recommendations:

- Use same-origin `/api` behind Nginx.
- Restrict CORS if exposing a separate API domain.
- Restrict or disable Swagger/Knife4j on public production.
- Set Spring AI logging to `INFO` or `WARN`.
- Rotate keys that appeared in local files or demos.

## 11. Degradation Strategy

Frontend:

- Loading, empty, error, and demo fallback states.
- Longer timeout for structured TravelPlan live calls.
- Static/RPG pages remain useful when model endpoints are unavailable.

Backend:

- Demo Mode for stable outputs.
- RAG explain supports `demo`, `lightweight`, and `pgvector` modes.
- Public production defaults to `travel.rag.mode=demo`, so PgVector is not required.
- Lightweight RAG searches local Markdown and avoids cloud vector database cost.
- PgVector is an optional Owner Live / local deep demo path; if it is unavailable, the API falls back to lightweight retrieval instead of returning 500.
- Trace fallback in Demo Mode when no real trace exists.

Operational:

- Prefer Demo Mode for public launch if live model cost or latency is risky.
- Use Owner Live Mode during controlled interviews.

## 12. Deployment Topology

Recommended production topology:

```text
Nginx
  -> /var/www/wayfinder frontend static files
  -> /api reverse proxy to 127.0.0.1:8123/api
Spring Boot JAR
  -> systemd service
  -> environment file /etc/wayfinder/wayfinder.env
HTTPS
  -> Certbot
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for concrete Nginx, systemd, and HTTPS examples.
