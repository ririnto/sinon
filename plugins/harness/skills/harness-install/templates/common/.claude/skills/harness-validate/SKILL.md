---
name: harness-validate
description: Run the installed target repository harness validator and report contract readiness. Use this skill after changing AGENTS.md, CLAUDE.md, ARCHITECTURE.md, docs, .claude agents, .claude skills, .claude/harness assets, hooks, CI, or generated artifacts.
---

# Harness Validate

Validate this target repository's installed harness. This target skill uses the local `.claude/harness/README.md` command; plugin-level validation belongs to the plugin checkout.

## First Safe Checks

1. Read `.claude/harness/README.md` for the stack command.
2. Read `.claude/harness/manifest.json` for required files, directories, and generated-artifact policy.
3. Confirm you are running from the target repository root.
4. Confirm whether the repository is Gradle, Maven, uv, or bun based on local files.

## Commands

Run the matching command:

| Stack | Command |
| --- | --- |
| Gradle | `./gradlew harnessValidate`, or `gradle harnessValidate` when the target uses system Gradle without a wrapper |
| Maven | `mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate` |
| uv | `uv run python .claude/harness/uv/harness_validate.py` |
| bun | `bun run .claude/harness/bun/harness-validate.ts` |

## Workflow

1. Run the matching command.
2. If it fails, classify the failure as missing contract file, missing `.gitkeep`, stale placeholder, executable-bit problem, CI/hook mismatch, or stack-tool failure.
3. Fix only failures within the requested scope.
4. Re-run the same command after fixes.

## Invariants

- The installed manifest is the target-local harness contract.
- `docs/generated/` may be empty, but generated files that exist need regeneration metadata.
- Seed references may be replaced when they do not match the target stack or domain.

## Output Contract

Report:

- `command`: exact command run.
- `result`: pass or fail.
- `failures`: contract category and file path.
- `next action`: fix made, required owner action, or blocker.
