# Wayfinder Guild Verification Guide

Use this guide before a release candidate, deployment, or interview rehearsal.

## 1. Backend Tests

Run the default fast and stable backend tests from the repository root:

```powershell
mvn test
```

Expected result:

```text
BUILD SUCCESS
```

Notes:

- The default Maven test profile is intended to avoid slow or environment-sensitive integration checks.
- Keep real API keys out of test logs.
- If a failure depends on live model/network access, move it behind an explicit integration profile.

## 2. Frontend Build

Run the Vue/Vite production build:

```powershell
cd frontend
npm run build
```

Expected result:

```text
built successfully
```

Known MVP warning:

- Phaser may make the main bundle larger than Vite's default chunk warning threshold.
- This is acceptable for the current portfolio MVP. Future work can lazy-load the RPG map route.

## 3. Wayfinder CLI Doctor

Run the Rust static quality gate for Skills, RPG metadata, eval cases, and prompt templates:

```powershell
cd tools/wayfinder-cli
cargo test
cargo run -- doctor --workspace ..\..
```

If the current shell has not picked up Rust in `PATH`, use the full Cargo path on this machine:

```powershell
C:\Users\cycle\.cargo\bin\cargo.exe test
C:\Users\cycle\.cargo\bin\cargo.exe run -- doctor --workspace ..\..
```

Expected doctor output:

```text
[OK] skills
[OK] rpg
[OK] evals
[OK] prompts
[OK] rag docs
[OK] naming
```

## 4. MCP Submodule Tests

Run the optional image-search MCP module tests:

```powershell
cd sy-image-search-mcp
mvn test
```

Notes:

- Live Pexels tests should stay disabled unless intentionally run with `PEXELS_API_KEY`.
- The release candidate should not depend on a personal local API key.

## 5. Recommended Full RC Verification

From a clean shell:

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

For interview rehearsal, also smoke test:

- `/`
- `/profile`
- `/projects`
- `/skills`
- `/evals`
- `/travel-agent`
- `/rag-library`
- `/trace`
- `/architecture`
