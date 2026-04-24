---
title: Harness Engineering
description: >-
  Overview of the Harness Engineering plugin, its agent-first repository skill, and the autonomous agents that enforce structure, review code, and drive end-to-end work.
---

Harness Engineering is a shared, skill-first plugin for designing and maintaining agent-first repositories with progressive disclosure, architecture enforcement, and entropy management.

## Purpose

- Provide reusable guidance for structuring repositories that AI agents can navigate through progressive disclosure rather than dense global context.
- Keep architectural invariants machine-checkable with custom linters and structural tests rather than review-time opinion.
- Track documentation drift, stale cross-links, and execution-plan freshness before they compound into unmanageable entropy.
- Drive autonomous implementation, review, and structure enforcement through a focused set of agents that share the same repository-first mental model.

## Included Skill

- `harness-engineering`: repository layout, CLAUDE.md as a table of contents, layer-dependency enforcement, doc gardening, and technical-debt tracking for agent-generated codebases.

## Included Agents

- `architecture-guard`: mechanical architecture enforcement, layer-dependency auditing, structural-test validation, and taste-invariant checks.
- `code-reviewer`: confidence-filtered review for bugs, security issues, and project-convention drift on changed code.
- `doc-gardener`: report-only entropy cleanup covering documentation drift, stale cross-links, quality grades, and execution-plan freshness.
- `e2e-driver`: autonomous end-to-end execution for reproducing a bug, implementing a fix or feature, and validating results with observability evidence.
- `spec-writer`: execution-plan authoring, progress tracking, and completion handling under `docs/exec-plans/`.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

Both manifests point to the same shared plugin root content, while the actual reusable surfaces live under `skills/` and `agents/` beside those manifests.

## Plugin Layout

```text
plugins/harness-engineering/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── README.md
├── agents/
│   ├── architecture-guard.md
│   ├── code-reviewer.md
│   ├── doc-gardener.md
│   ├── e2e-driver.md
│   └── spec-writer.md
└── skills/
    └── harness-engineering/
        ├── SKILL.md
        └── references/
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata with the declared `skills` entry point and shared plugin metadata.
- `.codex-plugin/plugin.json` carries thin Codex-facing marketplace metadata pointing at the same shared plugin root.
- `skills/harness-engineering/SKILL.md` holds the common path for repository layout, progressive disclosure, architecture enforcement, and entropy management.
- `skills/harness-engineering/references/` holds additive depth for bootstrap, CI integration, agent legibility, and repository-knowledge structure.
- `agents/*.md` define the autonomous agents with explicit triggers, bounded tools, and self-contained system prompts.

## Design Principles

- Prefer one coherent user job per skill and one coherent responsibility per agent.
- Keep the common path self-sufficient inside `SKILL.md` and move only on-demand depth into `references/`.
- Enforce layer direction and structural invariants mechanically rather than through human-only review.
- Treat documentation, execution plans, and quality grades as first-class entropy surfaces that must stay aligned with the code.

## Installation

Install from the Sinon marketplace:

```bash
/plugin install harness-engineering@sinon
```

For Claude Code local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/harness-engineering
```

Codex-facing marketplace metadata ships through `.codex-plugin/plugin.json`, and it refers to the same plugin root content at this path.

## Scope Notes

This plugin intentionally focuses on agent-first repository structure, mechanical enforcement, entropy management, and autonomous execution. It does not cover:

- language- or framework-specific coding style
- release management or deployment tooling
- general-purpose CI pipeline authoring beyond integrating the harness's structural checks
