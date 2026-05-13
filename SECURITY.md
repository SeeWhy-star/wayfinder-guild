# 敏感信息与安全配置

本仓库**不包含**任何 API Key、数据库密码等敏感信息。请按以下方式配置后再运行项目。

## 方式一：环境变量（推荐用于 CI/生产）

在运行前设置以下环境变量：

| 变量名              | 说明                                                                       |
| ------------------- | -------------------------------------------------------------------------- |
| `DB_URL`            | 数据库连接 URL，如 `jdbc:postgresql://host:5432/dbname`                    |
| `DB_USERNAME`       | 数据库用户名                                                               |
| `DB_PASSWORD`       | 数据库密码                                                                 |
| `DEEPSEEK_API_KEY`  | DeepSeek API Key；Owner Live 模式需要，公开 Demo Mode 可省略               |
| `DEEPSEEK_BASE_URL` | DeepSeek OpenAI-compatible API 地址，默认 `https://api.deepseek.com`       |
| `DEEPSEEK_CHAT_MODEL` | DeepSeek 对话模型，默认 `deepseek-chat`                                  |
| `SEARCH_PROVIDER`   | 搜索 provider，公开默认 `disabled`，Owner Live 可设为 `tavily`              |
| `TAVILY_API_KEY`    | Tavily 搜索 API Key（主应用）                                               |
| `PEXELS_API_KEY`    | Pexels 图片搜索 API Key（sy-image-search-mcp 子模块）                      |
| `AMAP_MAPS_API_KEY` | 高德地图 MCP 服务 API Key（启用 amap-maps 时需在启动前设置，子进程会继承） |
| `WAYFINDER_DEMO_ENABLED` | 公开部署建议为 `true`，Owner Live 模式再改为 `false` |
| `TRAVEL_RAG_MODE` | `demo` / `lightweight` / `pgvector`，公开部署建议 `demo` 或 `lightweight` |
| `WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS` | 允许跨域访问的前端域名，多个值用英文逗号分隔 |
| `SPRINGDOC_API_DOCS_ENABLED` | 是否开启 OpenAPI JSON，公开部署建议 `false` |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | 是否开启 Swagger UI，公开部署建议 `false` |
| `KNIFE4J_ENABLE` | 是否开启 Knife4j，公开部署建议 `false` |

示例（Windows PowerShell）：

```powershell
$env:DB_URL = "jdbc:postgresql://your-host:5432/your_db"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
$env:DEEPSEEK_API_KEY = "your-deepseek-key"
$env:DEEPSEEK_BASE_URL = "https://api.deepseek.com"
$env:DEEPSEEK_CHAT_MODEL = "deepseek-chat"
$env:WAYFINDER_DEMO_ENABLED = "false"
$env:TRAVEL_RAG_MODE = "lightweight"
```

## 方式二：本地配置文件（适合本地开发）

1. **主应用**：复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入数据库、DeepSeek、search-api 等。
2. **sy-image-search-mcp 子模块**：复制 `sy-image-search-mcp/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入 `pexels.api-key`。
3. **不要**将任何 `application-local.yml` 提交到 Git（已通过 `.gitignore` 忽略）。

主应用不再默认加载 `local` profile。需要读取本地 `application-local.yml` 时，请显式设置 `SPRING_PROFILES_ACTIVE=local`。`sy-image-search-mcp` 默认使用 `sse` profile；如需加载本地密钥示例文件，请设置 `MCP_SPRING_PROFILES_ACTIVE=sse,local`。

**MCP 高德地图**：`mcp-servers.json` 中已移除密钥。启用 amap-maps 时，请在启动主应用前设置环境变量 `AMAP_MAPS_API_KEY`，子进程会继承该环境变量。

## 已脱敏内容

- `application.yml` 中敏感项已改为环境变量占位符（如 `${DB_PASSWORD}`、`${DEEPSEEK_API_KEY}`）。
- 若曾将真实密钥提交过，请在 GitHub 上**轮换**这些 Key 并更新本地/服务器配置。
