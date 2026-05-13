# Wayfinder CLI

Rust developer tooling for static quality checks in the Wayfinder Guild project.

The CLI validates portfolio and Agent metadata before a demo or deployment:

- travel Skills under `src/main/resources/skills/**/SKILL.md`
- RPG metadata under `src/main/resources/rpg/*.json`
- eval cases under `evals/travel-cases.json`
- RPG prompt templates under `src/main/resources/prompts/rpg/*.st`
- RAG documents under `src/main/resources/document/*.md`
- naming governance for Wayfinder Guild branding and file conventions

## Install / Build

From this directory:

```powershell
cargo build
```

If the current PowerShell session has not picked up Rust in `PATH`, use the full Cargo path:

```powershell
C:\Users\cycle\.cargo\bin\cargo.exe build
```

## Commands

Run all checks and print a summary:

```powershell
cargo run -- doctor --workspace ..\..
```

Windows PowerShell fallback:

```powershell
C:\Users\cycle\.cargo\bin\cargo.exe run -- doctor --workspace ..\..
```

Validate Skills:

```powershell
cargo run -- lint-skills --workspace ..\..
```

Validate RPG metadata:

```powershell
cargo run -- lint-rpg --workspace ..\..
```

Validate eval cases:

```powershell
cargo run -- lint-evals --workspace ..\..
```

Validate prompt templates:

```powershell
cargo run -- lint-prompts --workspace ..\..
```

Validate RAG documents:

```powershell
cargo run -- lint-rag-docs --workspace ..\..
```

Validate naming governance:

```powershell
cargo run -- lint-naming --workspace ..\..
```

Print counts only:

```powershell
cargo run -- summary --workspace ..\..
```

## What It Checks

`lint-skills`

- required front matter fields: `id`, `name`, `description`, `tags`, `triggers`, `priority`
- skill `id` must match the containing directory name
- `priority` must be numeric

`lint-rpg`

- required files: `world.json`, `projects.json`, `skills.json`, `modules.json`, `profile.json`
- basic object/array shape
- quick route paths are non-empty
- area/NPC `moduleIds` references exist in `modules.json`

`lint-evals`

- each case has `id`, `name`, and `input`
- `expectedSkills` and `disallowedTools` are arrays

`lint-prompts`

- `.st` templates are not empty
- placeholder angle brackets are basically balanced

`lint-rag-docs`

- Markdown files exist under `src/main/resources/document/*.md`
- each file has YAML front matter with `id`, `title`, `tags`, `updated`, and `source_type`
- document `id` matches the filename stem
- `tags` is non-empty
- `source_type` is `curated-demo` or `local-note`
- body content is long enough to be useful for retrieval

`lint-naming`

- blocks old public brand and boundary names in active source, frontend, and docs
- warns when JSON `id` values are not `kebab-case`
- warns when docs are not `UPPER-KEBAB-CASE.md`
- warns when Vue page files are not `*Page.vue`, with documented compatibility exceptions

## Interview Note

This tool is intentionally small and static. It shows that Wayfinder Guild is not just a UI demo: the project metadata, prompts, evals, and skills have a repeatable quality gate that can run locally or in CI.
