# Repository Harness Contract

This repository uses a versioned harness so coding agents can work from stable project context, bounded delegation, deterministic checks, and reviewable evolution.

## Invariants

- The repository MUST keep agent instructions, skills, templates, documentation structure, and validation adapters in versioned files.
- `AGENTS.md` MUST be the primary repository harness contract for coding agents.
- `CLAUDE.md` MUST remain a Claude Code entry point and MUST refer to `AGENTS.md`. The canonical pattern is `AGENTS.md` as a symlink to `CLAUDE.md` (or vice versa) so both names resolve to one document.
- `ARCHITECTURE.md` MUST describe system boundaries, major components, data flow, and validation surfaces.
- `.claude/agents/` MUST contain specialized project agents with `name` and `description` frontmatter.
- `.claude/skills/*/SKILL.md` MUST contain focused procedures with `description` frontmatter.
- `docs/harness/templates/` MUST contain structured templates for agents, skills, workflows, CI integration, and repository documentation.
- `docs/harness/git-hooks/pre-commit` and `docs/harness/git-hooks/pre-push` MUST remain executable and use `/usr/bin/env sh`.
- Empty required directories MUST be kept in version control with `.gitkeep` until they contain project files.
- `docs/generated/` MUST contain actual generated repository artifacts when they exist. `docs/generated/db-schema.md` is an example of a possible generated database schema summary and MUST NOT be treated as a required scaffold file.
- Validation SHOULD run through the repository's native build/runtime ecosystem.
- `docs/harness/git-hooks/pre-commit` MUST follow the stack-specific intermediate gate: Gradle runs `harnessValidate`, and non-Gradle stacks run lightweight harness-rule compliance.
- `docs/harness/git-hooks/pre-push` SHOULD run the same final check command used by CI; for Gradle this is `check`.
- CI SHOULD run the same final check command used by generated pre-push.

## Required Repository Structure

```text
AGENTS.md            (symlink to CLAUDE.md, or its own file)
ARCHITECTURE.md
CLAUDE.md
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
├── harness/
│   ├── README.md
│   ├── manifest.json
│   ├── evolution-log.md
│   ├── git-hooks/
│   │   ├── pre-commit
│   │   └── pre-push
│   └── templates/
├── product-specs/
│   └── optional product specs such as new-user-onboarding.md
├── references/
│   └── optional replaceable reference seeds
├── DESIGN.md
├── FRONTEND.md
├── PLANS.md
├── PRODUCT_SENSE.md
├── QUALITY_SCORE.md
├── RELIABILITY.md
└── SECURITY.md
```

`docs/generated/` is reserved for artifacts produced by commands, schemas, build tools, migrations, reports, or other deterministic generation. Keep `.gitkeep` only while the directory has no real generated artifacts. `docs/generated/db-schema.md` is only an example of a possible output and MUST NOT be treated as a required file. Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.

## Optional Seed Files

The harness may install replaceable seed files under `docs/product-specs/` and `docs/references/`. These files are examples of where project-owned context can live; replace, rename, or remove them when they do not match the target repository.

## Operating Model

Humans define intent, constraints, review criteria, and acceptance gates. Agents perform bounded implementation work and use validators as feedback loops.

Agent work MUST start by reading `AGENTS.md`, `ARCHITECTURE.md`, and the relevant `docs/**` file for the task domain.

The harness is sufficient as the development operating surface when the project-specific context is present or explicitly created during the task. The scaffold MUST NOT be treated as a substitute for missing product requirements, source-of-truth schemas, tests, implementation code, runtime configuration, secrets, or domain references.

For an underspecified repository, agents MUST first create or update the relevant product spec, design document, architecture note, and active execution plan before implementation work.

## Harness Evolution

The repository harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance phases.

Harness changes MUST be versioned, reviewable, and validated. When repeated failures reveal a better policy, template, agent role, skill procedure, validation rule, generated-artifact inventory, or documentation structure, update the harness rather than relying on chat-only instructions.

The current committed harness is the active contract. Do not treat the original plugin defaults as permanent.

## Required Validation

Run the stack-specific harness validation command before merging changes that alter:

- `AGENTS.md`
- `CLAUDE.md`
- `ARCHITECTURE.md`
- `docs/**`
- `.claude/agents/`
- `.claude/skills/`
- `docs/harness/`
- `.git/hooks/` installation instructions
- CI harness jobs

# Claude Code Entry Point

Claude Code MUST use `AGENTS.md` as the primary Repository Harness Contract.

Before making changes, read:

1. `AGENTS.md`
2. `ARCHITECTURE.md`
3. The relevant file under `docs/**` for the task domain

Validation MUST use the stack-specific command documented in `docs/harness/README.md`.

`docs/generated/` is for generated repository artifacts. It MAY be empty and retained by `.gitkeep` until generated outputs exist. `docs/generated/db-schema.md` is only an example of a possible generated artifact and MUST NOT be treated as a required file.

Harness changes MAY be made during development when the current harness no longer matches project reality. Such changes MUST be committed as versioned files and validated before merge.
