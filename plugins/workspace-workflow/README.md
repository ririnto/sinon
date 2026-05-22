---
title: Workspace Workflow
description: >-
  Overview of the Workspace Workflow plugin: skills for worktree management, working-tree hygiene, merge and rebase strategies, commit conventions, and PR/MR composition, plus a workspace architect agent and slash commands for everyday workflow.
---

Workspace Workflow is a shared, skill-first plugin for end-to-end Git workspace and change-publication workflow in the Sinon Claude marketplace. It covers isolated worktree work, working-tree discipline, history integration (merge or rebase), commit message conventions, and pull or merge request composition under one coherent plugin.

## Purpose

- Provide reusable guidance for managing parallel development using git worktrees without switching HEAD.
- Establish hygiene discipline for clean working-tree state before any merge, rebase, commit, or publish step.
- Document merge and rebase strategies with concrete commands, decision tables, and conflict-handling procedures.
- Standardize commit messages with Conventional Commits and align PR/MR bodies across GitHub and GitLab.
- Coordinate decisions across these skills through a single architect agent and convenience slash commands.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| git-worktree-management | Create, list, remove, and repair isolated git worktrees for parallel branch work | "create a worktree", "work on multiple branches at once", "remove a stale worktree" |
| working-tree-hygiene | Inspect, stash, and maintain clean working-tree state before starting work or publishing | "check if the tree is clean", "stash changes", "verify branch sync", "prepare to push" |
| git-merge-strategies | Choose and execute merge mode (fast-forward, no-ff, squash, octopus) with conflict and rerere patterns | "merge a feature branch", "resolve a merge conflict", "decide between ff and no-ff" |
| git-rebase-strategies | Run interactive rebase, autosquash, and `--onto` reapplication while protecting shared history | "squash commits", "reorder history", "rebase onto a new base", "recover a failed rebase" |
| commit-convention | Author Conventional Commits messages with type, scope, body, footer, and split decisions | "write a commit message", "normalize history", "split a change into commits" |
| pr-mr-convention | Compose GitHub PR or GitLab MR with disciplined title, structured body, labels, and review checklist | "open a PR", "write an MR body", "decide draft vs ready", "pick labels and reviewers" |

These skills compose into the everyday loop: prepare a clean working tree (optionally inside a fresh worktree), shape commits with `commit-convention`, integrate with the right `git-merge-strategies` or `git-rebase-strategies` mode, and publish through `pr-mr-convention`.

## Included Agents

- workspace-architect: coordinates decisions across the workspace-workflow skills, sequences operations when a task spans worktree, hygiene, history integration, and publication, and enforces team conventions consistently.
- commit-message-architect: drafts Conventional Commit messages from staged changes and evaluates commit cohesion and readiness.
- pr-body-architect: drafts pull request or merge request bodies that preserve repository templates and describe real change intent.

## Included Commands

Slash commands are prefixed with `workspace-` to avoid collision with shorter, plugin-agnostic names that may exist in other plugins (for example, `commit`, `pr`, or `mr`).

- workspace-commit: compose a Conventional Commits-style commit message from staged or HEAD diff, following `commit-convention` rules.
- workspace-pr: compose a pull or merge request body from the current branch commits, following `pr-mr-convention` rules.
- workspace-worktree: create, list, switch, or clean up git worktrees through `git-worktree-management` patterns.

## Runtime Model

This plugin uses one shared plugin root with a Claude manifest that publishes the skill and command surfaces:

- `.claude-plugin/plugin.json` declares `./skills/` and `./commands/`.

The `agents/` directory ships at the plugin root and is described in this README. It is intentionally not declared in `plugin.json` because the Claude Code plugin manifest schema does not support an `agents` key.

## Plugin Layout

```text
plugins/workspace-workflow/
├── .claude-plugin/plugin.json
├── LICENSE
├── README.md
├── agents/
│   ├── workspace-architect.md
│   ├── commit-message-architect.md
│   └── pr-body-architect.md
├── commands/
│   ├── workspace-commit.md
│   ├── workspace-pr.md
│   └── workspace-worktree.md
└── skills/
    ├── commit-convention/
    │   └── SKILL.md
    ├── git-merge-strategies/
    │   └── SKILL.md
    ├── git-rebase-strategies/
    │   └── SKILL.md
    ├── git-worktree-management/
    │   └── SKILL.md
    ├── pr-mr-convention/
    │   └── SKILL.md
    └── working-tree-hygiene/
        └── SKILL.md
```

## Shipped Surfaces

- Six reusable skills under `skills/` cover the full workspace-to-publication workflow.
- One agent under `agents/` (workspace-architect) coordinates cross-skill decisions.
- Three slash commands under `commands/` (workspace-commit, workspace-pr, workspace-worktree) wrap the most frequent workflow entry points.
- The plugin does not ship hooks, MCP servers, LSP servers, output styles, monitors, or custom runtime data surfaces.

## Design Principles

- Prefer one coherent user job per skill; route cross-skill decisions through `workspace-architect`.
- Keep the common path self-sufficient inside each `SKILL.md` with concrete commands, invariants, and decision tables.
- Derive guidance from real Git behavior and repository state rather than generic tutorials.
- Treat shared history as a contract: rebasing or force-pushing published branches requires explicit, team-acknowledged intent.
- Keep `plugin.json` thin and let the skills, the agent, and the commands carry the reusable substance.

## Installation

Install from Sinon:

```sh
/plugin install workspace-workflow@sinon
```

For Claude Code local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/workspace-workflow
```

## Scope Notes

This plugin focuses on the Git-driven workspace and change-publication loop. It intentionally does not cover:

- Code review judgement on the merits of the change (the PR/MR body sets it up; reviewers and other plugins evaluate it).
- Language- or framework-specific build, test, or release tooling (see language plugins such as `java`, `kotlin`, or framework plugins such as `spring`, `reactor`).
- Custom CI/CD pipeline templates beyond minimal command snippets used in PR/MR descriptions.
- Hooks, MCP servers, or repository-level automation outside the workspace workflow itself.
