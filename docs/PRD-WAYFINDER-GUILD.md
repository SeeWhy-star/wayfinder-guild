# PRD: Wayfinder Guild

## 1. Project Background

Wayfinder Guild is the public portfolio product, with `wayfinder-guild` used for build and package-facing names.

The project started as an Agentic Travel Planning Backend built with Spring Boot, Java 21, Spring AI, and DeepSeek. It has grown into a productized AI engineering showcase that demonstrates Agent orchestration, RAG, Skills, Eval, Trace, Guardrails, and deployment readiness through a warm cosmic travel RPG experience.

The site is intended for personal website launch, interview demonstrations, and portfolio review.

## 2. Target Users

- Recruiters and hiring managers evaluating backend / full-stack / Agent engineering capability.
- Interviewers who want to understand architecture decisions quickly.
- Engineers interested in Spring AI, RAG, Agent orchestration, and production guardrails.
- The project owner, who needs a stable demo flow for live interviews and public portfolio traffic.

## 3. User Value

- Visitors can understand the project without reading code first.
- Interviewers can see real backend capabilities through interactive pages.
- The project owner can demonstrate product thinking, backend architecture, frontend experience, DevOps readiness, and quality gates in one coherent story.
- The portfolio is not just a resume page; it is an explorable AI engineering system.

## 4. Core Scenarios

1. Portfolio first impression
   - Visitor opens `/`.
   - Visitor sees Wayfinder Guild as an RPG-style AI engineering town.
   - Visitor can navigate through map buildings and quick routes.

2. Agent capability demo
   - Visitor opens `/travel-agent`.
   - Visitor generates or views a structured `TravelPlan`.
   - Visitor sees itinerary, budget, risks, loaded Skills, and trace entry.

3. Explainability demo
   - Visitor opens `/rag-library`.
   - Visitor inspects original query, rewritten query, retrieved documents, and answer.
   - Visitor opens `/trace` to inspect Agent execution steps.

4. Quality and reliability demo
   - Visitor opens `/skills` to see skill matching.
   - Visitor opens `/evals` to review evaluation cases and rules.
   - Visitor opens `/architecture` to understand the system boundaries.

5. Interview walkthrough
   - Owner follows the demo script and smoke route.
   - Owner can switch between Demo Mode and live model mode depending on network/model stability.

## 5. Functional Scope

Included in v0.1.0:

- RPG-style Wayfinder Guild homepage.
- Vue capability pages for profile, projects, skills, evals, travel agent, RAG library, trace, and architecture.
- Spring Boot APIs for travel chat, structured TravelPlan, RAG explain, trace, RPG metadata, Skills matching, and eval metadata.
- Demo Mode fallbacks for stable demonstrations.
- Rust Wayfinder CLI for static metadata quality checks.
- Deployment, verification, launch, and release governance documentation.

## 6. Non-goals

- No user login or account system.
- No payments, analytics dashboard, or CMS.
- No multiplayer RPG mechanics.
- No battle, inventory, or quest progression system.
- No guarantee that live model responses are instant or deterministic.
- No production-grade observability platform in v0.1.0.
- No real-time admin console for editing metadata.

## 7. Acceptance Criteria

Functional acceptance:

- `/` renders the Wayfinder Guild homepage and remains navigable.
- Core pages are reachable: `/profile`, `/projects`, `/skills`, `/evals`, `/travel-agent`, `/rag-library`, `/trace`, `/architecture`.
- `/api/rpg/world` and related RPG APIs return usable metadata.
- `/api/travel/plan` returns a structured TravelPlan in live mode or demo mode.
- `/api/travel/rag/explain` returns explainable RAG data or graceful fallback.
- `/api/travel/trace/{chatId}` returns trace events or demo trace fallback.

Quality acceptance:

- `mvn test` passes.
- `cd frontend && npm run build` passes.
- `cd tools/wayfinder-cli && cargo test` passes.
- `cd tools/wayfinder-cli && cargo run -- doctor --workspace ..\..` reports OK.
- No real API keys are committed.
- Deployment docs are sufficient for Nginx + Spring Boot JAR + systemd + HTTPS.

Experience acceptance:

- Live model latency is communicated through loading states.
- Backend unavailable states do not white-screen the site.
- Demo Mode can support a stable interview route.

## 8. Demo Mode / Owner Live Mode Strategy

Demo Mode:

- Controlled by `WAYFINDER_DEMO_ENABLED=true`.
- Used for public portfolio demos, unstable networks, or cost-controlled interviews.
- Provides predictable TravelPlan, trace, RAG explain, and eval sample result output.

Owner Live Mode:

- Controlled by `WAYFINDER_DEMO_ENABLED=false` with valid model/API configuration.
- Used when the owner wants to show real model behavior.
- Requires DeepSeek key, billing controls, and tolerance for 30-90 second structured generation latency.

Mode selection principle:

- Use Demo Mode for first impression reliability.
- Use Owner Live Mode for deeper technical interviews where live Agent behavior matters.
