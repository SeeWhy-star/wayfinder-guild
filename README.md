# Wayfinder Guild

[中文 README](README.zh-CN.md)

Wayfinder Guild is an AI engineering portfolio and Agentic AI demo built with Spring Boot 3.4, Java 21, Spring AI, DeepSeek's OpenAI-compatible API, Vue 3, Vite, and Phaser. The first screen is an explorable RPG-style portfolio town; the backend demonstrates travel planning agents, SyManus tool use, RAG, Skills, evals, guardrails, and agent traceability.

The public brand is Wayfinder Guild. Travel planning is still the core domain, so code names such as `TravelPlan`, `TravelRagService`, and related travel model names are intentionally retained.

## Capabilities

| Capability | What It Shows |
| --- | --- |
| Travel chat | Spring AI `ChatClient` with sync and SSE streaming endpoints. |
| Structured planning | `POST /api/travel/plan` returns a typed `TravelPlan` for cards, scoring, and trace review. |
| Skills | Markdown skills in `src/main/resources/skills/**/SKILL.md` are selected by travel context. |
| Agent orchestration | `RequirementCollector -> ItineraryPlanner -> BudgetEstimator -> RiskAdvisor -> ReportComposer`. |
| RAG | Stable demo RAG, local Markdown retrieval, and optional PgVector retrieval. |
| SyManus tools | Bounded file, PDF, image, download, web search/scrape, terminal, and artifact-link demos. |
| Guardrails | Input checks, URL/file/terminal boundaries, travel output softening, and artifact validation. |
| Agent Trace | Records intent, skill loading, RAG, planning, budget, risk, report, tool, and MCP steps. |
| Eval Harness | Configured travel eval cases plus a Rust static quality gate for project metadata. |

## Tech Stack

| Layer | Stack |
| --- | --- |
| Backend | Spring Boot 3.4, Java 21, Maven |
| LLM | Spring AI, DeepSeek OpenAI-compatible API |
| RAG | Spring AI VectorStore, PgVector, Markdown documents |
| Agent/tooling | Spring AI `@Tool`, custom ReAct/SyManus agents, Hutool, Jsoup, iText 9 |
| Frontend | Vue 3, Vite, Axios, Phaser |
| Optional MCP | `sy-image-search-mcp`, Spring AI MCP server/client |
| Quality | JUnit 5, Mockito, Rust `tools/wayfinder-cli` |

## Architecture

```text
Vue / Phaser frontend
  -> /api reverse proxy
    -> WayfinderTravelController
      -> WayfinderTravelFacade
        -> TravelOrchestratorService
          -> RequirementCollectorService
          -> ItineraryPlannerService
            -> TravelPlanService
              -> SkillLoaderService
              -> ChatClient / DeepSeek
          -> BudgetEstimatorService
          -> RiskAdvisorService
          -> ReportComposerService
```

Supporting services:

```text
GuardrailService       input, tool, URL, file, terminal, and output safety
TravelRagService       demo / lightweight / pgvector RAG modes
AgentTraceService      in-memory execution trace and SSE stream
TravelEvalHarness      deterministic scoring for TravelPlan quality
SyManus                bounded tool-calling agent with artifact registration
Wayfinder CLI          Rust static checks for skills, RPG data, evals, prompts, RAG docs, naming
```

## Repository Layout

```text
.
|-- src/main/java/com/seewhy/syaiagent/
|   |-- controller/       HTTP APIs
|   |-- app/              Wayfinder facade
|   |-- service/          chat, plan, RAG, trace, demo, RPG services
|   |-- orchestrator/     multi-step travel planning pipeline
|   |-- agent/            ReAct, ToolCallAgent, SyManus
|   |-- tools/            search, scrape, download, file, PDF, terminal, image tools
|   |-- rag/              document loading, query rewriting, PgVector config
|   |-- guardrail/        safety checks
|   |-- trace/            agent trace model and service
|   `-- eval/             travel eval harness
|-- src/main/resources/
|   |-- skills/           travel skills
|   |-- document/         local RAG Markdown documents
|   |-- rpg/              portfolio town metadata
|   `-- prompts/          RPG prompt templates
|-- frontend/             Vue 3 + Vite + Phaser app
|-- sy-image-search-mcp/  optional Pexels image-search MCP server
|-- tools/wayfinder-cli/  Rust static quality checker
|-- evals/                travel eval cases
`-- docs/                 architecture, deployment, verification, naming, RAG docs
```

## Run Locally

Requirements:

- JDK 21
- Maven 3.6+ or the included Maven wrapper scripts
- Node.js 18+
- Rust/Cargo only for `tools/wayfinder-cli`

The Maven wrapper is configured as `distributionType=only-script`, so the repository intentionally does not track `.mvn/wrapper/maven-wrapper.jar`.

Backend:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
mvn spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Default backend URL: `http://localhost:8123/api`  
Default frontend URL: `http://localhost:5173`

## Environment

