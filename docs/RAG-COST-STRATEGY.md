# RAG Cost Strategy

Wayfinder Guild keeps RAG visible in the public portfolio without making PgVector or a cloud database part of the default production path.

## Modes

Configure the backend with:

```env
TRAVEL_RAG_MODE=demo
```

Supported values:

- `demo`: stable explainable RAG sample. No VectorStore, PgVector, database, or extra retrieval cost.
- `lightweight`: metadata-aware keyword retrieval over `src/main/resources/document/*.md`. Good for public demos that should show real local snippets, document titles, tags, source type, and update dates.
- `pgvector`: optional live VectorStore mode for local deep demos or Owner Live Mode.

## Recommended Public Production

```env
WAYFINDER_DEMO_ENABLED=true
TRAVEL_RAG_MODE=demo
```

This keeps `/api/travel/rag/explain` stable and cheap. The frontend shows the current RAG mode, so cost-control mode is not presented as a system error.

The public site should default to `demo` or `lightweight`. Both modes avoid cloud vector databases, Redis, and managed database cost while still showing the RAG engineering loop: query, rewrite, retrieve, inspect documents, and explain the answer.

## Lightweight Public Alternative

```env
WAYFINDER_DEMO_ENABLED=false
TRAVEL_RAG_MODE=lightweight
```

Use this when the public site should retrieve from bundled Markdown without a vector database. The curated demo library under `src/main/resources/document/` is intentionally small, inspectable, and interview-friendly. It demonstrates retrieval quality through document metadata and practical travel planning language instead of relying on infrastructure spend.

## Owner Live PgVector

```env
WAYFINDER_DEMO_ENABLED=false
TRAVEL_RAG_MODE=pgvector
DEEPSEEK_API_KEY=...
DB_URL=jdbc:postgresql://127.0.0.1:5432/sy_ai_agent
DB_USERNAME=sy_ai_agent
DB_PASSWORD=...
```

PgVector remains an enhancement path for local deep demos or Owner Live Mode. It is not required for the public production path. If VectorStore creation or retrieval fails, the RAG service logs a warning and falls back to lightweight Markdown retrieval instead of returning 500.

## Why This Does Not Burn Money

The default path does not provision a cloud vector database, does not require Redis, and does not require a managed Postgres/PgVector instance. `demo` uses stable sample retrieval. `lightweight` reads packaged Markdown resources from the application and scores them in memory. That is enough to show a complete RAG product loop on a public portfolio while keeping PgVector available for controlled local demonstrations.

## Redis

Redis is intentionally deferred for v0.1.0. The current public launch can run without shared cache infrastructure. Redis remains useful later for rate limiting, trace caching, session sharing, and cross-instance state, but adding it now would increase operational cost and deployment complexity without solving the immediate PgVector cost problem.
