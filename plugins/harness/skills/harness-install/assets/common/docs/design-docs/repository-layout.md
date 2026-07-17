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
- `.agents/skills/` links to `.claude/skills/` so hosts without a Claude-style skill loader read the same skills as plain documents.
- Runtime inventory includes the two project skills, target policy, and host-provided native agents.
- `WORKFLOW.md` owns target orchestration, review, validation, and completion policy.
- The installer always copies one target-facing `WORKFLOW.md`.
- `--ci-host` selects CI files only.
- Existing target workflow variants remain unmanaged and unchanged.
- `.mcp.json` configures the project-local CodeGraph MCP server.
- `.codegraph/.gitignore` keeps CodeGraph local data out of Git.
- `.worktreeinclude` lists portable gitignored local inputs for worktrees.
- `.claude/settings.json` is a host adapter: it wires stack worktree setup for Claude Code only.
Other hosts run the same setup commands through their own configuration or manually; the workflow does not depend on this adapter.

## Worktree Setup

Each host uses its native Git worktree behavior.
`.worktreeinclude` lists portable gitignored local inputs.
Examples include `.env` and `*.local.*` files.
`.gitignore` ignores `.claude/worktrees/`.

The setup commands per stack are host-independent:
all stacks run `codegraph init` followed by `codegraph index`.
Bun runs `bun install`.
uv runs `uv sync`.
Gradle runs `./gradlew help`.
Maven runs `./mvnw -q -DskipTests dependency:go-offline`.

On Claude Code, the installed settings hook matches `EnterWorktree` and runs these commands asynchronously from the worktree directory, reporting async hook command failures.
On other hosts, run the stack setup command once per new worktree.

## Generated Artifacts

`docs/generated/` is reserved for deterministic generation outputs.
Examples include command outputs, schemas, build outputs, migrations, and reports.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.
