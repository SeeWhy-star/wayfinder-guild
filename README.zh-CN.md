# Wayfinder Guild

[English README](README.md)

Wayfinder Guild 是一个 AI Engineering Portfolio / Agentic AI 能力展示项目，基于 Spring Boot 3.4、Java 21、Spring AI、DeepSeek OpenAI-compatible API、Vue 3、Vite 与 Phaser。首页是一座可探索的 RPG 作品集小镇；后端展示旅行规划 Agent、SyManus 工具调用、RAG、Skills、Eval Harness、Guardrails 与 Agent Trace。

对外品牌统一使用 Wayfinder Guild。旅行规划仍是核心业务领域，所以代码中的 `TravelPlan`、`TravelRagService` 等旅行领域命名会保留。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 旅行对话 | Spring AI `ChatClient`，支持同步接口和 SSE 流式接口。 |
| 结构化规划 | `POST /api/travel/plan` 输出类型化 `TravelPlan`，用于卡片展示、评分和 Trace 复盘。 |
| Skills | `src/main/resources/skills/**/SKILL.md` 按旅行场景选择技能。 |
| Agent 编排 | `RequirementCollector -> ItineraryPlanner -> BudgetEstimator -> RiskAdvisor -> ReportComposer`。 |
| RAG | 稳定 Demo RAG、本地 Markdown 检索、可选 PgVector 检索。 |
| SyManus 工具 | 有边界的文件、PDF、图片、下载、网页搜索/抓取、终端和 artifact link 演示。 |
| Guardrails | 输入检查、URL/文件/终端边界、旅行输出软化、artifact 校验。 |
| Agent Trace | 记录意图识别、Skill 加载、RAG、规划、预算、风险、报告、工具和 MCP 步骤。 |
| Eval Harness | 配置化旅行评测样例，以及 Rust 静态质量门禁。 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4, Java 21, Maven |
| 大模型 | Spring AI, DeepSeek OpenAI-compatible API |
| RAG | Spring AI VectorStore, PgVector, Markdown documents |
| Agent / 工具 | Spring AI `@Tool`, 自研 ReAct/SyManus, Hutool, Jsoup, iText 9 |
| 前端 | Vue 3, Vite, Axios, Phaser |
| 可选 MCP | `sy-image-search-mcp`, Spring AI MCP server/client |
| 质量验证 | JUnit 5, Mockito, Rust `tools/wayfinder-cli` |

## 架构链路

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

支撑模块：

```text
GuardrailService       输入、工具、URL、文件、终端与输出安全边界
TravelRagService       demo / lightweight / pgvector 三种 RAG 模式
AgentTraceService      内存执行轨迹与 SSE 事件流
TravelEvalHarness      TravelPlan 质量评分
SyManus                有边界的工具调用 Agent 与 artifact 注册
Wayfinder CLI          Rust 静态检查 Skills、RPG 数据、evals、prompts、RAG docs、命名
```

## 目录结构

```text
.
|-- src/main/java/com/seewhy/syaiagent/
|   |-- controller/       HTTP API
|   |-- app/              Wayfinder facade
|   |-- service/          chat、plan、RAG、trace、demo、RPG 服务
|   |-- orchestrator/     多步骤旅行规划链路
|   |-- agent/            ReAct、ToolCallAgent、SyManus
|   |-- tools/            搜索、抓取、下载、文件、PDF、终端、图片工具
|   |-- rag/              文档加载、查询重写、PgVector 配置
|   |-- guardrail/        安全边界
|   |-- trace/            Agent Trace 模型与服务
|   `-- eval/             Travel Eval Harness
|-- src/main/resources/
|   |-- skills/           旅行 Skills
|   |-- document/         本地 RAG Markdown 文档
|   |-- rpg/              作品集小镇元数据
|   `-- prompts/          RPG prompt templates
|-- frontend/             Vue 3 + Vite + Phaser
|-- sy-image-search-mcp/  可选 Pexels 图片搜索 MCP 服务
|-- tools/wayfinder-cli/  Rust 静态质量检查工具
|-- evals/                旅行 eval cases
`-- docs/                 架构、部署、验证、命名、RAG 文档
```

## 本地运行

环境要求：

- JDK 21
- Maven 3.6+ 或仓库内 Maven wrapper 脚本
- Node.js 18+
- Rust/Cargo 仅用于 `tools/wayfinder-cli`

Maven wrapper 使用 `distributionType=only-script`，所以仓库不跟踪 `.mvn/wrapper/maven-wrapper.jar`。

后端：

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

默认后端地址：`http://localhost:8123/api`  
默认前端地址：`http://localhost:5173`

## 环境变量

公开生产环境应使用服务端环境变量，不提交本地配置文件。

