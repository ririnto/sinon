# Agent Frontmatter Patterns

Use these patterns when you need copyable frontmatter shapes for common agent roles.
Adapt the role wording and tool boundary to the actual job.

## Read-only analysis agent

See the Minimal example in `SKILL.md` for a complete read-only analysis shape (`schema-reviewer`).
The pattern below shows only the frontmatter differences for a read-only role:

```yaml
color: cyan
tools:
  - Read
  - Grep
```

Key traits: narrow inspection scope, no mutation tools, and a `description` that states the trigger condition clearly.

## Editing agent

```markdown
---
name: docs-refiner
description: >-
  Rewrite documentation files for structure, tone, and reader-ready handoff.
  Use this agent when a documentation file needs direct rewriting with a bounded writing workflow.
color: green
tools:
  - Read
  - Write
---
```

## Multi-file refactor agent

```markdown
---
name: dependency-updater
description: >-
  Update dependency versions, import paths, or API signatures across multiple files.
  Use this agent when a bounded multi-file update needs coordinated reads and targeted edits.
color: yellow
tools:
  - Read
  - Write
  - Edit
  - Grep
---
```
