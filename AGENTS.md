---
description: Repository-wide guidance for the Sinon plugin marketplace.
---

# Repository Guidelines

Sinon publishes Claude Code plugins and portable Agent Skills.
Read this file, `README.md`, each nearer `AGENTS.md`, and the affected plugin README before changing a package.
A closer `AGENTS.md` overrides this one for its subtree.
`CLAUDE.md` files are exact pointers and must not become duplicate rule files.

## Project Structure

- `plugins/` contains publishable packages.
- `.claude-plugin/marketplace.json` lists package roots.
- `scripts/` contains repository validation checks.

Open [repository conventions](docs/agent-references/repository-conventions.md) when editing source, scripts, docs, or config.

## Build, Test, and Development Commands

Run `bun ci` after dependency changes.
Run `bun run check` before a repository-wide handoff.

## Coding Style and Testing

Make the smallest change that satisfies observable acceptance criteria.
Preserve unrelated work.
Match local style and remove only newly introduced orphans.
Test executable behavior at the smallest useful scope.
Use unit tests by default, integration tests only for behavior that requires a real process, database, network, or equivalent boundary, and end-to-end tests only for non-duplicated core user journeys.
Do not turn prose guidance into source-string, heading, word-count, or file-presence tests.
Use TypeScript and Bun commands already present in the package.
Report a precise blocker when required evidence or authority is missing.

## Commit and Publication

Inspect `git status --short --branch` and the intended diff before staging.
The user-facing root session owns integration and publication.
Do not publish a general orchestration profile as a plugin component.

## Security and Configuration

Do not edit credentials, local configuration, caches, or vendored files unless the task names them.
Review changed hooks, scripts, MCP, LSP, settings, and packaged assets for command, filesystem, network, credential, and publication risks.