| 变量 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 部署用 `prod`，本地读取 `application-local.yml` 才用 `local`。 |
| `WAYFINDER_DEMO_ENABLED` | 公开 Demo Mode 为 `true`；Owner Live Mode 为 `false`。 |
| `TRAVEL_RAG_MODE` | `demo`、`lightweight` 或 `pgvector`。 |
| `DEEPSEEK_API_KEY` | Owner Live 模型调用需要；公开 Demo Mode 可用非密钥 disabled 占位符启动。 |
| `DEEPSEEK_BASE_URL` | 默认 `https://api.deepseek.com`。 |
| `DEEPSEEK_CHAT_MODEL` | 默认 `deepseek-chat`。 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 仅 PgVector 模式需要。 |
| `SEARCH_PROVIDER`, `TAVILY_API_KEY` | 可选 live web search；默认 `disabled`，Owner Live Mode 再设为 `tavily`。 |
| `PEXELS_API_KEY` | 可选图片搜索 / MCP key。 |
| `AMAP_MAPS_API_KEY` | 可选高德 MCP key。 |
| `WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS` | 允许跨域访问的前端域名，多个值用英文逗号分隔。 |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`, `KNIFE4J_ENABLE` | 公开部署保持 `false`，受控演示再开启。 |

本地敏感配置模板：

- `.env.example`
- `src/main/resources/application-local.yml.example`
- `sy-image-search-mcp/src/main/resources/application-local.yml.example`

真实 `.env`、`application-local.yml`、`private-docs`、`data`、`tmp`、`target`、`node_modules`、IDE 文件和生成物都不能提交。

## Demo Mode / Owner Live Mode

**Demo Mode**：`WAYFINDER_DEMO_ENABLED=true`，`TRAVEL_RAG_MODE=demo`

用于公开作品集。它让核心页面稳定，避免 live model / vector database 成本，并让 RAG Library 输出可复现。

**Owner Live Mode**：`WAYFINDER_DEMO_ENABLED=false`

仅用于受控演示。开启前应确认 API key、模型额度、工具边界和数据库可用性。可配合 `TRAVEL_RAG_MODE=lightweight` 展示本地 Markdown 检索，或配合 `TRAVEL_RAG_MODE=pgvector` 展示 PgVector。

RAG 模式：

- `demo`：固定、可解释、稳定的公开 RAG 响应。
- `lightweight`：读取 `src/main/resources/document/*.md`，不依赖 PgVector。
- `pgvector`：使用 Spring AI VectorStore + PgVector；不可用时降级到 lightweight。

## 主要 API

基础路径：`/api`

| Endpoint | Method | 说明 |
| --- | --- | --- |
| `/health` | GET | 根健康检查。 |
| `/travel/health` | GET | Travel 服务健康检查。 |
| `/travel/chat` | POST | 同步旅行对话。 |
| `/travel/chat/stream` | GET | SSE 旅行对话。 |
| `/travel/plan` | POST | 结构化 `TravelPlan`。 |
| `/travel/report` | POST | 结构化旅行报告。 |
| `/travel/rag` | POST | RAG 问答。 |
| `/travel/rag/explain` | POST | 带检索文档的 RAG 问答。 |
| `/travel/manus/chat` | GET | SyManus 工具 Agent 流式对话。 |
| `/travel/manus/demo-tool` | POST | 固定安全 demo 工具运行。 |
| `/travel/manus/artifacts/{artifactId}` | GET | 安全 artifact 预览。 |
| `/travel/trace/{chatId}` | GET | Agent Trace 历史。 |
| `/travel/trace/{chatId}/stream` | GET | Agent Trace SSE。 |
| `/rpg/world` | GET | Phaser 作品集地图元数据。 |
| `/rpg/evals/run/{caseId}` | POST | 运行 live eval case。 |

示例：

```http
POST /api/travel/plan
Content-Type: application/json
```

```json
{
  "message": "我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点",
  "chatId": "demo-japan-family"
}
```

## 测试验证

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

发布候选检查见 [docs/VERIFY.md](docs/VERIFY.md)。

## 部署建议

推荐公开运行环境：

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

用 HTTPS 托管 `frontend/dist`，并把 `/api` 反向代理到 Spring Boot。live model、PgVector、MCP、终端、文件写入和下载类工具只应在 owner-controlled 场景开启。

新建公开 GitHub 仓库前：

- 轮换任何可能曾出现在本地文件或旧历史里的 key；
- 对最终分支和历史做 secret scan；
- 确保 `application-local.yml`、`.env`、`private-docs`、`data`、`tmp`、`target`、`node_modules`、IDE 文件不进 Git；
- 明确 `sy-image-search-mcp` 是随仓库发布还是作为可选模块说明。

## 相关文档

- [部署指南](docs/DEPLOYMENT.md)
- [验证指南](docs/VERIFY.md)
- [安全说明](SECURITY.md)
- [Agentic Travel Backend](docs/AGENTIC-TRAVEL-BACKEND.md)
- [技术设计](docs/TECH-DESIGN-WAYFINDER.md)
- [RAG 成本策略](docs/RAG-COST-STRATEGY.md)
- [Wayfinder CLI](tools/wayfinder-cli/README.md)

## License

本项目采用 [MIT License](LICENSE)。
