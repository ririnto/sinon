---
description: >-
  Overview of the Workspace Workflow plugin: skills for worktree management, working-tree hygiene, merge and rebase strategies, commit conventions, and change description composition, plus workspace agents for everyday workflow.
metadata:
  reference:
    Claude Code plugins:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Workspace Workflow

Workspace Workflow provides Git worktree, preservation, merge, rebase, commit-message, and change-description skills.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| git-worktree-management | Create, list, remove, and repair isolated git worktrees for parallel branch work | "create a worktree", "work on multiple branches at once", "remove a stale worktree" |
| working-tree-hygiene | Inspect and preserve working-tree, index, stash, and upstream state for the requested Git operation | "check working-tree state", "stash changes", "verify branch sync", "prepare to push" |
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

Claude Code loads this package through the Sinon marketplace entry and its default component paths.
Codex uses `.codex-plugin/plugin.json` to load skills from the default `skills/` directory.
Claude Code also loads the three plugin-root agents listed above.
The listed plugin-root agents remain Claude Code surfaces.

## Plugin Layout

```text
plugins/workspace-workflow/
+-- .codex-plugin/plugin.json
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
