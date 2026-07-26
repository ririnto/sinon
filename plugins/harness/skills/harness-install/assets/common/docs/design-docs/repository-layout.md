# Repository Layout

## Purpose

This document describes repository surface categories and representative runtime assets that support agent work.

- Files and directories.
- Local settings.
- Worktree setup.
- Generated-artifact locations.

## Representative Installed Structure

```text
./
+-- ARCHITECTURE.md
+-- CONTRIBUTING.md
+-- .gitignore
+-- .worktreeinclude
+-- .mcp.json
+-- .markdownlint-cli2.jsonc
+-- .claude/
|   +-- settings.json
|   +-- skills/
|       +-- autonomous-execution/
|       |   +-- SKILL.md
|       +-- issue-mining/
|           +-- SKILL.md
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
+-- rules/
    +-- no-box-drawing.ts
    +-- exec-plan-links.ts
```

## Runtime Assets

- `.claude/skills/` contains `autonomous-execution` and `issue-mining`.
Each `SKILL.md` owns its named workflow.
- Runtime assets include project skills, target policy, and host-provided native agents.
- `CONTRIBUTING.md` owns the shared contribution lifecycle, validation, review, handoff, and approval-gated remote effects.
- `rules/` holds the custom markdownlint rules referenced by `.markdownlint-cli2.jsonc`.
- A selected environment bundle may add `.github/` or `.gitlab/` resources.
- Environment CI resources remain inert until the selected tool configuration activates them.
- `.mcp.json` configures the project-local CodeGraph MCP server.
- `.worktreeinclude` lists portable gitignored local inputs for worktrees.
- `.claude/settings.json` is a host adapter: it wires stack worktree setup for Claude Code only.
  Other hosts run the same setup commands through their own configuration or manually.
  The workflow does not depend on this adapter.

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
Examples include command outputs, schemas, build outputs, and reports.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.
