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
├── agents/
│   └── spec-driven-development.md
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
        │   ├── sdd.sh
        │   ├── sdd/            # Python package backing the CLI
        │   └── pyproject.toml
        ├── assets/
        │   ├── templates/
        │   └── schemas/
        └── .gitignore
```

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- `agents/` contains the Claude-facing agent trigger surface.
- `skills/spec-driven-development/agents/openai.yaml` contains skill-local OpenAI metadata.
- `skills/spec-driven-development/scripts/sdd.sh` is the single CLI entrypoint; it dispatches all SDD subcommands (`validate`, `list-frontmatter`, `get-frontmatter`, `generate-diagram`, `list-tags`) via `uvx` against the bundled `sdd` Python package.
- `assets/templates/` contains scaffolds for SPEC.md, RESEARCH.md, CONTRACT.md, CHANGELOG.md, and openapi.yaml.
- `assets/schemas/` contains JSON Schema definitions for frontmatter validation.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only additive depth into `references/`.
- Derive spec content from requirements, not from implementation.
- Keep manifests aligned with the actual shipped runtime surface.

## Offline Runtime

The packaged skill is usable offline once [uv](https://github.com/astral-sh/uv) is installed on the host.
`skills/spec-driven-development/scripts/sdd.sh` invokes the bundled Python package through `uvx --from "${script_dir}"`, which resolves the local `pyproject.toml` and runs the `sdd` console script without reaching any remote index at execution time.

Maintainers update the runtime by editing the Python sources under `skills/spec-driven-development/scripts/sdd/` and the accompanying `pyproject.toml`.

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
