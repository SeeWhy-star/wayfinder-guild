# Travel Skill Specification

Each travel skill lives in its own folder under `src/main/resources/skills/{skill-id}/SKILL.md`.

## Front Matter

Use a small metadata block at the top of each skill file:

```markdown
---
id: family-trip-planning
name: Family Trip Planning
description: Plans accessible, low-stress trips for families.
tags: family, parents, elderly, comfort
triggers: parents, family, elderly, children
priority: 80
---
```

## Body

The body should be concise operational guidance for the model:

- Planning principles.
- Budget and pacing rules.
- Must-ask missing information.
- Risk reminders.
- Output constraints.

Keep skills reusable and composable. Do not include secrets, API keys, or environment-specific values.
