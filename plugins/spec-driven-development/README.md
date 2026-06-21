---
description: >-
  Plugin for spec-first development workflow with research, specification authoring, review gates, and verification.
---

# Spec-Driven Development

Spec-Driven Development is a skill-first plugin for spec-first development workflow in the Sinon Claude marketplace.

## Purpose

Provide reusable spec-driven development guidance that remains portable across Claude Code plugin installations.

## Included Skill

- `spec-driven-development`: research unknowns, author `SPEC.md`, pass review gates, implement against approved spec, verify completeness.

## How the Skill Branches

Use `spec-driven-development` when the user explicitly asks to follow a spec-first workflow, write a `SPEC.md` before implementing, create a specification, or use a spec-first approach.

- `references/workflow.md` - full stage model, approval gates, review loops, and lifecycle semantics.
- `references/spec-authoring-guide.md` - writing or revising `SPEC.md` content.
- `references/research-authoring-guide.md` - writing or revising `RESEARCH.md`.
- `references/linking-guide.md` - editing `call` relationships or checking dependencies.
- `references/review-checklist.md` - Spec Review and Implementation Review.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Plugin Layout

```text
plugins/spec-driven-development/
+-- .claude-plugin/plugin.json
+-- README.md
+-- agents/
|   +-- spec-driven-development.md
+-- skills/
    +-- spec-driven-development/
        +-- SKILL.md
        +-- references/
        |   +-- workflow.md
        |   +-- spec-authoring-guide.md
        |   +-- research-authoring-guide.md
        |   +-- linking-guide.md
        |   +-- review-checklist.md
        |   +-- examples/
        +-- scripts/
        |   +-- sdd.ts          # Single CLI entrypoint and Bun toolkit
        +-- assets/
        |   +-- templates/
        |   +-- schemas/
        +-- .gitignore
```

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- `agents/` contains the Claude-facing agent trigger surface.
- `skills/spec-driven-development/scripts/sdd.ts` is the single CLI entrypoint for all SDD subcommands (`validate`, `list-frontmatter`, `get-frontmatter`, `generate-diagram`, `list-tags`).
- `assets/templates/` contains scaffolds for `SPEC.md`, `RESEARCH.md`, `CONTRACT.md`, `CHANGELOG.md`, and openapi.yaml.
- `assets/schemas/` contains JSON Schema definitions for frontmatter validation.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.
- Derive spec content from requirements, not from implementation.
- Keep manifests aligned with the actual shipped runtime surface.

## Offline-Capable Runtime

The packaged skill is usable offline when [Bun](https://bun.sh/) is installed on the host.
`skills/spec-driven-development/scripts/sdd.ts` uses a Bun shebang and Bun's built-in TypeScript runtime.

Maintainers update the runtime by editing `skills/spec-driven-development/scripts/sdd.ts`.

## Installation

When this plugin is published in the Sinon marketplace, install it with:

```sh
claude plugin install spec-driven-development@sinon
```

For current local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/spec-driven-development
```

## Scope Notes

This plugin focuses on spec-first development workflow guidance.
It does not cover:

- Git branch management
- CI/CD pipeline design
- General project management or task tracking
