# Wayfinder Guild Deployment Guide

This deployment keeps the public site cheap and safe: visitors can view the demo and portfolio, while live model/API/MCP/tool/artifact features require an Owner Token verified by the backend.

Public Stable Engineering Demos are replayed real-run recordings. They return fixed JSON, realistic terminal output, artifact metadata, and summary cards captured from owner local runs; they do not run Maven, execute `java -version`, write files, generate PDFs/PNGs, register temporary artifacts, or expose download links.

Public Travel Agent requests use the demo chat stream and frozen TravelPlan fixture unless Owner access is verified and the user explicitly enables the page's matching live action. Server-side API keys only mean the server is capable of live work; they do not make live model calls public.

## Build

Backend:

```bash
mvn clean package
```

Frontend:

```bash
cd frontend
npm ci
npm run build
```

Copy `frontend/dist/` to the Nginx web root, for example `/var/www/wayfinder/current`.

## Production Environment

Public demo default:

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8123
WAYFINDER_DEMO_ENABLED=true
WAYFINDER_OWNER_TOKEN=replace-with-a-long-random-secret
WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS=https://your-domain.example
TRAVEL_RAG_MODE=demo
SEARCH_PROVIDER=disabled
TAVILY_API_KEY=
PEXELS_API_KEY=
DEEPSEEK_API_KEY=
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
KNIFE4J_ENABLE=false
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO
LOGGING_LEVEL_COM_SEEWHY_SYAIAGENT=INFO
```

Owner Live Mode needs the same `WAYFINDER_OWNER_TOKEN` plus the specific providers you want to use:

```env
DEEPSEEK_API_KEY=...
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_CHAT_MODEL=deepseek-chat
SEARCH_PROVIDER=tavily
TAVILY_API_KEY=...
PEXELS_API_KEY=...
TRAVEL_RAG_MODE=lightweight
```

Use `TRAVEL_RAG_MODE=pgvector` only when PostgreSQL/PgVector is configured. Keep MCP disabled unless you are running a controlled Owner demo.

The frontend may store a submitted token in browser session storage and send it as `X-Wayfinder-Owner-Token` / `WAYFINDER_OWNER_TOKEN` cookie for `/api` requests, but `/api/travel/owner-status` confirmation only unlocks live controls. Owner verified only unlocks live controls; it does not switch pages into live mode or change default portfolio pages into live mode.

If `WAYFINDER_OWNER_TOKEN` is empty, protected live endpoints remain forbidden in production.

## Run Spring Boot

Manual start:

```bash
java -jar /opt/wayfinder/wayfinder-guild.jar
```

Example `/etc/systemd/system/wayfinder.service`:

```ini
[Unit]
Description=Wayfinder Guild Spring Boot API
After=network.target

[Service]
User=wayfinder
WorkingDirectory=/opt/wayfinder
EnvironmentFile=/etc/wayfinder/wayfinder.env
ExecStart=/usr/bin/java -jar /opt/wayfinder/wayfinder-guild.jar
Restart=on-failure
RestartSec=5
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

Commands:

```bash
sudo systemctl daemon-reload
sudo systemctl enable wayfinder
sudo systemctl start wayfinder
sudo systemctl status wayfinder
```

## Nginx

Serve Vite static files and proxy `/api` to the private backend listener:

```nginx
server {
    listen 80;
    server_name your-domain.example;

    root /var/www/wayfinder/current;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8123/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_read_timeout 300s;
    }
}
```

Put TLS in front with Certbot or your hosting provider. Do not expose port `8123` publicly; bind firewall access to `127.0.0.1`/local server only.

## Rollback

Keep releases versioned:

```text
/opt/wayfinder/releases/2026-05-13-001/wayfinder-guild.jar
/var/www/wayfinder/releases/2026-05-13-001/dist
```

Rollback steps:

```bash
sudo ln -sfn /var/www/wayfinder/releases/previous/dist /var/www/wayfinder/current
sudo cp /opt/wayfinder/releases/previous/wayfinder-guild.jar /opt/wayfinder/wayfinder-guild.jar
sudo systemctl restart wayfinder
sudo nginx -t
sudo systemctl reload nginx
```

Verify:

```bash
curl -f https://your-domain.example/api/health
curl -f https://your-domain.example/api/travel/demo-status
```

## Public vs Owner

Public visitors can access portfolio/RPG metadata, health checks, demo status, Owner status validation, demo TravelPlan, demo chat stream, demo RAG explain, and static frontend assets.

Public visitors and default page loads can click Stable Engineering Demos, but those requests call `/api/travel/manus/recorded-demo-tool` and replay real-run recordings only.

Public visitors cannot trigger live model calls, MCP, Tavily/Pexels/search, SyManus live tool loops, real demo-tool server tasks, Maven tests, runtime commands, file/PDF/PNG generation, or artifact preview/download.

Owner access is enabled by setting `WAYFINDER_OWNER_TOKEN` on the server and entering the same token in the frontend top bar. The token is stored only in the browser session/cookie, not in source code. If validation fails, the frontend clears Owner state and keeps the public demo paths usable.

With verified Owner access, live controls become available but stay off by default. Stable Engineering Demos call `/api/travel/manus/demo-tool`, Streaming Chat calls `/api/travel/chat/stream?liveMode=true`, TravelPlan calls the live structured planner, and Trace reads live trace registries only when the matching page-level Live Run / Live Chat / Live TravelPlan / Live Trace control is explicitly enabled. Each live request is still verified by the backend Owner token check; if validation fails, the frontend disables the live control and keeps the public demo path usable. Use these only for controlled demos.
