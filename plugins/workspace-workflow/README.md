---
description: >-
  Overview of the Workspace Workflow plugin: skills for worktree management, working-tree hygiene, merge and rebase strategies, commit conventions, and change description composition, plus workspace agents for everyday workflow.
---

# Workspace Workflow

Workspace Workflow is a shared, skill-first plugin for end-to-end Git workspace and change-integration workflow in the Sinon Claude marketplace.
It covers isolated worktree work, working-tree discipline, history integration (merge or rebase), commit message conventions, and Git-contained change-description composition under one coherent plugin.

## Purpose

- Provide reusable guidance for managing parallel development using git worktrees without switching HEAD.
- Preserve working-tree and staging intent with operation-specific checks rather than requiring a clean tree for every task.
- Document merge and rebase strategies with concrete commands, decision tables, and conflict-handling procedures.
- Standardize commit messages with Conventional Commits and align change description bodies across Git repositories.
- Coordinate decisions across these skills through focused workspace agents.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| git-worktree-management | Create, list, remove, and repair isolated git worktrees for parallel branch work | "create a worktree", "work on multiple branches at once", "remove a stale worktree" |
| working-tree-hygiene | Inspect, stash, and maintain clean working-tree state before starting work or publishing | "check if the tree is clean", "stash changes", "verify branch sync", "prepare to push" |
| git-merge-strategies | Choose and execute merge mode (fast-forward, no-ff, squash, octopus) with conflict and rerere patterns | "merge a feature branch", "resolve a merge conflict", "decide between ff and no-ff" |
| git-rebase-strategies | Run interactive rebase, autosquash, and `--onto` reapplication while protecting shared history | "squash commits", "reorder history", "rebase onto a new base", "recover a failed rebase" |
| commit-convention | Author Conventional Commits messages with type, scope, body, footer, and split decisions | "write a commit message", "normalize history", "split a change into commits" |
| change-description | Compose Git-contained change descriptions with rationale, validation, review focus, and merge handoff | "describe a change", "write review context", "prepare a merge handoff" |

Load the skill for the requested Git operation.
Compose skills only when the task needs several operations, preserving existing work and each action's authorization boundary.

## Included Agents

- workspace-architect: coordinates decisions across the workspace-workflow skills, sequences operations when a task spans worktree, hygiene, history integration, and change description, and enforces team conventions consistently.
- commit-message-architect: drafts Conventional Commit messages from staged changes and evaluates commit cohesion and readiness.
- change-description-architect: drafts Git-contained change descriptions that state real change intent and merge context.

All three agents are read-only leaves for workspace decisions.
They may load workspace skills but do not delegate, mutate Git state, or publish.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Plugin Layout

```text
plugins/workspace-workflow/
+-- .claude-plugin/plugin.json
+-- LICENSE
+-- README.md
+-- agents/
|   +-- workspace-architect.md
|   +-- commit-message-architect.md
|   +-- change-description-architect.md
+-- skills/
    +-- commit-convention/
    |   +-- SKILL.md
    +-- git-merge-strategies/
    |   +-- SKILL.md
    +-- git-rebase-strategies/
    |   +-- SKILL.md
    +-- git-worktree-management/
    |   +-- SKILL.md
    +-- change-description/
    |   +-- SKILL.md
    +-- working-tree-hygiene/
        +-- SKILL.md
```

## Shipped Surfaces

- Six reusable skills under `skills/` cover the full workspace-to-integration workflow.
- Three agents under `agents/` cover workflow coordination, commit-message drafting, and change description body drafting.

## Design Principles

- Prefer one coherent user job per skill.
  - Route cross-skill decisions through `workspace-architect`.
- Keep each `SKILL.md` focused on its operation, authority boundaries, relevant command choices, and completion evidence.
- Derive guidance from real Git behavior and repository state rather than generic tutorials.
- Keep change descriptions self-contained and derive review and merge context from repository evidence.
- Treat shared history as a contract: rebasing or force-pushing published branches requires explicit, team-acknowledged intent.
- Keep `plugin.json` thin and let the skills and agents carry the reusable substance.

## Installation

Install from Sinon:

```sh
claude plugin install workspace-workflow@sinon
```

For Claude Code local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/workspace-workflow
```

## Scope Notes

This plugin focuses on the Git-driven workspace and change-integration loop.
It intentionally does not cover:

- Code review judgement on the merits of the change.
  - The change description sets up the review context.
  - Reviewers and other plugins evaluate it.
- Language- or framework-specific build, test, or release tooling (see language plugins such as `java`, `kotlin`, or framework plugins such as `spring`, `reactor`).
- Custom CI/CD pipeline templates beyond minimal command snippets used in repository guidance.
- Hooks, MCP servers, or repository-level automation outside the workspace workflow itself.
