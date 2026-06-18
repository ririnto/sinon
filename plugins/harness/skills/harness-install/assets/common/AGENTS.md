# Repository Contract

This repository uses versioned contracts so coding agents can work from stable project context, bounded delegation, deterministic checks, and reviewable evolution.
Fresh installs use `AGENTS.md` as the primary contract.
`CLAUDE.md` is a pointer document that imports `AGENTS.md` so Claude Code loads the same root rules.

## Entry Point

Any coding agent runtime that loads `AGENTS.md` MUST treat this document as its primary contract.
Claude Code loads the same contract through `CLAUDE.md`, which imports `AGENTS.md`.
Before making changes, an agent MUST read:

1. `AGENTS.md`
2. `ARCHITECTURE.md`
3. The relevant file under `docs/**` for the task domain

`WORKFLOW.md` holds intake, branch, validation, review, and publication decisions.

Validation commands supplied to agents MUST match `WORKFLOW.md`.

`docs/generated/` is for generated repository artifacts.
It MAY be empty and retained by `.gitkeep` until generated outputs exist.
Generated outputs SHOULD record their source command, input files, freshness, and regeneration trigger.

Contract changes MAY be made during development when the current contract no longer matches project reality.
Such changes MUST be committed as versioned files and validated before merge.

## Invariants

- The repository MUST keep agent instructions, skills, templates, documentation structure, and validation adapters in versioned files.
- `AGENTS.md` SHOULD be the primary repository contract for coding agents on fresh installs.
- `CLAUDE.md` MUST remain a pointer document that imports `AGENTS.md`.
- `.agents/` MUST be a symlink to `.claude/` so that runtimes looking up either directory resolve to the same content.
- `ARCHITECTURE.md` MUST describe system boundaries, major components, data flow, and validation surfaces.
- `WORKFLOW.md` MUST describe intake, branch naming, worktree isolation, host CLI selection, validation, review, and publication.
- Manual Git worktree paths are operator-owned and MUST be written as `<worktree-path>` in reusable instructions.
- `.claude/settings.json` MUST remain valid Claude Code project settings.
- `.gitignore` MUST include the selected install mode's ecosystem ignores plus common OS, editor, log, and temporary-file ignores.
- `.tmp/` MUST be ignored so issue, pull request, and merge request body drafts can be prepared before CLI publication.
- Direct Markdown children under `docs/` MUST stay limited to the approved root document inventory in `.markdownlint-cli2.jsonc`.
  - Add new durable documentation under `docs/design-docs/`, `docs/product-specs/`, `docs/templates/`, `docs/generated/`, or another approved subdirectory instead.
- `.claude/agents/` MUST contain specialized project agents with `name` and `description` frontmatter.
- `.claude/skills/*/SKILL.md` MUST contain focused procedures with `description` frontmatter.
- `docs/templates/` MUST contain structured templates for agents, skills, and repository documentation.
- `scripts/` MUST contain repository validation helpers and markdownlint custom rules.
- `docs/git-hooks/pre-commit` and `docs/git-hooks/pre-push` MUST remain executable and use `/usr/bin/env sh`.
- Empty required directories MUST be kept in version control with `.gitkeep` until they contain project files.
- `docs/generated/` MUST contain generated repository artifacts when a real generation source exists.
- Validation SHOULD run through the repository's native build/runtime ecosystem.
- `docs/git-hooks/pre-commit` and `docs/git-hooks/pre-push` are packaged placeholders retained as documentation records.
- Active `pre-commit` MUST include the selected stack validation command.
- Active `pre-commit` MAY include stack preflight checks before the selected validation command.
- Active `pre-push` MUST run the installed stack-specific final check.
- CI files, when present, MUST run the selected stack validation command.
- Execution plans belong in `docs/exec-plans/` with filenames of the form `yyyy-MM-dd-<slug>.md`.
- Completed plans preserve their name while moving to the completed-state location in `docs/exec-plans/`.
- Plan files in completed state MUST contain checked task lines or no task list.

## Required Repository Structure

```text
AGENTS.md
CLAUDE.md            (imports AGENTS.md)
ARCHITECTURE.md
WORKFLOW.md
.gitignore
.markdownlint-cli2.jsonc
.agents              (symlink to .claude)
.claude/
├── agents/
│   ├── implementation-agent.md
│   └── review-agent.md
├── settings.json
└── skills/
    ├── review/
    │   └── SKILL.md
    └── validate/
        └── SKILL.md
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

`docs/generated/` is reserved for artifacts produced by commands, schemas, build tools, migrations, reports, or other deterministic generation.
Keep `.gitkeep` only while the directory has no real generated artifacts.
Actual generated items SHOULD document their source command, input files, freshness, and regeneration trigger.

## Optional Seed Files

The installer may place replaceable seed files under `docs/product-specs/` and `docs/references/`.
These files are examples of where project-owned context can live.
Replace, rename, or remove them when they drift from the target repository.

## Operating Model

Humans define intent, constraints, review criteria, and acceptance gates.
Agents perform bounded implementation work and use validators as feedback loops.

Agent work MUST start by reading `AGENTS.md`, `ARCHITECTURE.md`, and the relevant `docs/**` file for the task domain.
Task prompts supply workflow decisions to delegated agents when Git, review, validation, or publication policy matters.

The contract is sufficient as the development operating surface when the project-specific context is present or explicitly created during the task.

For an underspecified repository, agents MUST first create or update the relevant product spec, design document, architecture note, and active execution plan before implementation work.

## Contract Evolution

The repository contract MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance phases.

Contract changes MUST be versioned, reviewable, and validated.
When repeated failures reveal a better policy, template, agent role, skill procedure, validation rule, generated-artifact inventory, or documentation structure, update the contract rather than relying on chat-only instructions.

The current committed contract is the active contract.
Treat target-owned project truth as the source for future contract evolution.

## Required Validation

Run the stack-specific validation command before merging changes that alter:

- `CLAUDE.md`
- `AGENTS.md`
- `ARCHITECTURE.md`
- `WORKFLOW.md`
- `.gitignore`
- `.markdownlint-cli2.jsonc`
- `docs/**`
- `.claude/agents/`
- `.claude/skills/`
- `docs/git-hooks/`
- `docs/templates/`
- `scripts/`
- `.git/hooks/` installation instructions
- CI jobs
