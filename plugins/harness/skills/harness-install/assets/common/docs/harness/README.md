# Repository Harness

This directory contains versioned harness assets used by local agents and CI validators.

## Required repository context

| Path | Purpose |
| --- | --- |
| `CLAUDE.md` | Repository harness contract; fresh installs use it as the real file |
| `AGENTS.md` | Same root contract document as `CLAUDE.md`, via a symlink in one direction |
| `.claude/` | Primary directory for agents, skills, commands, and runtime state |
| `.agents/` | Symlink alias of `.claude/` so runtimes that load `.agents/` resolve to the same content |
| `ARCHITECTURE.md` | System architecture, boundaries, data flow, and validation surface |
| `docs/exec-plans/active/` | Contains active execution plans, or `.gitkeep` until active plans exist |
| `docs/exec-plans/completed/` | Contains completed execution plans, or `.gitkeep` until completed plans exist |
| `docs/generated/` | Contains generated artifacts, or `.gitkeep` until generated artifacts exist |

## Generated artifacts

`docs/generated/` is a generated-artifact location. Valid contents include API snapshots, dependency inventories, build metadata, generated architecture maps, schema dumps, report outputs, or other reviewable outputs — only when the project actually generates them.

Keep `docs/generated/.gitkeep` only while the path has no real generated artifacts. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

## Optional seed files

Files such as `docs/product-specs/new-user-onboarding.md` are replaceable seeds. Keep them only when they match the target repository, or replace them with project-specific context and evidence.

## Harness-only development readiness

The installed harness is sufficient to start and continue agent-guided development loops when each task has a concrete goal, relevant context, and an acceptance gate. It provides the agent contract, context directories, project agents, project skills, templates, two-stage Git hook validation, native validation adapters, and CI command patterns.

The harness alone does not define product requirements, domain rules, architecture decisions, implementation source code, runtime configuration, secrets, or generated artifacts. For underspecified repositories, the first development task MUST populate the affected product spec, design doc, architecture note, and active execution plan before implementation proceeds.

## Harness evolution

The repository harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance. Treat the current committed harness files as the active contract. When the harness changes, update this directory, `CLAUDE.md`, validators, and templates as needed; record the evolution in the relevant `docs/exec-plans/` entry rather than in a separate log file.

## Validation

Run the command for the repository stack:

- Gradle harness validation: `./gradlew harnessCheck`, or `gradle harnessCheck` when this repository uses system Gradle without a wrapper
- Gradle final check: `./gradlew check`, or `gradle check` when this repository uses system Gradle without a wrapper
- Maven: `mvn -q -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check`
- uv: `uv run --script docs/harness/uv/harness_check.py`
- bun: `bun --install=fallback run docs/harness/bun/harness-check.ts`
- shell: `sh docs/harness/shell/harness-check.sh`

The generated Gradle `pre-commit` hook runs `harnessCheck`; non-Gradle `pre-commit` hooks check lightweight harness-rule compliance only. The generated `pre-push` hook runs the selected final check command and should match CI when CI snippets are present.

Run validation from the repository root. The uv, bun, and Maven validators bind the current working directory as the target root. Native validators support the installed `docs/harness/manifest.json` schema and compare the list fields written by this harness.

## Manifest schema

`docs/harness/manifest.json` is validated against `docs/harness/manifest.schema.json`. Each stack ships a self-contained schema; there is no shared base or code-tier schema across stacks. The schema declares the rule keys that the installed stack's validator recognises.

If the installer wired Gradle into a complex existing `settings.gradle(.kts)`, review the resulting plugin management and composite build blocks manually before relying on `check` in CI.
