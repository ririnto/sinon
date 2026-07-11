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
+-- WORKFLOW.github.md
+-- WORKFLOW.gitlab.md
+-- WORKFLOW.none.md
+-- .gitignore
+-- .worktreeinclude
+-- .mcp.json
+-- .markdownlint-cli2.jsonc
+-- .codegraph/
|   +-- .gitignore
+-- .claude/
|   +-- agents/
|   |   +-- implementer.md
|   |   +-- reviewer.md
|   |   +-- scoped-implementer.md
|   |   +-- validation-executor.md
|   +-- settings.json
|   +-- skills/
|       +-- autonomous-execution/
|       |   +-- SKILL.md
|       +-- issue-mining/
|           +-- SKILL.md
+-- .agents/
|   +-- skills/         -> .claude/skills/
+-- .codex/
|   +-- agents/
|       +-- implementer.toml
|       +-- reviewer.toml
|       +-- scoped-implementer.toml
|       +-- validation-executor.toml
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

- `.claude/agents/` contains four installed agents: `implementer`, `scoped-implementer`, `reviewer`, and `validation-executor`.
  Each file owns its bounded leaf contract.
- `.claude/skills/` contains two installed skills: `autonomous-execution` and `issue-mining`.
  Each `SKILL.md` owns its named workflow.
- `.agents/skills/` links to `.claude/skills/`.
- `.codex/agents/` contains Codex counterparts for the four installed agents.
- `WORKFLOW.md` owns target orchestration, review, validation, and completion policy.
- `WORKFLOW.github.md`, `WORKFLOW.gitlab.md`, and `WORKFLOW.none.md` are independently
  installed record-host addenda.
  Run the installer to copy all three.
  Select the addendum for each work item through `WORKFLOW.md`, then open only the selected
  addendum.
  The addenda contain host-specific record procedures; `WORKFLOW.md` remains the canonical
  policy.
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
