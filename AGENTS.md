---
description: Repository-wide guidance for the Sinon plugin marketplace.
---

# Repository Guidelines

Sinon publishes Claude Code plugins and portable Agent Skills.
`CLAUDE.md` files are exact pointers and must not become duplicate rule files.

## Project Structure

- `plugins/` contains publishable packages.
- `.claude-plugin/marketplace.json` lists package roots.
- `scripts/` contains repository validation checks.
- Preserve existing named top-level components.
- Each top-level component MUST have one documented responsibility.
- Adding, removing, or moving a top-level component MUST update architecture, dependencies, consumers, and relevant documentation.
- Do not add legacy parallel surfaces or compatibility shims without an external contract.

Use [repository conventions](docs/agent-references/repository-conventions.md) for source, script, documentation, and configuration changes.

## Build, Test, and Development Commands

Run `bun install` after dependency changes.
The repository-wide check command is `bun run check`.

## Change Discipline

Make the smallest change that satisfies the acceptance criteria and preserve unrelated work.
Use the existing TypeScript and Bun commands.
Report a precise blocker when required evidence or authority is missing.

## Commit and Publication

The user-facing root session owns integration and publication.
Publish only capability-scoped plugin components; keep general orchestration profiles out of plugins.

## Security and Configuration

Do not edit credentials, local configuration, caches, or vendored files unless the task names them.
Review changes that cross command, filesystem, network, credential, or publication boundaries for safety risks.
