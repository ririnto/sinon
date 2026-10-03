---
description: >-
  Plugin for an explicitly requested end-to-end specification-driven delivery lifecycle with research, approval, implementation, and verification gates.
metadata:
  reference:
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Spec-Driven Development

Spec-Driven Development provides a gated specification lifecycle through implementation and verification.

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
- `references/authoring-guide.md` - writing or revising `SPEC.md` content.
- `references/research-authoring-guide.md` - writing or revising `RESEARCH.md`.
- `references/linking-guide.md` - editing `call` relationships or checking dependencies.
- `references/review-checklist.md` - Spec Review and Implementation Review.

## Runtime Model

This package uses `.claude-plugin/plugin.json` in Claude Code and `.codex-plugin/plugin.json` in Codex.
Codex loads the skill from the default `skills/` directory.
Claude Code also loads the agent under `agents/`.
The listed agent remains a Claude Code surface.

## Plugin Layout

```text
plugins/spec-driven-development/
+-- .claude-plugin/plugin.json
+-- .codex-plugin/plugin.json
+-- README.md
+-- package.json            # Standalone Node.js runtime dependencies
+-- pnpm-workspace.yaml     # Runtime dependency catalog
+-- pnpm-lock.yaml          # Standalone installation lockfile
+-- agents/
|   +-- spec-driven-development.md
+-- skills/
    +-- spec-driven-development/
        +-- SKILL.md
        +-- references/
        |   +-- workflow.md
        |   +-- authoring-guide.md
        |   +-- research-authoring-guide.md
        |   +-- linking-guide.md
        |   +-- review-checklist.md
        |   +-- examples/
        +-- scripts/
        |   +-- sdd.ts          # Thin Node.js CLI entrypoint
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

## Offline-Capable Runtime

The packaged skill requires Node.js.
Install its runtime dependencies with pnpm before offline use:

```sh
pnpm --dir /path/to/installed/spec-driven-development install --prod --frozen-lockfile
```

The package declares `tsx` for TypeScript execution, `yaml` for YAML parsing, and `fast-sort` for immutable sorting.
The package's catalog and lockfile support installation outside the Sinon repository.
These dependencies belong to the installed plugin and do not depend on the consuming repository's packages.
After installation, commands work offline.

Run the CLI from the consuming repository:
Resolve the package root two levels above the installed skill directory.
Replace the example package path with that absolute path.

```sh
PLUGIN_ROOT="/absolute/path/to/installed/plugin"
SKILL_ROOT="${PLUGIN_ROOT}/skills/spec-driven-development"
node --import "${PLUGIN_ROOT}/node_modules/tsx/dist/loader.mjs" "${SKILL_ROOT}/scripts/sdd.ts" validate ./spec
```

The absolute loader path resolves the installed plugin's dependencies while preserving the current working directory.
`skills/spec-driven-development/scripts/sdd.ts` delegates to modular source under `skills/spec-driven-development/scripts/sdd/`.

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
