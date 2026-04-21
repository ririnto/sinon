---
title: Spec-Driven Development
description: >-
  Plugin for spec-first development workflow with research, specification authoring,
  review gates, and verification.
---

Spec-Driven Development is a skill-first plugin for spec-first development workflow in the Sinon universal marketplace.

## Purpose

Provide reusable spec-driven development guidance that remains portable across Claude Code and Codex-style plugin systems.

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

This plugin uses one shared plugin root with two runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

The actual reusable content lives beside those manifests at the plugin root.

## Plugin Layout

```text
plugins/spec-driven-development/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── README.md
├── LICENSE
├── .gitignore
├── agents/
│   └── spec-driven-development.md
├── docs/
│   └── maintainers/
│       └── runtime-architecture.md
└── skills/
    └── spec-driven-development/
        ├── SKILL.md
        ├── agents/openai.yaml
        ├── references/
        │   ├── workflow.md
        │   ├── spec-authoring-guide.md
        │   ├── research-authoring-guide.md
        │   ├── linking-guide.md
        │   ├── review-checklist.md
        │   └── examples/
        ├── scripts/
        ├── dist/
        ├── assets/
        │   ├── templates/
        │   └── schemas/
        └── .gitignore
```

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- `agents/` contains the Claude-facing agent trigger surface.
- `skills/spec-driven-development/agents/openai.yaml` contains skill-local OpenAI metadata.
- `skills/spec-driven-development/scripts/` contains stable shell entrypoints.
- `skills/spec-driven-development/dist/` contains the committed offline runtime executed by those entrypoints.
- `assets/templates/` contains scaffolds for SPEC.md, RESEARCH.md, CONTRACT.md, CHANGELOG.md, and openapi.yaml.
- `assets/schemas/` contains JSON Schema definitions for frontmatter validation.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.
- Derive spec content from requirements, not from implementation.
- Keep manifests aligned with the actual shipped runtime surface.

## Offline Runtime

The packaged skill is usable offline with Node.js 20+.
Runtime shell entrypoints execute committed artifacts under `skills/spec-driven-development/dist/` and do not perform package installation at execution time.

Maintainers rebuild the packaged runtime from the TypeScript sources in `skills/spec-driven-development/`.
Maintainer-only architecture notes live under `docs/maintainers/`, not in the skill's user-facing `references/` directory.

## Installation

When this plugin is published in the Sinon marketplace, install it with:

```bash
/plugin install spec-driven-development@sinon
```

For current local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/spec-driven-development
```

Codex-facing marketplace metadata ships through `.codex-plugin/plugin.json`, but it points to the same shared `skills/` content at this plugin root.

## Scope Notes

This plugin focuses on spec-first development workflow guidance. It does not cover:

- Git branch management
- CI/CD pipeline design
- General project management or task tracking
