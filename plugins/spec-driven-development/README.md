---
title: Spec-Driven Development
description: >-
  Plugin for spec-first development workflow with research, specification authoring,
  review gates, and verification.
---

Spec-Driven Development is a skill-first plugin for spec-first development workflow in the Sinon Claude marketplace.

## Purpose

Provide reusable spec-driven development guidance that remains portable across Claude Code plugin installations.

## Included Skill

- `spec-driven-development`: research unknowns, author SPEC.md, pass review gates, implement against approved spec, verify completeness.

## How the Skill Branches

Use `spec-driven-development` when the user explicitly asks to follow a spec-first workflow, write a SPEC.md before implementing, create a specification, or use a spec-first approach.

- `references/workflow.md` — full stage model, approval gates, review loops, and lifecycle semantics.
- `references/spec-authoring-guide.md` — writing or revising `SPEC.md` content.
- `references/research-authoring-guide.md` — writing or revising `RESEARCH.md`.
- `references/linking-guide.md` — editing `call` relationships or checking dependencies.
- `references/review-checklist.md` — Spec Review and Implementation Review.

## Runtime Model

This plugin uses one shared plugin root with a Claude manifest:

- `.claude-plugin/plugin.json`

The manifest declares `./skills/`. Agents remain in the plugin-root `agents/` directory and are described here rather than declared in `.claude-plugin/plugin.json` because this repository's manifest rules prohibit an `agents` key.

## Plugin Layout

```text
plugins/spec-driven-development/
├── .claude-plugin/plugin.json
├── README.md
├── agents/
│   └── spec-driven-development.md
└── skills/
    └── spec-driven-development/
        ├── SKILL.md
        ├── references/
        │   ├── workflow.md
        │   ├── spec-authoring-guide.md
        │   ├── research-authoring-guide.md
        │   ├── linking-guide.md
        │   ├── review-checklist.md
        │   └── examples/
        ├── scripts/
        │   └── sdd.py          # Single CLI entrypoint and Python toolkit
        ├── assets/
        │   ├── templates/
        │   └── schemas/
        └── .gitignore
```

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- `agents/` contains the Claude-facing agent trigger surface.
- `skills/spec-driven-development/scripts/sdd.py` is the single CLI entrypoint for all SDD subcommands (`validate`, `list-frontmatter`, `get-frontmatter`, `generate-diagram`, `list-tags`).
- `assets/templates/` contains scaffolds for SPEC.md, RESEARCH.md, CONTRACT.md, CHANGELOG.md, and openapi.yaml.
- `assets/schemas/` contains JSON Schema definitions for frontmatter validation.
- The plugin does not ship plugin commands, hooks, MCP servers, LSP servers, or custom runtime data surfaces.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.
- Derive spec content from requirements, not from implementation.
- Keep manifests aligned with the actual shipped runtime surface.

## Offline-Capable Runtime

The packaged skill is usable offline only when [uv](https://github.com/astral-sh/uv) is installed and the Python interpreter plus dependencies declared by the bundled CLI entrypoint are already cached or otherwise available locally.
`skills/spec-driven-development/scripts/sdd.py` uses a `uv run` shebang and PEP 723 metadata to declare the required Python dependencies.
If uv must download Python or dependencies, network access may be required unless those inputs are already cached.

Maintainers update the runtime by editing `skills/spec-driven-development/scripts/sdd.py` and its dependency metadata.

## Installation

When this plugin is published in the Sinon marketplace, install it with:

```sh
/plugin install spec-driven-development@sinon
```

For current local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/spec-driven-development
```

## Scope Notes

This plugin focuses on spec-first development workflow guidance. It does not cover:

- Git branch management
- CI/CD pipeline design
- General project management or task tracking
