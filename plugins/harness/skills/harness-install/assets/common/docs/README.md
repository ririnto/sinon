# Repository Validation and Templates

This directory contains versioned repository assets used by local agents and CI validators.

## Required repository context

| Path | Purpose |
| --- | --- |
| `CLAUDE.md` | Repository contract; fresh installs use it as the real file |
| `AGENTS.md` | Same root contract document as `CLAUDE.md`, via a symlink in one direction |
| `.claude/` | Primary directory for agents, skills, commands, and runtime state |
| `.agents/` | Symlink alias of `.claude/` so runtimes that load `.agents/` resolve to the same content |
| `ARCHITECTURE.md` | System architecture, boundaries, data flow, and validation surface |
| `docs/exec-plans/` | Contains execution plans by state, with `.gitkeep` placeholders while empty |
| `docs/generated/` | Contains generated artifacts, or `.gitkeep` until generated artifacts exist |

## Generated artifacts

`docs/generated/` is a generated-artifact location. Valid contents include API snapshots, dependency inventories, build metadata, generated architecture maps, schema dumps, report outputs, or other reviewable outputs only when the project actually generates them.

Keep `docs/generated/.gitkeep` only while the path has no real generated artifacts. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

## Optional seed files

Seed files are replaceable project-starting context. Keep them only when they match the target repository, or replace them with project-specific context and evidence.

## Development readiness

The installed contract is sufficient to start and continue agent-guided development loops when each task has a concrete goal, relevant context, and an acceptance gate. It provides the agent contract, context directories, project agents, project skills, templates, two-stage Git hook validation, native validation adapters, and CI command patterns.

The contract alone does not define product requirements, domain rules, architecture decisions, implementation source code, runtime configuration, secrets, or generated artifacts. For underspecified repositories, the first development task MUST populate the affected product spec, design doc, architecture note, and active execution plan before implementation proceeds.

## Contract evolution

The repository contract MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance. Treat the current committed contract files as the active contract. When the contract changes, update this directory, `CLAUDE.md`, validators, and templates that the change affects; record the evolution in the relevant `docs/exec-plans/` entry rather than in a separate log file.

## Template catalog

`docs/templates/` contains the current common asset templates used by this repository:

| Directory | Contents |
| --- | --- |
| `templates/agent/` | Agent definition template (`AGENT.md`) |
| `templates/ci/` | Generic validation workflow template (`validation.yaml`) |
| `templates/docs/` | Design docs, execution plans, generated artifacts, product specs, and LLM reference templates |
| `templates/skill/` | Skill and reference templates (`SKILL.md`, `reference.md`) |
| `templates/workflow/` | Workflow template (`WORKFLOW.md`) |

## Validation

Run the selected stack validation command from the repository root:

`{{validation_command}}`

The generated `pre-commit` hook runs `{{validation_command}}`. The generated `pre-push` hook also runs the same command and should match CI when CI workflows are present.
The selected stack validation command also runs `npx -y markdownlint-cli2@0.22.1` from the repository root. Markdown validation uses `.markdownlint-cli2.jsonc` plus the local custom rule at `docs/scripts/exec-plan-links.ts`, which rejects durable Markdown references to removable dated execution-plan state files. Durable references should point to `docs/exec-plans/tech-debt-tracker.md`, design docs, or product specs instead.

Markdown validation requires Node.js >=22.18.0 with npm/npx available. The first run may download `markdownlint-cli2@0.22.1` through npx.

Native validation configuration is owned by the selected stack's tooling and shared repository conventions, such as `.editorconfig` and stack-specific build/tooling config.

If installer wiring produced complex existing build/tooling integration, review generated integration blocks manually before relying on CI.
