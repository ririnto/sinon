# Repository Layout

## Purpose

This document records the repository structure and runtime assets that support agent work.
It is the inventory for files, directories, local settings, worktree setup, and generated-artifact locations.

## Required Structure

```text
./
+-- AGENTS.md
+-- CLAUDE.md            (imports AGENTS.md)
+-- ARCHITECTURE.md
+-- WORKFLOW.md
+-- .gitignore
+-- .worktreeinclude
+-- .mcp.json
+-- .markdownlint-cli2.jsonc
+-- .codegraph/
|   +-- .gitignore
+-- .claude/
|   +-- agents/
|   |   +-- implementation.md
|   |   +-- review.md
|   |   +-- scoped-implementer.md
|   +-- settings.json
|   +-- skills/
|       +-- autonomous-execution/
|       |   +-- SKILL.md
|       +-- issue-mining/
|       |   +-- SKILL.md
|       +-- review/
|           +-- SKILL.md
+-- .agents/
|   +-- skills/         -> .claude/skills/
+-- .codex/
|   +-- agents/
|       +-- implementation.toml
|       +-- review.toml
|       +-- scoped-implementer.toml
+-- docs/
|   +-- design-docs/
|   |   +-- core-beliefs.md
|   |   +-- repository-layout.md
|   +-- exec-plans/
|   |   +-- active/
|   |   +-- completed/
|   |   +-- tech-debt-tracker.md
|   +-- generated/
|   +-- templates/
|   +-- product-specs/
|   |   +-- optional product specs such as new-user-onboarding.md
|   +-- references/
|   |   +-- optional offline references
|   +-- DESIGN.md
|   +-- FRONTEND.md
|   +-- PLANS.md
|   +-- PRODUCT_SENSE.md
|   +-- QUALITY_SCORE.md
|   +-- RELIABILITY.md
|   +-- SECURITY.md
+-- scripts/
    +-- no-box-drawing.ts
    +-- docs-root-files.ts
    +-- exec-plan-links.ts
```

## Runtime Assets

- `.claude/agents/scoped-implementer.md` defines the exhaustive-file-scope Haiku contract for exact edits and targeted validation.
- `.claude/agents/implementation.md` defines the Sonnet contract for large or cross-file work that requires discovery and integration validation.
- `.claude/agents/review.md` defines the independent review subagent contract.
- `.claude/skills/autonomous-execution/SKILL.md` defines the explicit autonomous follow-through loop.
- `.claude/skills/issue-mining/SKILL.md` defines issue investigation and optional issue-registration workflow.
- `.agents/skills/` links to `.claude/skills/`.
- `.codex/agents/scoped-implementer.toml` defines the equivalent Luna low-effort exact-scope agent.
- `.codex/agents/implementation.toml` defines the Terra medium-effort general implementation agent.
- `.codex/agents/review.toml` defines the Codex review agent.
- `.mcp.json` configures the project-local CodeGraph MCP server.
- `.codegraph/.gitignore` keeps CodeGraph local data out of Git.
- `.worktreeinclude` lists portable gitignored local inputs for Claude Code worktrees.
- `.claude/settings.json` contains the active stack worktree setup hook.

## Worktree Setup

Claude worktrees use Claude Code's default Git worktree behavior.
`.worktreeinclude` lists portable gitignored local inputs.
Examples include `.env` and `*.local.*` files.
`.gitignore` ignores `.claude/worktrees/`.

The `hooks.PostToolUse[]` entry in `.claude/settings.json` matches the `EnterWorktree` tool and runs async setup commands from the worktree directory.
All stacks run `codegraph init; codegraph index`.
Bun runs `bun install`.
uv runs `uv sync`.
Gradle runs `./gradlew help`.
Maven runs `./mvnw -q -DskipTests dependency:go-offline`.
Claude Code reports async hook command failures.

## Generated Artifacts

`docs/generated/` is reserved for deterministic generation outputs.
Examples include command outputs, schemas, build outputs, migrations, and reports.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.
