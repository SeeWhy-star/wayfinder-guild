# Release Notes: Wayfinder Guild v0.1.0

## Version

`v0.1.0`

Release type: portfolio release candidate / first public launch candidate.

## Release Content

Wayfinder Guild v0.1.0 includes:

- Agentic Travel Backend with Spring Boot, Java 21, Spring AI, and DeepSeek.
- Structured TravelPlan generation.
- Skills system based on Markdown resources.
- Travel eval harness and eval metadata pages.
- Guardrails for safer Agent/tool behavior.
- Agent Trace APIs and frontend trace timeline.
- Explainable RAG endpoint and frontend RAG library page.
- RPG Portfolio Backend metadata APIs.
- Vue 3 / Vite frontend with Phaser RPG homepage.
- Capability pages for profile, projects, skills, evals, architecture, travel agent, RAG library, and trace.
- Demo Mode for stable interviews and public demos.
- Rust Wayfinder CLI for static metadata quality checks.
- Deployment, verification, launch checklist, and release governance documentation.

## Impact Scope

User-facing:

- New portfolio website experience.
- Interactive Agent capability demonstrations.
- More reliable demo and fallback states.

Backend:

- Travel, RAG, trace, eval, RPG metadata, and demo APIs.
- Environment-driven model/tool configuration.

Frontend:

- RPG homepage and capability pages.
- Same-origin `/api` deployment expectation.

Tooling:

- Rust CLI under `tools/wayfinder-cli`.
- Release verification docs.

## Configuration Items

Important environment variables:

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8123
WAYFINDER_DEMO_ENABLED=false
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_CHAT_MODEL=deepseek-chat
DB_URL=
DB_USERNAME=
DB_PASSWORD=
SEARCH_PROVIDER=tavily
TAVILY_API_KEY=
PEXELS_API_KEY=
AMAP_MAPS_API_KEY=
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO
VITE_API_BASE=/api
```

Mode strategy:

- Public stable demo: `WAYFINDER_DEMO_ENABLED=true`.
- Owner live demo: `WAYFINDER_DEMO_ENABLED=false` with valid DeepSeek configuration.

## Risk Points

- Live DeepSeek calls may be slow, costly, or temporarily unavailable.
- Structured TravelPlan generation can take 30-90 seconds.
- Public Swagger/Knife4j exposure should be reviewed.
- CORS is permissive for development; production should prefer same-origin Nginx proxy or restricted origins.
- Optional PgVector/RAG dependencies may not be available in minimal deployments.
- Phaser increases frontend bundle size.

## Rollback Plan

Backend rollback:

1. Keep the previous JAR as `/opt/wayfinder/wayfinder-guild.previous.jar`.
2. Replace the active JAR.
3. Restart systemd service.

```bash
sudo cp /opt/wayfinder/wayfinder-guild.previous.jar /opt/wayfinder/wayfinder-guild.jar
sudo systemctl restart wayfinder
```

Frontend rollback:

1. Keep previous frontend dist archive.
2. Restore `/var/www/wayfinder`.
3. Reload Nginx if needed.

```bash
sudo rm -rf /var/www/wayfinder
sudo mkdir -p /var/www/wayfinder
sudo tar -xzf /opt/wayfinder/frontend.previous.tgz -C /var/www
sudo systemctl reload nginx
```

Emergency mitigation:

- Enable Demo Mode if live model calls fail.
- Temporarily hide or avoid live model routes during interviews.
- Roll DNS or Nginx back to a previous static build if frontend launch fails.

## Post-launch Checks

Within 10 minutes:

- Open `/`.
- Open `/api/travel/health`.
- Open `/profile`, `/travel-agent`, `/rag-library`, `/trace`, and `/architecture`.
- Confirm browser console has no blocking errors.
- Confirm Nginx and Spring Boot logs have no repeated 5xx errors.

Within 24 hours:

- Review DeepSeek cost and quota.
- Review access logs and error logs.
- Confirm certificate renewal timer is active.
- Confirm no secrets appear in logs.
- Run `wayfinder-cli doctor` against the deployed release branch.
