---
description: Repository-wide guidance for the Sinon plugin marketplace.
---

# Repository Guidelines

Sinon publishes Claude Code plugins and portable Agent Skills.

## Project Structure

- `plugins/` contains publishable packages.
- `.claude-plugin/marketplace.json` lists local package roots and external catalog registrations.
- `docs/agent-references/` contains repository authoring conventions.
- `scripts/` contains repository validation checks.
- `rules/` contains repository Markdown custom lint rules.
- `.github/` contains GitHub repository automation configuration.

Preserve existing named top-level components, each with one documented responsibility.
Adding, removing, or moving a component MUST update its architecture, dependencies, consumers, and relevant documentation.
Do not add legacy parallel surfaces or compatibility shims without an external contract.

## Authoring

Write all repository guidance and agent-to-agent communication in English.

## Task References

Use the relevant sections of [repository conventions](docs/agent-references/repository-conventions.md) for shell, source, documentation, or configuration edits.
For TypeScript source under `scripts/` or `rules/`, use [TypeScript conventions](docs/agent-references/typescript.md).
Read supporting references when their subject applies, not as a prerequisite to every edit.

## Completion And Checks

Make the smallest complete change that meets the acceptance criteria.
Continue through implementation, relevant proof, and proportional diff review.
Preserve unrelated work and report a precise blocker when required evidence or authority is missing.
When guidance blocks progress, name its file, quote the relevant instruction, and distinguish the requirement from your interpretation.
Use the existing TypeScript and Bun commands.
Choose checks for changed behavior and explicit acceptance criteria; reuse passing evidence for unaffected checks.
Run `bun install` after authorized dependency changes.
The repository-wide check is `bun run check`; its named `check:*` commands support narrower validation.
Report exact commands, exit codes, and any required check that could not run.

## Authority And Publication

Explicit user instructions override this guidance within system, safety, and tool constraints.
Read-only inspection does not authorize changing the inspected resource.
Continue authorized edits and checks without asking again for each step.
The user-facing root session owns integration and publication, and grants any delegated Git actions explicitly.
Publish only capability-scoped plugin components.
Keep general orchestration profiles out of plugins.

## Security And Configuration

Do not edit credentials, local configuration, caches, or vendored files unless the task names them.
Keep task rationale and evidence self-contained in Git, without work-item identifiers, review URLs, or local environment details.
Use repository-relative paths and portable examples in committed content and messages.
Review changes that cross command, filesystem, network, credential, or publication boundaries for safety risks.