Public production should use server-side environment variables, not committed local config files.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Use `prod` for deployment, `local` only for local `application-local.yml`. |
| `WAYFINDER_DEMO_ENABLED` | `true` for public Demo Mode; `false` for Owner Live Mode. |
| `TRAVEL_RAG_MODE` | `demo`, `lightweight`, or `pgvector`. |
| `DEEPSEEK_API_KEY` | Required for Owner Live model calls; public Demo Mode boots with a non-secret disabled placeholder. |
| `DEEPSEEK_BASE_URL` | Defaults to `https://api.deepseek.com`. |
| `DEEPSEEK_CHAT_MODEL` | Defaults to `deepseek-chat`. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Required only for PgVector mode. |
| `SEARCH_PROVIDER`, `TAVILY_API_KEY` | Optional live web search. Default is `disabled`; use `tavily` only for Owner Live Mode. |
| `PEXELS_API_KEY` | Optional image search / MCP key. |
| `AMAP_MAPS_API_KEY` | Optional AMap MCP key. |
| `WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS` | Comma-separated allowed frontend origins. |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`, `KNIFE4J_ENABLE` | Keep `false` in public deployment unless deliberately exposing API docs. |

Local secret templates:

- `.env.example`
- `src/main/resources/application-local.yml.example`
- `sy-image-search-mcp/src/main/resources/application-local.yml.example`

Never commit real `.env`, `application-local.yml`, `private-docs`, `data`, `tmp`, `target`, `node_modules`, IDE files, or generated build outputs.

## Modes

**Demo Mode**: `WAYFINDER_DEMO_ENABLED=true`, `TRAVEL_RAG_MODE=demo`

Use this for the public portfolio. It keeps core pages stable, avoids live model/vector costs for demo paths, and keeps the RAG Library deterministic.

**Owner Live Mode**: `WAYFINDER_DEMO_ENABLED=false`

Use this only for controlled demos where keys, model quota, tool boundaries, and database availability are known. Pair it with `TRAVEL_RAG_MODE=lightweight` for local Markdown retrieval or `TRAVEL_RAG_MODE=pgvector` for PgVector.

RAG behavior:

- `demo`: fixed explainable RAG responses for public stability.
- `lightweight`: retrieves bundled Markdown from `src/main/resources/document/*.md` without PgVector.
- `pgvector`: uses Spring AI VectorStore and PgVector; if unavailable, the service degrades to lightweight retrieval.

## Main APIs

Base path: `/api`

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/health` | GET | Root health check. |
| `/travel/health` | GET | Travel service health check. |
| `/travel/chat` | POST | Sync travel chat. |
| `/travel/chat/stream` | GET | SSE travel chat stream. |
| `/travel/plan` | POST | Structured `TravelPlan`. |
| `/travel/report` | POST | Structured travel report. |
| `/travel/rag` | POST | RAG answer. |
| `/travel/rag/explain` | POST | RAG answer with retrieved documents. |
| `/travel/manus/chat` | GET | SyManus tool-agent stream. |
| `/travel/manus/demo-tool` | POST | Fixed safe demo tool runs. |
| `/travel/manus/artifacts/{artifactId}` | GET | Secure artifact preview. |
| `/travel/trace/{chatId}` | GET | Agent trace history. |
| `/travel/trace/{chatId}/stream` | GET | Agent trace SSE stream. |
| `/rpg/world` | GET | Phaser portfolio map metadata. |
| `/rpg/evals/run/{caseId}` | POST | Run a live eval case. |

Example:

```http
POST /api/travel/plan
Content-Type: application/json
```

```json
{
  "message": "Plan a relaxed 7-day Japan family trip for 3 people in June with a 20000 CNY budget.",
  "chatId": "demo-japan-family"
}
```

## Verification

```powershell
mvn test
cd frontend
npm run build
cd ..\tools\wayfinder-cli
cargo test
cargo run -- doctor --workspace ..\..
cd ..\..\sy-image-search-mcp
mvn test
```

See [docs/VERIFY.md](docs/VERIFY.md) for the release-candidate checklist.

## Deployment Notes

Recommended public runtime:

```env
SPRING_PROFILES_ACTIVE=prod
WAYFINDER_DEMO_ENABLED=true
TRAVEL_RAG_MODE=demo
WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS=https://your-domain.example
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
KNIFE4J_ENABLE=false
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO
LOGGING_LEVEL_COM_SEEWHY_SYAIAGENT=INFO
```

Serve `frontend/dist` behind HTTPS and reverse proxy `/api` to Spring Boot. Keep live model, PgVector, MCP, terminal, file-write, and download demos behind owner-controlled settings.

Before creating a new public GitHub repository:

- rotate any key that may have existed in local files or old history;
- run a secret scan on the final branch/history;
- keep `application-local.yml`, `.env`, `private-docs`, `data`, `tmp`, `target`, `node_modules`, and IDE files out of Git;
- decide whether to publish `sy-image-search-mcp` with this repository or document it as optional.

## Documentation

- [Deployment Guide](docs/DEPLOYMENT.md)
- [Verification Guide](docs/VERIFY.md)
- [Security Notes](SECURITY.md)
- [Agentic Travel Backend](docs/AGENTIC-TRAVEL-BACKEND.md)
- [Technical Design](docs/TECH-DESIGN-WAYFINDER.md)
- [RAG Cost Strategy](docs/RAG-COST-STRATEGY.md)
- [Wayfinder CLI](tools/wayfinder-cli/README.md)

## License

This project is released under the [MIT License](LICENSE).
