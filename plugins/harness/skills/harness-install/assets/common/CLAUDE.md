# Repository Contract

This repository uses versioned contracts so coding agents can work from stable project context, bounded delegation, deterministic checks, and reviewable evolution.
Fresh installs use `CLAUDE.md` as the primary contract with `AGENTS.md` as a symlink alias.
Existing AGENTS-only repositories may preserve `AGENTS.md` as the real file and add `CLAUDE.md` as the symlink alias.
In either orientation, both filenames MUST resolve to this single document, regardless of which agent runtime is running.

## Entry Point

Any coding agent runtime that loads `AGENTS.md` or `CLAUDE.md` MUST treat this document as its primary contract.
Before making changes, an agent MUST read:

1. `CLAUDE.md` (this document, also resolvable as `AGENTS.md`)
2. `ARCHITECTURE.md`
3. The relevant file under `docs/**` for the task domain

Validation MUST use the stack-specific command documented in `docs/README.md`.

`docs/generated/` is for generated repository artifacts.
It MAY be empty and retained by `.gitkeep` until generated outputs exist.
Fake placeholder files MUST NOT be added.

Contract changes MAY be made during development when the current contract no longer matches project reality.
Such changes MUST be committed as versioned files and validated before merge.

## Invariants

- The repository MUST keep agent instructions, skills, templates, documentation structure, and validation adapters in versioned files.
- `CLAUDE.md` SHOULD be the primary repository contract for coding agents on fresh installs.
- `AGENTS.md` and `CLAUDE.md` MUST resolve to the same document, via a symlink in one direction.
- `.agents/` MUST be a symlink to `.claude/` so that runtimes looking up either directory resolve to the same content.
- `ARCHITECTURE.md` MUST describe system boundaries, major components, data flow, and validation surfaces.
- `.claude/agents/` MUST contain specialized project agents with `name` and `description` frontmatter.
- `.claude/skills/*/SKILL.md` MUST contain focused procedures with `description` frontmatter.
- `docs/templates/` MUST contain structured templates for agents, skills, workflows, CI integration, and repository documentation.
- `docs/git-hooks/pre-commit` and `docs/git-hooks/pre-push` MUST remain executable and use `/usr/bin/env sh`.
- Empty required directories MUST be kept in version control with `.gitkeep` until they contain project files.
- `docs/generated/` MUST contain actual generated repository artifacts when they exist.
  - Fake placeholder files MUST NOT be added.
- Validation SHOULD run through the repository's native build/runtime ecosystem.
- `docs/git-hooks/pre-commit` and `docs/git-hooks/pre-push` are packaged placeholders until installation renders active stack hooks.
- Active `pre-commit` MUST include the selected stack validation command: `{{validation_command}}`.
- Active `pre-commit` MAY include stack preflight checks before the selected validation command.
- Active `pre-push` MUST run the generated stack-specific final check.
- CI files, when present, MUST run the selected stack validation command.
- Execution plans belong in `docs/exec-plans/` with filenames of the form `yyyy-MM-dd-<slug>.md`.
- Completed plans preserve their name while moving to the completed-state location in `docs/exec-plans/`.
- Plan files in completed state MUST NOT contain any unchecked `- [ ]` task lines.

## Required Repository Structure

```text
CLAUDE.md
AGENTS.md            (same document as CLAUDE.md via symlink in one direction)
ARCHITECTURE.md
WORKFLOW.md
docs/
├── design-docs/
│   └── core-beliefs.md  (or real design docs)
├── exec-plans/
│   ├── active/
│   │   └── .gitkeep  (or real active plans named yyyy-MM-dd-<slug>.md)
│   ├── completed/
│   │   └── .gitkeep  (or completed plans moved here)
│   └── tech-debt-tracker.md
├── generated/
│   └── .gitkeep  (or real generated artifacts)
├── git-hooks/
│   ├── pre-commit
│   └── pre-push
├── templates/
├── scripts/
├── product-specs/
│   └── optional product specs such as new-user-onboarding.md
├── references/
│   └── optional replaceable reference seeds
├── README.md
├── DESIGN.md
├── FRONTEND.md
├── PLANS.md
├── PRODUCT_SENSE.md
├── QUALITY_SCORE.md
├── RELIABILITY.md
└── SECURITY.md
```

`docs/generated/` is reserved for artifacts produced by commands, schemas, build tools, migrations, reports, or other deterministic generation.
Keep `.gitkeep` only while the directory has no real generated artifacts.
Do not add fake placeholder files.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.

## Optional Seed Files

The installer may place replaceable seed files under `docs/product-specs/` and `docs/references/`.
These files are examples of where project-owned context can live.
Replace, rename, or remove them when they do not match the target repository.

## Operating Model

Humans define intent, constraints, review criteria, and acceptance gates.
Agents perform bounded implementation work and use validators as feedback loops.

Agent work MUST start by reading `CLAUDE.md`, `ARCHITECTURE.md`, and the relevant `docs/**` file for the task domain.

The contract is sufficient as the development operating surface when the project-specific context is present or explicitly created during the task.

For an underspecified repository, agents MUST first create or update the relevant product spec, design document, architecture note, and active execution plan before implementation work.

## Contract Evolution

The repository contract MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance phases.

Contract changes MUST be versioned, reviewable, and validated.
When repeated failures reveal a better policy, template, agent role, skill procedure, validation rule, generated-artifact inventory, or documentation structure, update the contract rather than relying on chat-only instructions.

The current committed contract is the active contract.
Do not treat the original plugin defaults as permanent.

## Required Validation

Run the stack-specific validation command before merging changes that alter:

- `CLAUDE.md`
- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/**`
- `.claude/agents/`
- `.claude/skills/`
- `docs/git-hooks/`
- `docs/templates/`
- `docs/scripts/`
- `docs/README.md`
- `.git/hooks/` installation instructions
- CI jobs
