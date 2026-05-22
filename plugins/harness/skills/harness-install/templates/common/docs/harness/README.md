# Repository Harness

This directory contains versioned harness assets used by local agents and CI validators.

## Required repository context

| Path | Purpose |
| --- | --- |
| `AGENTS.md` | Primary repository harness contract |
| `CLAUDE.md` | Claude Code entry point that references `AGENTS.md` |
| `ARCHITECTURE.md` | System architecture, boundaries, data flow, and validation surface |
| `docs/exec-plans/active/` | Contains active execution plans, or `.gitkeep` until active plans exist |
| `docs/exec-plans/completed/` | Contains completed execution plans, or `.gitkeep` until completed plans exist |
| `docs/generated/` | Contains generated artifacts, or `.gitkeep` until generated artifacts exist |

## Generated artifacts

`docs/generated/` is a generated-artifact location, not a required database-documentation location. A path such as `docs/generated/db-schema.md` is valid only when the repository actually generates a database schema summary. Other generated artifacts MAY include API snapshots, dependency inventories, build metadata, generated architecture maps, report outputs, or other reviewable outputs.

Keep `docs/generated/.gitkeep` only while the path has no real generated artifacts. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

## Optional seed files

Files such as `docs/product-specs/new-user-onboarding.md` and `docs/references/*-llms.txt` are replaceable seeds. Keep them only when they match the target repository, or replace them with project-specific context and evidence.

## Harness-only development readiness

The installed harness is sufficient to start and continue agent-guided development loops when each task has a concrete goal, relevant context, and an acceptance gate. It provides the agent contract, context directories, project agents, project skills, templates, two-stage Git hook validation, native validation adapters, and CI command patterns.

The harness alone does not define product requirements, domain rules, architecture decisions, implementation source code, runtime configuration, secrets, or generated artifacts. For underspecified repositories, the first development task MUST populate the affected product spec, design doc, architecture note, and active execution plan before implementation proceeds.

## Harness evolution

The repository harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance. Treat the current committed harness files as the active contract. When the harness changes, update this directory, `AGENTS.md`, validators, templates, and `.claude/harness/evolution-log.md` as needed.

## Validation

Run the command for the repository stack:

- Gradle harness validation: `./gradlew harnessValidate`, or `gradle harnessValidate` when this repository uses system Gradle without a wrapper
- Gradle final check: `./gradlew check`, or `gradle check` when this repository uses system Gradle without a wrapper
- Maven: `mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate`
- uv: `uv run python .claude/harness/uv/harness_validate.py`
- bun: `bun run .claude/harness/bun/harness-validate.ts`

The generated Gradle `pre-commit` hook runs `harnessValidate`; non-Gradle `pre-commit` hooks check lightweight harness-rule compliance only. The generated `pre-push` hook runs the selected final check command and should match CI when CI snippets are present.

Run validation from the repository root. The uv, bun, and Maven validators bind the current working directory as the target root. Native validators support the installed `.claude/harness/manifest.json` schema and compare the list fields written by this harness.

If the installer wired Gradle into a complex existing `settings.gradle(.kts)`, review the resulting plugin management and composite build blocks manually before relying on `check` in CI.
