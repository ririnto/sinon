---
description: Repository-wide guidance for the Sinon plugin marketplace.
---

# Repository Guidelines

Sinon publishes Claude Code plugins and portable Agent Skills. Read this file, `README.md`, each nearer `AGENTS.md`, and the affected plugin README before changing a package. A closer `AGENTS.md` overrides this one for its subtree. `CLAUDE.md` files are exact pointers and must not become duplicate rule files.

## Project Structure

- `plugins/` contains publishable packages.
- `.claude-plugin/marketplace.json` lists package roots.
- `.codex/agents/` contains Codex counterparts for shared agents.
- `scripts/` contains repository validation and routing checks.

Open `plugins/AGENTS.md` for package, documentation, and script rules. Open the affected plugin's `AGENTS.md` for its local contract. Open `plugins/harness/skills/harness-install/assets/common/WORKFLOW.md` for delegation lifecycle and publication decisions.

## Build, Test, and Development Commands

Run `bun ci` after dependency changes. Run `bun run check` before a repository-wide handoff. Use `plugins/harness/scripts/plugin-self-check.ts` for Harness runtime or packaged-asset changes. Run `claude plugin validate plugins/<plugin>` after changing a plugin package.

## Coding Style and Testing

Make the smallest change that satisfies observable acceptance criteria. Preserve unrelated work. Match local style, remove only newly introduced orphans, and add a focused regression for a behavior fix. Use TypeScript and Bun commands already present in the package. Report a precise blocker when required evidence or authority is missing.

## Commit and Publication

Inspect `git status --short --branch` and the intended diff before staging. The user-facing root session owns integration and publication. Do not package a general orchestration profile. It delegates exploration, implementation, documentation, audit, review, and validation to bounded leaves. Writers receive disjoint ownership and worktrees; failed or missing workers block fan-in. When a dispatch API exposes `fork_turns`, new workers use `fork_turns: "none"` with self-contained context. Do not infer this field for CLI dispatch.

## Security and Configuration

Do not edit credentials, local configuration, caches, or vendored files unless the task names them. Review changed hooks, scripts, MCP, LSP, settings, and packaged assets for command, filesystem, network, credential, and publication risks.
