---
title: Git Workflow
description: >-
  Overview of the Git Workflow plugin, its merged skill, and template-aware GitHub/GitLab review workflow guidance.
---

Git Workflow is a shared, skill-first plugin for repository-state-driven commit and review publication work in the Sinon Claude marketplace.

## Purpose

- Provide reusable Git workflow guidance that remains portable across Claude Code plugin installations.
- Keep the portable value surface in `skills/`, with the common path centered on real repository state rather than generic commit, PR, or MR text.
- Ground guidance in staged diffs, working-tree status, recent history, and repository templates instead of ad-hoc wording.
- Keep commit, pull request, and merge request guidance procedural, template-backed, and focused on quality gates before publication.

## Included Skill

- `git-change-publication`: commit readiness checks, staged-change hygiene, Conventional Commit selection, commit body drafting, fallback review-body generation, and template-aware GitHub/GitLab body preservation guidance.

## Included Agents

Two runtime agents for crafting commit messages and pull request bodies.

- `commit-message-architect`: draft and refine Conventional Commit messages from staged changes, with rationale-focused bodies and scope validation.
- `pr-body-architect`: write pull request descriptions that preserve GitHub templates while clearly summarizing changes, impact, and testing guidance.

## How the Skill Branches by Host

Use `git-change-publication` when the job is to decide commit readiness, draft a Conventional Commit from the real diff, and prepare hosted review text. The skill owns the detailed common path and host-specific reference routing.

## Runtime Model

This plugin uses one shared plugin root with a thin Claude manifest:

- `.claude-plugin/plugin.json`

The actual reusable content lives beside the manifest at the plugin root.
Agents are shipped from the plugin-root `agents/` directory and are intentionally not declared in the manifest because plugin manifest rules prohibit an `agents` key.

## Plugin Layout

```text
plugins/git-workflow/
├── .claude-plugin/plugin.json
├── README.md
├── agents/
│   ├── commit-message-architect.md
│   └── pr-body-architect.md
└── skills/
    └── git-change-publication/
        ├── SKILL.md
        └── references/
            ├── github-pull-request-templates.md
            └── gitlab-merge-request-templates.md
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata.
- `agents/` holds the commit-message and pull-request body drafting agents.
- `skills/git-change-publication/SKILL.md` is the common path for commit readiness, split-vs-single decisions, Conventional Commit drafting, fallback hosted review text, and validation phrasing.
- `skills/git-change-publication/references/` holds additive host-specific depth only.

## Shipped Surfaces

- The plugin ships one reusable Git skill under `skills/`.
- The plugin ships two agents (`commit-message-architect`, `pr-body-architect`) for repository-state-driven change publication.
- The plugin does not ship commands, hooks, MCP servers, LSP servers, or custom runtime data surfaces.
- Host-specific GitHub and GitLab mechanics stay inside the skill references rather than the manifests or plugin root README.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only host-specific additive depth into `references/`.
- Derive commit, PR, and MR body text from actual repository state, not from generic placeholders.
- Keep manifests thin and let marketplace catalogs describe distribution.

## Installation

Install from Sinon:

```sh
/plugin install git-workflow@sinon
```

For Claude Code local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/git-workflow
```

## Scope Notes

This plugin intentionally focuses on portable, repository-state-driven change publication guidance. It does not cover:

- custom MCP servers
- hooks
- custom repository GitHub or GitLab review workflow design beyond preserving an existing template during PR/MR drafting
- Git history rewriting or force-push strategy
- merge-conflict resolution
- general GitHub or GitLab issue, CI, or project-management workflows
