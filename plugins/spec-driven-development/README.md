---
description: >-
  Plugin for an explicitly requested end-to-end specification-driven delivery lifecycle with research, approval, implementation, and verification gates.
---

# Spec-Driven Development

Spec-Driven Development is a skill-first plugin for gated specification-driven delivery in the Sinon Claude marketplace.

## Purpose

Provide reusable spec-driven development guidance that remains portable across Claude Code plugin installations.

## Included Skill

- `spec-driven-development`: run the full research, `SPEC.md`, approval, implementation, implementation-review, and verification lifecycle.

## Included Agent

- `spec-driven-development`: spec-first research, authoring, review, implementation, and completeness decisions.

The agent is a sequential leaf.
It does not spawn subagents.
Parallel work returns to the top-level session.

## How the Skill Branches

Use `spec-driven-development` only when the user explicitly asks to run or resume the end-to-end gated lifecycle through implementation and verification.
Standalone `SPEC.md` creation or review is outside this trigger.

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
        |   +-- sdd.ts          # Thin Bun/shebang CLI entrypoint
        |   +-- sdd/            # Modular runtime source and command modules
        +-- assets/
        |   +-- templates/
        |   +-- schemas/
        +-- .gitignore
```

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- `agents/` contains the Claude-facing agent trigger surface.
- `skills/spec-driven-development/scripts/sdd.ts` is the single CLI entrypoint for all SDD subcommands (`validate`, `list-frontmatter`, `get-frontmatter`, `generate-diagram`, `list-tags`).
- The entrypoint delegates runtime work to modular source under `skills/spec-driven-development/scripts/sdd/`, including command modules.
- `assets/templates/` contains scaffolds for `SPEC.md`, `RESEARCH.md`, `CONTRACT.md`, `CHANGELOG.md`, and openapi.yaml.
- `assets/schemas/` contains JSON Schema author references.
- The runtime validator enforces only the documented, selected subset of fields and does not parse these files.

## Design Principles

- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.
- Derive spec content from requirements, not from implementation.

## Offline-Capable Runtime

The packaged skill is usable offline when [Bun](https://bun.sh/) is installed on the host.
`skills/spec-driven-development/scripts/sdd.ts` is a thin Bun/shebang entrypoint that delegates to modular source under `skills/spec-driven-development/scripts/sdd/`.

Maintainers update runtime source under `skills/spec-driven-development/scripts/sdd/`.
The entrypoint remains the sole CLI surface.

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

This plugin focuses on the complete gated delivery lifecycle, not specification documents as standalone artifacts.
It does not cover:

- Git branch management
- CI/CD pipeline design
- General project management or task tracking
