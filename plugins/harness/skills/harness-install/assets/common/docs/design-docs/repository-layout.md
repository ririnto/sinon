# Repository Layout

## Purpose

This document records the repository structure and runtime assets that support agent work.
It inventories these repository surfaces:

- Files and directories.
- Local settings.
- Worktree setup.
- Generated-artifact locations.

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
|   +-- settings.json
|   +-- skills/
|       +-- autonomous-execution/
|       |   +-- SKILL.md
|       +-- issue-mining/
|           +-- SKILL.md
+-- .agents/
|   +-- skills/         -> .claude/skills/
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

- `.claude/skills/` contains `autonomous-execution` and `issue-mining`.
Each `SKILL.md` owns its named workflow.
- `.agents/skills/` links to `.claude/skills/`.
- Runtime inventory includes the two project skills, target policy, and host-provided native agents.
- `WORKFLOW.md` owns target orchestration, review, validation, and completion policy.
- The installer always copies one target-facing `WORKFLOW.md`.
- `--ci-host` selects CI files only.
- Existing target workflow variants remain unmanaged and unchanged.
- `.mcp.json` configures the project-local CodeGraph MCP server.
- `.codegraph/.gitignore` keeps CodeGraph local data out of Git.
- `.worktreeinclude` lists portable gitignored local inputs for worktrees.
- `.claude/settings.json` contains the Claude Code adapter for stack worktree setup.

## Worktree Setup

Each host uses its native Git worktree behavior.
`.worktreeinclude` lists portable gitignored local inputs.
Examples include `.env` and `*.local.*` files.
`.gitignore` ignores `.claude/worktrees/`.

The installed Claude Code settings hook matches `EnterWorktree` and runs async setup commands from the worktree directory.
All stacks run `codegraph init` followed by `codegraph index`.
Bun runs `bun install`.
uv runs `uv sync`.
Gradle runs `./gradlew help`.
Maven runs `./mvnw -q -DskipTests dependency:go-offline`.
Claude Code reports async hook command failures.

## Generated Artifacts

`docs/generated/` is reserved for deterministic generation outputs.
Examples include command outputs, schemas, build outputs, migrations, and reports.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.
