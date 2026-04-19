---
title: Git Workflow
description: >-
  Overview of the Git Workflow plugin, its merged skill, and template-aware GitHub/GitLab review workflow guidance.
---

Git Workflow is a shared, skill-first plugin for repository-state-driven commit and review publication work in the Sinon universal marketplace.

## Purpose

- Provide reusable Git workflow guidance that remains portable across Claude Code and Codex-style plugin systems.
- Keep the portable value surface in `skills/`, with the common path centered on real repository state rather than generic commit, PR, or MR text.
- Ground guidance in staged diffs, working-tree status, recent history, and repository templates instead of ad-hoc wording.
- Keep commit, pull request, and merge request guidance procedural, template-backed, and focused on quality gates before publication.

## Included Skill

- `git-change-publication`: commit readiness checks, staged-change hygiene, Conventional Commit selection, commit body drafting, fallback review-body generation, and template-aware GitHub/GitLab body preservation guidance.

## How the Skill Branches by Host

Use `git-change-publication` when the job is to decide commit readiness, draft a Conventional Commit from the real diff, and prepare hosted review text.

- Stay in `SKILL.md` for the common path: inspect the repo state, decide split vs single commit, draft the Conventional Commit, and prepare a generic hosted-review fallback body.
- Open the GitHub reference when you need exact pull request template detection, named template handling, or GitHub-specific preservation rules.
- Open the GitLab reference when you need exact merge request template detection, `Default.md` handling, or GitLab variable/quick-action preservation rules.

Typical workflow:

1. Inspect `git status`, staged diffs, unstaged diffs, and recent commit history.
2. Decide whether the current changes represent one logical commit or should be split.
3. Draft a Conventional Commit subject and a short rationale-focused body from the real diff.
4. Branch to the host-specific reference only when exact GitHub or GitLab template mechanics matter.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

The actual reusable content lives beside those manifests at the plugin root.

## Plugin Layout

```text
plugins/git-workflow/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── README.md
└── skills/
    └── git-change-publication/
        ├── SKILL.md
        ├── agents/openai.yaml
        └── references/
            ├── github-pull-request-templates.md
            └── gitlab-merge-request-templates.md
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata.
- `.codex-plugin/plugin.json` carries thin Codex-facing marketplace metadata plus the `./skills/` entry point.
- `skills/git-change-publication/SKILL.md` is the common path for commit readiness, split-vs-single decisions, Conventional Commit drafting, fallback hosted review text, and validation phrasing.
- `skills/git-change-publication/references/` holds additive host-specific depth only.
- `skills/git-change-publication/agents/openai.yaml` is the thin trigger surface for the same skill, not a second workflow.

## Shipped Surfaces

- The plugin ships one reusable skill under `skills/`.
- The plugin does not ship commands, hooks, MCP servers, LSP servers, or custom runtime data surfaces.
- Host-specific GitHub and GitLab mechanics stay inside the skill references rather than the manifests or plugin root README.

## Design Principles

- Prefer one coherent user job per skill.
- Keep the common path self-sufficient inside `SKILL.md` and move only host-specific additive depth into `references/`.
- Derive commit, PR, and MR body text from actual repository state, not from generic placeholders.
- Keep manifests thin and let marketplace catalogs describe distribution.

## Installation

Install from Sinon:

```bash
/plugin install git-workflow@sinon
```

For Claude Code local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/git-workflow
```

Codex-facing marketplace metadata ships through `.codex-plugin/plugin.json`, but it points to the same shared `skills/` content at this plugin root.

## Scope Notes

This plugin intentionally focuses on portable, repository-state-driven change publication guidance. It does not cover:

- custom MCP servers
- hooks
- custom repository-local GitHub or GitLab review workflow design beyond preserving an existing template during PR/MR drafting
- Git history rewriting or force-push strategy
- merge-conflict resolution
- general GitHub or GitLab issue, CI, or project-management workflows
