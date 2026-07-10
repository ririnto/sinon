# Repository Guidelines

This file is the installed repository contract. A closer `AGENTS.md` overrides it for its subtree. `CLAUDE.md` points here exactly. Open `WORKFLOW.md` for lifecycle detail and `ARCHITECTURE.md` plus relevant `docs/` files for task context.

## Project Structure

Use `.claude/agents/` and `.claude/skills/` only for their named task. Keep durable documents under `docs/` and use `docs/templates/` for new project artifacts. Do not edit credentials, local environment files, caches, or vendored code unless the user names them.

## Build, Test, and Development Commands

Run the validation command selected by `WORKFLOW.md` before completion. Report changed files, evidence, skipped checks, and risks. Use the project’s existing setup and development commands rather than inventing replacements.

## Coding Style and Testing

Keep changes scoped to the request and local patterns. Preserve worktree changes from other workers. Use English Markdown headings, language-tagged fences, and ASCII tree markers. Update related documentation, templates, agents, skills, validation helpers, and generated metadata when behavior changes.

## Commit and Publication

The user-facing root session is the sole orchestrator and publisher. It delegates exploration, implementation, documentation, audit, review, and validation to leaves. Use Opus/Sol medium for top-level orchestration, Sonnet/Terra medium for substantive leaves, and Haiku/Luna low for bounded mechanical work. Give writers disjoint scopes and worktrees, require full fan-in, return findings to owners, and re-review fixes. When a dispatch API exposes `fork_turns`, set `fork_turns: "none"` and provide self-contained context. Agents do not delegate or publish.

## Security and Configuration

Keep Codex depth at `max_depth = 1`. Review hooks, scripts, MCP, settings, and generated assets for command, filesystem, network, credential, and publication risk.
